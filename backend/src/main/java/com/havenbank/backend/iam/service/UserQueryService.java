package com.havenbank.backend.iam.service;

import com.havenbank.backend.iam.dto.UserResponse;
import com.havenbank.backend.iam.mapper.UserMapper;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-side queries for user profiles.
 */
@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getById(UUID userId) {
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
