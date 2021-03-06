package uk.nhs.prm.deduction.e2e.nems;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.nhs.prm.deduction.e2e.TestConfiguration;
import uk.nhs.prm.deduction.e2e.queue.SqsQueue;

@Component
public class MeshForwarderQueue extends NemsEventMessageQueue {

    @Autowired
    public MeshForwarderQueue(SqsQueue sqsQueue, TestConfiguration configuration) {
        super(sqsQueue, configuration.meshForwarderObservabilityQueueUri());
    }
}
