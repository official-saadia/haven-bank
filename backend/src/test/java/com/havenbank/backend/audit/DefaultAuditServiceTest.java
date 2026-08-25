package com.havenbank.backend.audit.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.domain.AuditTrail;
import com.havenbank.backend.audit.repository.AuditTrailRepository;
import com.havenbank.backend.shared.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * FR-5.2: the ambient request context (source IP, user agent, correlation id) is stamped onto every
 * audit record, sourced differently depending on which thread is calling - the request thread reads
 * live thread-locals, an {@code @Async} caller must supply what it captured earlier. Never tested at
 * any level before this.
 */
@ExtendWith(MockitoExtension.class)
class DefaultAuditServiceTest {

    @Mock
    private AuditTrailRepository repository;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void recordingWithNoBoundRequestPersistsNullAmbientContextRatherThanFailing() {
        DefaultAuditService service = new DefaultAuditService(repository);
        UUID actorId = UUID.randomUUID();
        AuditEvent event = AuditEvent.success(actorId, AuditAction.LOGIN_SUCCESS, "signed in");

        // No MDC correlation id set, and no request bound to this thread - AuditContext.current()
        // must degrade to nulls rather than throw, since a scheduled job or a test has no request.
        service.record(event);

        ArgumentCaptor<AuditTrail> captor = ArgumentCaptor.forClass(AuditTrail.class);
        verify(repository).save(captor.capture());
        AuditTrail saved = captor.getValue();
        assertThat(saved.getActorUserId()).isEqualTo(actorId);
        assertThat(saved.getAction()).isEqualTo(AuditAction.LOGIN_SUCCESS);
        assertThat(saved.getOutcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
        assertThat(saved.getDetail()).isEqualTo("signed in");
        assertThat(saved.getCorrelationId()).isNull();
        assertThat(saved.getSourceIp()).isNull();
    }

    @Test
    void recordingOnTheRequestThreadCapturesTheLiveCorrelationIdFromMdc() {
        DefaultAuditService service = new DefaultAuditService(repository);
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-abc-123");
        AuditEvent event = AuditEvent.failure(null, AuditAction.LOGIN_FAILURE, "bad password");

        service.record(event);

        ArgumentCaptor<AuditTrail> captor = ArgumentCaptor.forClass(AuditTrail.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCorrelationId()).isEqualTo("corr-abc-123");
        assertThat(captor.getValue().getOutcome()).isEqualTo(AuditEvent.Outcome.FAILURE);
        // A failed login has no actor (the credential was wrong, there is no authenticated user).
        assertThat(captor.getValue().getActorUserId()).isNull();
    }

    @Test
    void recordingWithAnExplicitlySuppliedContextUsesThatRatherThanAmbientThreadLocals() {
        DefaultAuditService service = new DefaultAuditService(repository);
        // Simulate the @Async case: MDC on this thread is empty/different from what the original
        // request thread captured before publishing the event.
        MDC.put(CorrelationIdFilter.MDC_KEY, "wrong-thread-correlation-id");
        AuditContext capturedOnRequestThread = new AuditContext("203.0.113.5", "TestAgent/1.0", "corr-original");
        AuditEvent event = AuditEvent.failure(null, AuditAction.RATE_LIMITED, "CRITICAL /login");

        service.record(event, capturedOnRequestThread);

        ArgumentCaptor<AuditTrail> captor = ArgumentCaptor.forClass(AuditTrail.class);
        verify(repository).save(captor.capture());
        AuditTrail saved = captor.getValue();
        // The explicitly-passed context wins, not whatever this (wrong) thread's MDC happens to say.
        assertThat(saved.getCorrelationId()).isEqualTo("corr-original");
        assertThat(saved.getSourceIp()).isEqualTo("203.0.113.5");
        assertThat(saved.getUserAgent()).isEqualTo("TestAgent/1.0");
    }

    @Test
    void everyRecordedEventGetsItsOwnRandomId() {
        DefaultAuditService service = new DefaultAuditService(repository);
        service.record(AuditEvent.success(UUID.randomUUID(), AuditAction.LOGIN_SUCCESS, "a"));
        service.record(AuditEvent.success(UUID.randomUUID(), AuditAction.LOGIN_SUCCESS, "b"));

        ArgumentCaptor<AuditTrail> captor = ArgumentCaptor.forClass(AuditTrail.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getId()).isNotEqualTo(captor.getAllValues().get(1).getId());
    }
}
