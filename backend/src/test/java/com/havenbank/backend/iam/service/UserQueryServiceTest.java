package com.havenbank.backend.iam.service;

import com.havenbank.backend.iam.domain.User;
import com.havenbank.backend.iam.domain.UserStatus;
import com.havenbank.backend.iam.dto.UserResponse;
import com.havenbank.backend.iam.mapper.UserMapper;
import com.havenbank.backend.iam.repository.UserRepository;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserQueryService service;

    @Test
    void gettingAKnownUserReturnsTheMappedResponse() {
        UUID id = UUID.randomUUID();
        User user = org.mockito.Mockito.mock(User.class);
        UserResponse expected = new UserResponse(id, "someone@example.com", "Someone",
                UserStatus.ACTIVE, true, java.util.Set.of("CUSTOMER"));
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(expected);

        UserResponse result = service.getById(id);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void gettingAnUnknownUserIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(missing)).isInstanceOf(ResourceNotFoundException.class);
    }
}
