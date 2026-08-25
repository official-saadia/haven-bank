package com.havenbank.backend.money.service;

import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.money.domain.Policy;
import com.havenbank.backend.money.domain.PolicyKey;
import com.havenbank.backend.money.dto.PolicyRequest;
import com.havenbank.backend.money.dto.PolicyResponse;
import com.havenbank.backend.money.mapper.PolicyMapper;
import com.havenbank.backend.money.repository.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Same versioning shape as {@code FeeAdminServiceTest}: a new policy value closes whichever row was
 * previously open for that key+scope, so {@code PolicyService.dailyLimit()}/{@code stepUpThreshold()}
 * (which take the single effective row) never sees two simultaneously-open values.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PolicyAdminServiceTest {

    @Mock
    private PolicyRepository policies;
    @Mock
    private AuditService auditService;
    @Mock
    private PolicyMapper policyMapper;

    @InjectMocks
    private PolicyAdminService service;

    @Test
    void settingANewValueClosesThePreviouslyOpenRowForTheSameKeyAndScope() {
        Policy currentlyOpen = mock(Policy.class);
        when(policies.findByPolicyKeyAndScopeAndEffectiveToIsNull(PolicyKey.DAILY_LIMIT, "GLOBAL"))
                .thenReturn(List.of(currentlyOpen));
        when(policies.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(policyMapper.toResponse(any())).thenReturn(
                new PolicyResponse(UUID.randomUUID(), "DAILY_LIMIT", "GLOBAL", new BigDecimal("500.00"), null, null));

        service.createVersion(UUID.randomUUID(),
                new PolicyRequest("DAILY_LIMIT", "GLOBAL", new BigDecimal("500.00")));

        verify(currentlyOpen).close(any());
        verify(policies).save(any());
        verify(auditService).record(any());
    }

    @Test
    void differentScopesForTheSameKeyDoNotCloseEachOther() {
        // Only the GLOBAL-scoped row is looked up and closed; a hypothetical per-tier scope row
        // must not be touched by a GLOBAL update, and vice versa - the repository call is scoped
        // by both key AND scope together.
        when(policies.findByPolicyKeyAndScopeAndEffectiveToIsNull(PolicyKey.STEP_UP_THRESHOLD, "GLOBAL"))
                .thenReturn(List.of());
        when(policies.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(policyMapper.toResponse(any())).thenReturn(
                new PolicyResponse(UUID.randomUUID(), "STEP_UP_THRESHOLD", "GLOBAL", new BigDecimal("1000.00"), null, null));

        service.createVersion(UUID.randomUUID(),
                new PolicyRequest("STEP_UP_THRESHOLD", "GLOBAL", new BigDecimal("1000.00")));

        verify(policies).findByPolicyKeyAndScopeAndEffectiveToIsNull(PolicyKey.STEP_UP_THRESHOLD, "GLOBAL");
        verify(policies).save(any());
    }
}
