package sk.master.backend.config;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import sk.master.backend.persistence.entity.UserEntity;
import sk.master.backend.persistence.model.Role;
import sk.master.backend.persistence.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.name}")
    private String adminName;

    @Value("${admin.password}")
    private String adminPassword;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (!userRepository.existsByEmail(adminEmail)) {
            log.info("Creating admin account");
            UserEntity admin = new UserEntity();
            admin.setName(adminName);
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole(Role.ADMIN);
            admin.setEnabled(true);
            userRepository.save(admin);
        } else {
            log.debug("Admin account already exists, skipping seed.");
        }
    }
}
