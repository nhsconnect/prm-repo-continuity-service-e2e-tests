package uk.nhs.prm.deduction.e2e.live_technical_test.inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.EnableScheduling;
import uk.nhs.prm.deduction.e2e.TestConfiguration;
import uk.nhs.prm.deduction.e2e.mesh.MeshMailbox;
import uk.nhs.prm.deduction.e2e.performance.awsauth.AssumeRoleCredentialsProviderFactory;
import uk.nhs.prm.deduction.e2e.performance.awsauth.AutoRefreshingRoleAssumingSqsClient;
import uk.nhs.prm.deduction.e2e.queue.SqsQueue;
import uk.nhs.prm.deduction.e2e.suspensions.SuspensionMessageObservabilityQueue;

import static java.time.ZoneOffset.ofHours;
import static java.time.ZonedDateTime.now;
import static uk.nhs.prm.deduction.e2e.live_technical_test.TestParameters.outputTestParameter;
import static uk.nhs.prm.deduction.e2e.nhs.NhsIdentityGenerator.generateRandomOdsCode;
import static uk.nhs.prm.deduction.e2e.nhs.NhsIdentityGenerator.randomNemsMessageId;
import static uk.nhs.prm.deduction.e2e.utility.NemsEventFactory.createNemsEventFromTemplate;

@SpringBootTest(classes = {
        MeshMailbox.class,
        TestConfiguration.class,
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableScheduling
public class InjectChangeOfGPMessageTest {

    @Autowired
    private MeshMailbox meshMailbox;

    @Autowired
    private TestConfiguration config;

    private SuspensionMessageObservabilityQueue suspensionMessageObservabilityQueue;

    @BeforeEach
    public void setUp() {
        var sqsClient = new AutoRefreshingRoleAssumingSqsClient(new AssumeRoleCredentialsProviderFactory());
        suspensionMessageObservabilityQueue = new SuspensionMessageObservabilityQueue(new SqsQueue(sqsClient), new TestConfiguration());
    }


    @Test
    public void shouldInjectTestMessageOnlyIntendedToRunInNonProdEnvironment() {
        String nemsMessageId = randomNemsMessageId();
        String nhsNumber = config.getNhsNumberForSyntheticPatientInPreProd();
        String previousGP = generateRandomOdsCode();

        suspensionMessageObservabilityQueue.deleteAllMessages();

        var nemsSuspension = createNemsEventFromTemplate(
                "change-of-gp-suspension.xml",
                nhsNumber,
                nemsMessageId,
                previousGP,
                now(ofHours(0)).toString());

        meshMailbox.postMessage(nemsSuspension);

        // MAYBE add some extra synthetic patient change of gps to simulate UK-wide synthetic activity??

        System.out.println("injected nemsMessageId: " + nemsMessageId + " of course this will not be known in prod, so should be picked up when change of gp received");
        outputTestParameter("live_technical_test_nhs_number", nhsNumber);
        outputTestParameter("live_technical_test_previous_gp", previousGP);
    }
}
