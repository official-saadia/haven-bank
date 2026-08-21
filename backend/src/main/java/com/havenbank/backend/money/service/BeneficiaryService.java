package com.havenbank.backend.money.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.money.domain.Beneficiary;
import com.havenbank.backend.money.dto.BeneficiaryRequest;
import com.havenbank.backend.money.dto.BeneficiaryResponse;
import com.havenbank.backend.money.repository.BeneficiaryRepository;
import com.havenbank.backend.shared.error.BusinessException;
import com.havenbank.backend.shared.error.ErrorType;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import com.havenbank.backend.money.mapper.BeneficiaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Saved payees, scoped to their owner on every operation.
 *
 * <h2>Why adding a payee does not verify the account exists</h2>
 * The obvious behaviour — reject an account number that is not on our books — turns this endpoint
 * into an account-enumeration oracle. Anyone with a login could walk the number space and learn
 * which accounts are real, which is exactly the disclosure FR-2.3 and FR-1.7 exist to prevent.
 * So an add is treated as an address-book write: the shape is validated, the existence is not.
 * The account is resolved at transfer time, where the check is already rate limited (SENSITIVE
 * tier), already audited, and already returns a deliberately non-leaking error (FR-3.10).
 *
 * <p>The consequence is that a saved payee confers no authority whatsoever. It prefills a form.
 * Every transfer runs the same ownership, balance, limit and step-up checks it would have run
 * had the customer typed the number by hand.
 */
@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaries;
    private final AuditService auditService;
    private final BeneficiaryMapper beneficiaryMapper;

    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> listOwn(UUID userId) {
        return beneficiaries.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(beneficiaryMapper::toResponse)
                // Sorted after decryption; ciphertext has no useful collation order.
                .sorted(Comparator.comparing(b -> displayName(b).toLowerCase()))
                .toList();
    }

    @Transactional
    public BeneficiaryResponse add(UUID userId, BeneficiaryRequest request) {
        String accountNumber = request.accountNumber().trim();
        if (beneficiaries.existsByUserIdAndAccountNumber(userId, accountNumber)) {
            throw new BusinessException(ErrorType.CONFLICT, HttpStatus.CONFLICT,
                    "You've already saved a payee with that account number.", "accountNumber");
        }
        Beneficiary saved = beneficiaries.save(Beneficiary.builder()
                .userId(userId)
                .name(request.name().trim())
                .nickname(blankToNull(request.nickname()))
                .accountNumber(accountNumber)
                .build());

        // Masked target: the audit trail records which payee, never the full number (FR-5.3).
        auditService.record(AuditEvent.success(userId, AuditAction.BENEFICIARY_ADDED,
                saved.maskedAccountNumber()));
        return beneficiaryMapper.toResponse(saved);
    }

    @Transactional
    public BeneficiaryResponse update(UUID userId, UUID id, BeneficiaryRequest request) {
        Beneficiary beneficiary = owned(userId, id);
        beneficiary.rename(request.name().trim(), blankToNull(request.nickname()));
        auditService.record(AuditEvent.success(userId, AuditAction.BENEFICIARY_UPDATED,
                beneficiary.maskedAccountNumber()));
        return beneficiaryMapper.toResponse(beneficiary);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Beneficiary beneficiary = owned(userId, id);
        beneficiaries.delete(beneficiary);
        auditService.record(AuditEvent.success(userId, AuditAction.BENEFICIARY_DELETED,
                beneficiary.maskedAccountNumber()));
    }

    /**
     * IDOR-safe fetch. A row belonging to someone else and a row that does not exist produce the
     * same 404, so the endpoint cannot be used to probe for valid identifiers.
     */
    private Beneficiary owned(UUID userId, UUID id) {
        return beneficiaries.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary not found"));
    }


    private String displayName(BeneficiaryResponse b) {
        return b.nickname() != null ? b.nickname() : b.name();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}