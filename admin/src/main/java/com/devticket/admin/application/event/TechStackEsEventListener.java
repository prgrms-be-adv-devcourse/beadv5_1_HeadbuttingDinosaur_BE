package com.devticket.admin.application.event;

import com.devticket.admin.infrastructure.persistence.repository.TechStackEsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TechStackEsEventListener {

    private final TechStackEsRepository techStackEsRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handCreated(TechStackCreatedEvent event) {
        techStackEsRepository.save(event.id(), event.name(), event.embedding());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUpdated(TechStackUpdatedEvent event) {
        techStackEsRepository.update(event.id(), event.name(), event.embedding());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDeleted(TechStackDeletedEvent event) {
        techStackEsRepository.delete(event.id());
    }

}
