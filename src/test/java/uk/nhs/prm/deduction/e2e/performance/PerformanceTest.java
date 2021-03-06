package uk.nhs.prm.deduction.e2e.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import uk.nhs.prm.deduction.e2e.TestConfiguration;
import uk.nhs.prm.deduction.e2e.mesh.MeshMailbox;
import uk.nhs.prm.deduction.e2e.pdsadaptor.PdsAdaptorClient;
import uk.nhs.prm.deduction.e2e.performance.awsauth.AssumeRoleCredentialsProviderFactory;
import uk.nhs.prm.deduction.e2e.performance.awsauth.AutoRefreshingRoleAssumingSqsClient;
import uk.nhs.prm.deduction.e2e.performance.load.*;
import uk.nhs.prm.deduction.e2e.queue.BasicSqsClient;
import uk.nhs.prm.deduction.e2e.queue.SqsMessage;
import uk.nhs.prm.deduction.e2e.queue.SqsQueue;
import uk.nhs.prm.deduction.e2e.suspensions.MofUpdatedMessageQueue;
import uk.nhs.prm.deduction.e2e.utility.QueueHelper;

import java.time.LocalDateTime;
import java.util.List;

import static java.lang.System.out;
import static java.time.LocalDateTime.now;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static uk.nhs.prm.deduction.e2e.nhs.NhsIdentityGenerator.randomNemsMessageId;
import static uk.nhs.prm.deduction.e2e.nhs.NhsIdentityGenerator.randomNhsNumber;
import static uk.nhs.prm.deduction.e2e.performance.NemsTestEvent.nonSuspensionEvent;
import static uk.nhs.prm.deduction.e2e.performance.load.LoadPhase.atFlatRate;
import static uk.nhs.prm.deduction.e2e.performance.reporting.PerformanceChartGenerator.generateProcessingDurationScatterPlot;
import static uk.nhs.prm.deduction.e2e.performance.reporting.PerformanceChartGenerator.generateThroughputPlot;

@SpringBootTest(classes = {
        PerformanceTest.class,
        MeshMailbox.class,
        SqsQueue.class,
        TestConfiguration.class,
        QueueHelper.class,
        MofUpdatedMessageQueue.class,
        AssumeRoleCredentialsProviderFactory.class,
        BasicSqsClient.class,
        AutoRefreshingRoleAssumingSqsClient.class
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableScheduling
public class PerformanceTest {

    public static final int TOTAL_MESSAGES_PER_DAY = 17000;
    public static final int SUSPENSION_MESSAGES_PER_DAY = 4600;
    public static final int NON_SUSPENSION_MESSAGES_PER_DAY = TOTAL_MESSAGES_PER_DAY - SUSPENSION_MESSAGES_PER_DAY;
    public static final int THROUGHPUT_BUCKET_SECONDS = 60;

    @Autowired
    private MeshMailbox meshMailbox;
    @Autowired
    private TestConfiguration config;
    @Autowired
    ApplicationContext context;

    private MofUpdatedMessageQueue mofUpdatedMessageQueue;

    @BeforeEach
    public void setUp() {
        mofUpdatedMessageQueue = new MofUpdatedMessageQueue(new SqsQueue(appropriateAuthenticationSqsClient()), config);
    }

    @Disabled("only used for perf test development not wanted on actual runs")
    @Test
    public void shouldMoveSingleSuspensionMessageFromNemsToMofUpdatedQueue() {
        var nhsNumberPool = new RoundRobinPool<>(config.suspendedNhsNumbers());
        var suspensions = new SuspensionCreatorPool(nhsNumberPool);

        var nemsEvent = injectSingleNemsSuspension(new DoNothingTestEventListener(), suspensions.next());

        out.println("looking for message containing: " + nemsEvent.nemsMessageId());

        var successMessage = mofUpdatedMessageQueue.getMessageContaining(nemsEvent.nemsMessageId());

        assertThat(successMessage).isNotNull();

        nemsEvent.finished(successMessage);
    }

    @Test
    public void testAllSuspensionMessagesAreProcessedWhenLoadedWithProfileOfRatesAndInjectedMessageCounts() {
        final int overallTimeout = config.performanceTestTimeout();
        final var recorder = new PerformanceTestRecorder();

        var eventSource = createMixedSuspensionsAndNonSuspensionsTestEventSource(SUSPENSION_MESSAGES_PER_DAY, NON_SUSPENSION_MESSAGES_PER_DAY);
        var loadSource = new LoadRegulatingPool<>(eventSource, config.performanceTestLoadPhases(List.<LoadPhase>of(
                atFlatRate(10, "1"),
                atFlatRate(10, "2"))));

        var suspensionsOnlyRecorder = new SuspensionsOnlyEventListener(recorder);
        while (loadSource.unfinished()) {
            injectSingleNemsSuspension(suspensionsOnlyRecorder, loadSource.next());
        }

        loadSource.summariseTo(out);

        out.println("Checking mof updated message queue...");

        try {
            final var timeout = now().plusSeconds(overallTimeout);
            while (before(timeout) && recorder.hasUnfinishedEvents()) {
                for (SqsMessage nextMessage : mofUpdatedMessageQueue.getNextMessages(timeout)) {
                    recorder.finishMatchingMessage(nextMessage);
                }
            }
        }
        finally {
            recorder.summariseTo(out);

            generateProcessingDurationScatterPlot(recorder, "Suspension event processing durations vs start time (non-suspensions not shown)");
            generateThroughputPlot(recorder, THROUGHPUT_BUCKET_SECONDS, "Suspension event mean throughput per second in " + THROUGHPUT_BUCKET_SECONDS + " second buckets");
        }

        assertThat(recorder.hasUnfinishedEvents()).isFalse();
    }

    private NemsTestEvent injectSingleNemsSuspension(NemsTestEventListener listener, NemsTestEvent testEvent) {
        var nemsSuspension = testEvent.createMessage();

        listener.onStartingTestItem(testEvent);

        String meshMessageId = meshMailbox.postMessage(nemsSuspension, false);

        testEvent.started(meshMessageId);

        listener.onStartedTestItem(testEvent);

        return testEvent;
    }

    private MixerPool<NemsTestEvent> createMixedSuspensionsAndNonSuspensionsTestEventSource(int suspensionMessagesPerDay, int nonSuspensionMessagesPerDay) {
        var suspensionsSource = new SuspensionCreatorPool(suspendedNhsNumbers());
        var nonSuspensionsSource = new BoringNemsTestEventPool(nonSuspensionEvent(randomNhsNumber(), randomNemsMessageId()));
        return new MixerPool<>(
                suspensionMessagesPerDay, suspensionsSource,
                nonSuspensionMessagesPerDay, nonSuspensionsSource);
    }

    private RoundRobinPool<String> suspendedNhsNumbers() {
        List<String> suspendedNhsNumbers = config.suspendedNhsNumbers();
        checkSuspended(suspendedNhsNumbers);
        return new RoundRobinPool(suspendedNhsNumbers);
    }

    private void checkSuspended(List<String> suspendedNhsNumbers) {
        if (!config.getEnvironmentName().equals("perf")) {
            PdsAdaptorClient pds = new PdsAdaptorClient("performance-test", config.getPdsAdaptorPerformanceApiKey(), config.getPdsAdaptorUrl());
            for (String nhsNumber: suspendedNhsNumbers) {
                var patientStatus = pds.getSuspendedPatientStatus(nhsNumber);
                out.println(nhsNumber + ": " + patientStatus);
                assertThat(patientStatus.getIsSuspended()).isTrue();
            }
        }
    }

    private boolean before(LocalDateTime timeout) {
        return now().isBefore(timeout);
    }

    private AutoRefreshingRoleAssumingSqsClient appropriateAuthenticationSqsClient() {
        if (config.performanceTestTimeout() > TestConfiguration.SECONDS_IN_AN_HOUR * 0.9) {
            var authStrategyWarning = "Performance test timeout is approaching an hour, getting where this will not work if " +
                    "using temporary credentials (such as obtained by user using MFA) if it exceeds the expiration time. " +
                    "Longer runs will need to be done in pipeline where refresh can be made from the AWS instance's " +
                    "metadata credentials lookup.";
            System.err.println(authStrategyWarning);
        }
        out.println("AUTH STRATEGY: using auto-refresh, role-assuming sqs client");
        return context.getBean(AutoRefreshingRoleAssumingSqsClient.class);
    }
}
