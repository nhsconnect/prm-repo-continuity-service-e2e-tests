package uk.nhs.prm.deduction.e2e.tests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.nhs.prm.deduction.e2e.TestConfiguration;
import uk.nhs.prm.deduction.e2e.auth.AuthTokenGenerator;
import uk.nhs.prm.deduction.e2e.deadletter.NemsEventProcessorDeadLetterQueue;
import uk.nhs.prm.deduction.e2e.mesh.MeshClient;
import uk.nhs.prm.deduction.e2e.mesh.MeshMailbox;
import uk.nhs.prm.deduction.e2e.nems.MeshForwarderQueue;
import uk.nhs.prm.deduction.e2e.nems.NemsEventMessage;
import uk.nhs.prm.deduction.e2e.nems.NemsEventProcessorUnhandledQueue;
import uk.nhs.prm.deduction.e2e.pdsadaptor.PdsAdaptorClient;
import uk.nhs.prm.deduction.e2e.pdsadaptor.PdsAdaptorResponse;
import uk.nhs.prm.deduction.e2e.queue.SqsQueue;
import uk.nhs.prm.deduction.e2e.suspensions.MofUpdatedMessageQueue;
import uk.nhs.prm.deduction.e2e.suspensions.NemsEventProcessorSuspensionsMessageQueue;
import uk.nhs.prm.deduction.e2e.suspensions.SuspensionServiceNotReallySuspensionsMessageQueue;
import uk.nhs.prm.deduction.e2e.utility.Helper;

import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


@SpringBootTest(classes = {
        EndToEndTest.class,
        MeshMailbox.class,
        SqsQueue.class,
        MeshClient.class,
        TestConfiguration.class,
        AuthTokenGenerator.class,
        MeshForwarderQueue.class,
        NemsEventProcessorUnhandledQueue.class,
        NemsEventProcessorSuspensionsMessageQueue.class,
        SuspensionServiceNotReallySuspensionsMessageQueue.class,
        NemsEventProcessorDeadLetterQueue.class,
        MeshForwarderQueue.class,
        Helper.class,
        MofUpdatedMessageQueue.class,
        PdsAdaptorClient.class
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EndToEndTest {

    public static final String PATIENT_WHICH_HAS_CURRENT_GP_NHS_NUMBER = "9692294994";
    public static final String PATIENT_WHICH_HAS_NO_CURRENT_GP_NHS_NUMBER = "9693797515";
    @Autowired
    private MeshForwarderQueue meshForwarderQueue;
    @Autowired
    private NemsEventProcessorUnhandledQueue nemsEventProcessorUnhandledQueue;
    @Autowired
    private NemsEventProcessorSuspensionsMessageQueue suspensionsMessageQueue;
    @Autowired
    private SuspensionServiceNotReallySuspensionsMessageQueue notReallySuspensionsMessageQueue;
    @Autowired
    private MofUpdatedMessageQueue mofUpdatedMessageQueue;
    @Autowired
    private NemsEventProcessorDeadLetterQueue nemsEventProcessorDeadLetterQueue;
    @Autowired
    private MeshMailbox meshMailbox;
    @Autowired
    private Helper helper;

    @BeforeAll
    void init() {
        meshForwarderQueue.deleteAllMessages();
        nemsEventProcessorDeadLetterQueue.deleteAllMessages();
        suspensionsMessageQueue.deleteAllMessages();
        nemsEventProcessorUnhandledQueue.deleteAllMessages();
        notReallySuspensionsMessageQueue.deleteAllMessages();
    }

    @Test
    public void shouldMoveSuspensionMessageFromNemsToMofUpdatedQueue() throws Exception {
        String suspendedPatientNhsNumber = PATIENT_WHICH_HAS_NO_CURRENT_GP_NHS_NUMBER;

        PdsAdaptorClient pdsAdaptorClient = new PdsAdaptorClient(suspendedPatientNhsNumber);

        PdsAdaptorResponse pdsAdaptorResponse = pdsAdaptorClient.getSuspendedPatientStatus();

        pdsAdaptorClient.updateManagingOrganisation(PdsAdaptorTest.generateRandomOdsCode(), pdsAdaptorResponse.getRecordETag());

        NemsEventMessage nemsSuspension = helper.createNemsEventFromTemplate("change-of-gp-suspension.xml", suspendedPatientNhsNumber);
        meshMailbox.postMessage(nemsSuspension);
        assertThat(meshForwarderQueue.hasMessage(nemsSuspension.body()));
        assertThat(suspensionsMessageQueue.hasMessage(suspendedPatientNhsNumber));
        assertThat(mofUpdatedMessageQueue.hasMessage(suspendedPatientNhsNumber));

    }

    @Test
    public void shouldMoveSuspensionMessageWherePatientIsNoLongerSuspendedToNotSuspendedQueue() throws Exception {
        String currentlyRegisteredPatientNhsNumber = PATIENT_WHICH_HAS_CURRENT_GP_NHS_NUMBER;

        NemsEventMessage nemsSuspension = helper.createNemsEventFromTemplate("change-of-gp-suspension.xml", currentlyRegisteredPatientNhsNumber);

        meshMailbox.postMessage(nemsSuspension);

        assertThat(meshForwarderQueue.hasMessage(nemsSuspension.body()));
        assertThat(suspensionsMessageQueue.hasMessage(currentlyRegisteredPatientNhsNumber));
        assertThat(notReallySuspensionsMessageQueue.hasMessage(currentlyRegisteredPatientNhsNumber));

    }

    @Test
    public void shouldMoveNonSuspensionMessageFromNemsToUnhandledQueue() throws Exception {
        String nhsNumber = helper.randomNhsNumber();
        NemsEventMessage nemsNonSuspension = helper.createNemsEventFromTemplate("change-of-gp-non-suspension.xml", nhsNumber);
        meshMailbox.postMessage(nemsNonSuspension);

        assertThat(meshForwarderQueue.hasMessage(nemsNonSuspension.body()));
        assertThat(nemsEventProcessorUnhandledQueue.hasMessage(nemsNonSuspension.body()));

    }


    @Test
    public void shouldSendUnprocessableMessagesToDlQ() throws Exception {
        Map<String, NemsEventMessage> dlqMessages = helper.getDLQNemsEventMessages();
        log("Posting DLQ messages");
        for (Map.Entry<String, NemsEventMessage> message : dlqMessages.entrySet()) {
            meshMailbox.postMessage(message.getValue());
            log("Posted " + message.getKey() + " message");
            assertThat(nemsEventProcessorDeadLetterQueue.hasMessage(message.getValue().body()));
        }
    }

    public void log(String message) {
        System.out.println(message);
    }
}
