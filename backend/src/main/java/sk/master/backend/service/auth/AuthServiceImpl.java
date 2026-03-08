package sk.master.backend.service.auth;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
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
public class AuthServiceImpl implements AuthService, UserDetailsService {
    private final static Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokenRepository;
    private final GraphRepository graphRepository;
    private final PipelineConfigRepository pipelineConfigRepository;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            @Lazy AuthenticationManager authenticationManager,
            RefreshTokenRepository refreshTokenRepository,
            GraphRepository graphRepository,
            PipelineConfigRepository pipelineConfigRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.authenticationManager = authenticationManager;
        this.refreshTokenRepository = refreshTokenRepository;
        this.graphRepository = graphRepository;
        this.pipelineConfigRepository = pipelineConfigRepository;
    }

    @Override
    public @NonNull UserDetails loadUserByUsername(@NonNull String email) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return new User(
                user.getEmail(),
                user.getPassword(),
                user.isEnabled(),
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }

    @Override
    public TokenResponseDto login(LoginRequestDto request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        UserDetails userDetails = loadUserByUsername(request.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails);
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
        UserDetails userDetails = loadUserByUsername(token.getUser().getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails);
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
