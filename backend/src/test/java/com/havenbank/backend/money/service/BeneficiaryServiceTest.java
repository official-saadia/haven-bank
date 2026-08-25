package com.havenbank.backend.money.service;

import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.money.domain.Beneficiary;
import com.havenbank.backend.money.dto.BeneficiaryRequest;
import com.havenbank.backend.money.dto.BeneficiaryResponse;
import com.havenbank.backend.money.mapper.BeneficiaryMapper;
import com.havenbank.backend.money.repository.BeneficiaryRepository;
import com.havenbank.backend.shared.error.BusinessException;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FR-2.3/FR-1.7 applied to the beneficiary module: every operation is scoped to its owner, and
 * {@link BeneficiaryService#add} deliberately never checks the account number exists (see the
 * class's own javadoc on avoiding an enumeration oracle) - this asserts that intent directly,
 * rather than only trusting the comment.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BeneficiaryServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private BeneficiaryRepository beneficiaries;
    @Mock
    private AuditService auditService;
    @Mock
    private BeneficiaryMapper beneficiaryMapper;

    @InjectMocks
    private BeneficiaryService service;

    @Test
    void addingAPayeeNeverChecksWhetherTheAccountNumberExists() {
        when(beneficiaries.existsByUserIdAndAccountNumber(eq(USER_ID), anyString())).thenReturn(false);
        when(beneficiaries.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(beneficiaryMapper.toResponse(any())).thenAnswer(inv -> {
            Beneficiary b = inv.getArgument(0);
            return new BeneficiaryResponse(b.getId(), b.getName(), b.getNickname(), b.getAccountNumber(), null);
        });

        BeneficiaryRequest request = new BeneficiaryRequest("Nonexistent Payee", null, "ZZ00NOSUCHACCT1234");

        // The point of this test: no repository other than `beneficiaries` itself is even injected
        // into BeneficiaryService (no AccountRepository dependency exists to check against) - a
        // clean save is the only possible outcome regardless of whether the number is real.
        BeneficiaryResponse response = service.add(USER_ID, request);

        assertThat(response.accountNumber()).isEqualTo("ZZ00NOSUCHACCT1234");
        verify(beneficiaries).save(any());
    }

    @Test
    void addingADuplicateAccountNumberForTheSameUserIsRejected() {
        when(beneficiaries.existsByUserIdAndAccountNumber(USER_ID, "GB29NWBK60161331926819")).thenReturn(true);

        BeneficiaryRequest request = new BeneficiaryRequest("Existing Payee", null, "GB29NWBK60161331926819");

        assertThatThrownBy(() -> service.add(USER_ID, request))
                .isInstanceOf(BusinessException.class);
        verify(beneficiaries, never()).save(any());
    }

    @Test
    void theAuditRecordCarriesOnlyTheMaskedAccountNumberNeverTheFullOne() {
        when(beneficiaries.existsByUserIdAndAccountNumber(any(), any())).thenReturn(false);
        when(beneficiaries.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(beneficiaryMapper.toResponse(any())).thenReturn(
                new BeneficiaryResponse(UUID.randomUUID(), "Someone", null, "GB29NWBK60161331926819", null));

        service.add(USER_ID, new BeneficiaryRequest("Someone", null, "GB29NWBK60161331926819"));

        verify(auditService).record(argThat(event ->
                event.detail().equals("••••6819") && !event.detail().contains("GB29NWBK")));
    }

    @Test
    void updatingAnotherCustomersBeneficiaryIsIndistinguishableFromItNotExisting() {
        UUID someoneElsesBeneficiary = UUID.randomUUID();
        when(beneficiaries.findByIdAndUserId(someoneElsesBeneficiary, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER_ID, someoneElsesBeneficiary,
                new BeneficiaryRequest("New Name", null, "GB00000000000000001")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletingAnotherCustomersBeneficiaryIsIndistinguishableFromItNotExisting() {
        UUID someoneElsesBeneficiary = UUID.randomUUID();
        when(beneficiaries.findByIdAndUserId(someoneElsesBeneficiary, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, someoneElsesBeneficiary))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(beneficiaries, never()).delete(any());
    }

    @Test
    void updatingAnOwnedBeneficiaryRenamesItAndAudits() {
        Beneficiary owned = Beneficiary.builder().userId(USER_ID).name("Old Name")
                .accountNumber("GB29NWBK60161331926819").build();
        when(beneficiaries.findByIdAndUserId(owned.getId(), USER_ID)).thenReturn(Optional.of(owned));
        when(beneficiaryMapper.toResponse(owned)).thenReturn(
                new BeneficiaryResponse(owned.getId(), "New Name", "Nick", owned.getAccountNumber(), null));

        BeneficiaryResponse response = service.update(USER_ID, owned.getId(),
                new BeneficiaryRequest("New Name", "Nick", "GB29NWBK60161331926819"));

        assertThat(owned.getName()).isEqualTo("New Name");
        assertThat(owned.getNickname()).isEqualTo("Nick");
        assertThat(response.name()).isEqualTo("New Name");
        verify(auditService).record(any());
    }

    @Test
    void deletingAnOwnedBeneficiaryRemovesItAndAudits() {
        Beneficiary owned = Beneficiary.builder().userId(USER_ID).name("To Delete")
                .accountNumber("GB29NWBK60161331926819").build();
        when(beneficiaries.findByIdAndUserId(owned.getId(), USER_ID)).thenReturn(Optional.of(owned));

        service.delete(USER_ID, owned.getId());

        verify(beneficiaries).delete(owned);
        verify(auditService).record(any());
    }

    @Test
    void listOwnSortsByDisplayNamePreferringNicknameOverName() {
        when(beneficiaries.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(
                mock(Beneficiary.class), mock(Beneficiary.class)));
        when(beneficiaryMapper.toResponse(any()))
                .thenReturn(new BeneficiaryResponse(UUID.randomUUID(), "Zach Formal Name", "Ant", null, null))
                .thenReturn(new BeneficiaryResponse(UUID.randomUUID(), "Amy Formal Name", null, null, null));

        List<BeneficiaryResponse> result = service.listOwn(USER_ID);

        // Sorted by display name ("Ant" nickname vs "Amy Formal Name" name) - "Amy..." < "Ant"
        // alphabetically, so it must come first even though "Zach..." was returned first from the
        // repository.
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Amy Formal Name");
    }
}
