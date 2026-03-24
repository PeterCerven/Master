package sk.master.backend.service.auth;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.master.backend.exception.UserAlreadyExistsException;
import sk.master.backend.persistence.dto.auth.*;
import sk.master.backend.persistence.entity.RefreshTokenEntity;
import sk.master.backend.persistence.entity.UserEntity;
import sk.master.backend.persistence.repository.GraphRepository;
import sk.master.backend.persistence.repository.PipelineConfigRepository;
import sk.master.backend.persistence.repository.RefreshTokenRepository;
import sk.master.backend.persistence.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final static Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokenRepository;
    private final GraphRepository graphRepository;
    private final PipelineConfigRepository pipelineConfigRepository;

    @Override
    public TokenResponseDto login(LoginRequestDto request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        UserEntity userEntity = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + request.getEmail()));
        String accessToken = jwtService.generateAccessToken(userEntity);
        return TokenResponseDto.of(accessToken);
    }

    @Override
    @Transactional
    public String createRefreshTokenForLogin(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        return refreshTokenService.createRefreshToken(user);
    }

    @Override
    @Transactional
    public TokenResponseDto refreshAccessToken(String refreshToken) {
        RefreshTokenEntity token = refreshTokenService.validateRefreshToken(refreshToken);
        String accessToken = jwtService.generateAccessToken(token.getUser());
        return TokenResponseDto.of(accessToken);
    }

    @Override
    @Transactional
    public UserResponseDto createUser(CreateUserRequestDto request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("User with email: " + request.getEmail() + " already exists");
        }
        log.info("Creating new user: email={}, role={}", request.getEmail(), request.getRole());

        UserEntity user = new UserEntity();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setEnabled(true);
        userRepository.save(user);

        return UserResponseDto.fromEntity(user);
    }

    @Override
    public UserResponseDto getCurrentUser(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        return UserResponseDto.fromEntity(user);
    }

    @Override
    @Transactional
    public void logout(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        refreshTokenService.revokeAllUserTokens(user.getId());
    }

    @Override
    public List<UserResponseDto> listUsers() {
        return userRepository.findAll().stream()
                .map(UserResponseDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public void setUserEnabled(Long targetUserId, Long requestingUserId) {
        if (targetUserId.equals(requestingUserId)) {
            throw new IllegalArgumentException("Cannot modify your own account");
        }
        UserEntity user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + targetUserId));
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        if (!user.isEnabled()) {
            refreshTokenRepository.revokeAllByUserId(targetUserId);
        }
    }

    @Override
    @Transactional
    public void deleteUser(Long targetUserId, Long requestingUserId) {
        if (targetUserId.equals(requestingUserId)) {
            throw new IllegalArgumentException("Cannot delete your own account");
        }
        refreshTokenRepository.deleteAllByUserId(targetUserId);
        graphRepository.deleteAllByUserId(targetUserId);
        pipelineConfigRepository.deleteByUserId(targetUserId);
        userRepository.deleteById(targetUserId);
    }
}
