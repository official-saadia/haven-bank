package com.havenbank.backend.iam.mapper;

import com.havenbank.backend.iam.domain.Role;
import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.dto.AdminUserResponse;
import com.havenbank.backend.iam.dto.UserResponse;
import com.havenbank.backend.iam.dto.UserView;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps {@link User} aggregates to their read projections.
 *
 * <p>This is the project's mapping convention (see docs/README.md): one Spring-managed mapper bean
 * per aggregate, doing entity &rarr; DTO only. Any derived or context-dependent value is passed in
 * by the caller rather than computed here, so a mapper never depends on a repository or service.
 * Being a bean (not a static util) means it is injected like any other collaborator and could be
 * swapped for a generated mapper later without touching call sites.
 */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getStatus(), user.isEmailVerified(), roleNames(user));
    }

    public AdminUserResponse toAdminResponse(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getStatus(), user.isEmailVerified(), roleNames(user));
    }

    public UserView toView(User user) {
        return new UserView(user.getId(), user.getEmail(), user.getFullName(), roleNames(user));
    }

    private static Set<String> roleNames(User user) {
        return user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
    }
}
