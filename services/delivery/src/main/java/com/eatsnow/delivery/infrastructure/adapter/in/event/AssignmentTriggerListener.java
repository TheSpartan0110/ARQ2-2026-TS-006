package com.eatsnow.delivery.infrastructure.adapter.in.event;

import com.eatsnow.delivery.domain.event.AssignmentTrigger;
import com.eatsnow.delivery.domain.port.in.AssignDeliveriesUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts an assignment pass right after the transaction that made it worthwhile has committed: a delivery was
 * registered, a driver went on duty or a driver completed a delivery (Spec 3.6). Running after the commit means the
 * new row or the freed driver is visible to the assignment transactions.
 */
@Component
public class AssignmentTriggerListener {

    private final AssignDeliveriesUseCase assignment;

    public AssignmentTriggerListener(AssignDeliveriesUseCase assignment) {
        this.assignment = assignment;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTrigger(AssignmentTrigger trigger) {
        assignment.assignPending();
    }
}
