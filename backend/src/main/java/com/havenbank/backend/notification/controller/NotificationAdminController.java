package com.havenbank.backend.notification.controller;

import com.havenbank.backend.notification.domain.Status;
import com.havenbank.backend.notification.dto.NotificationView;
import com.havenbank.backend.notification.service.NotificationAdminService;
import com.havenbank.backend.shared.ratelimit.RateLimitTier;
import com.havenbank.backend.shared.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin view over the notification log, primarily to inspect the dead-letter queue and requeue
 * failed deliveries. ADMIN only.
 */
@Tag(name = "Admin: notifications", description = "Inspect the notification dead-letter queue and requeue failed sends.")
@RestController
@RequestMapping("/api/v1/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
class NotificationAdminController {

    private final NotificationAdminService notificationAdmin;

    @Operation(summary = "List notifications by status", description = "Defaults to the dead-letter queue.")
    @GetMapping
    @RateLimited(RateLimitTier.STANDARD)
    public Page<NotificationView> list(@RequestParam(defaultValue = "DEAD_LETTER") Status status,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        return notificationAdmin.list(status, PageRequest.of(page, Math.min(size, 200)));
    }

    @Operation(summary = "Requeue a notification", description = "Returns a dead-lettered notification to the retry queue.")
    @PostMapping("/{id}/retry")
    @RateLimited(RateLimitTier.STANDARD)
    public NotificationView retry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return notificationAdmin.requeue(id, UUID.fromString(jwt.getSubject()));
    }
}