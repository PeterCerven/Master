package sk.master.backend.service.auth;

import sk.master.backend.persistence.dto.auth.*;

import java.util.List;

public interface AuthService {

    TokenResponseDto login(LoginRequestDto request);

    String createRefreshTokenForLogin(String email);

    TokenResponseDto refreshAccessToken(String refreshToken);

    UserResponseDto createUser(CreateUserRequestDto request);

    UserResponseDto getCurrentUser(String email);

    void logout(String email);

    List<UserResponseDto> listUsers();

    void setUserEnabled(Long targetUserId, Long requestingUserId);

    void deleteUser(Long targetUserId, Long requestingUserId);
}
