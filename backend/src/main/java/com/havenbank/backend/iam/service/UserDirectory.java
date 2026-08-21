package com.havenbank.backend.iam.service;

import com.havenbank.backend.iam.dto.UserView;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Published lookup API for other modules that need to resolve a user's stable id and roles by email
 * (the authenticated principal name). This keeps the IAM persistence internals encapsulated while
 * still letting the authorization server stamp {@code sub}/{@code roles} into issued tokens.
 */
public interface UserDirectory {

    Optional<UserView> findByEmail(String email);

    /**
     * Batch-resolve user ids to their email addresses for display (e.g. the audit trail's actor
     * column). Unknown or deleted ids are simply absent from the returned map. Kept as a single
     * batch call so a page of audit rows costs one query, not one per row.
     */
    Map<UUID, String> emailsByIds(Collection<UUID> ids);
}
