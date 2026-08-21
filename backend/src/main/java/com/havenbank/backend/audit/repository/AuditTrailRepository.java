package com.havenbank.backend.audit.repository;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditTrail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditTrailRepository extends JpaRepository<AuditTrail, UUID> {

    Page<AuditTrail> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditTrail> findByActionOrderByCreatedAtDesc(AuditAction action, Pageable pageable);
}
