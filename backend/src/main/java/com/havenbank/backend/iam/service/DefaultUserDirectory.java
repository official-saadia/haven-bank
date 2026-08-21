package com.havenbank.backend.iam.service;

import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.dto.UserView;
import com.havenbank.backend.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link UserDirectory}, backed by the IAM user store.
 */
@Service
@RequiredArgsConstructor
class DefaultUserDirectory implements UserDirectory {

    private final UserRepository userRepository;
    private final com.havenbank.backend.iam.mapper.UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserView> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(userMapper::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> emailsByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getEmail));
    }
}
