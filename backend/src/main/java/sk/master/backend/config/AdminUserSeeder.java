package sk.master.backend.config;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import sk.master.backend.persistence.entity.UserEntity;
import sk.master.backend.persistence.model.Role;
import sk.master.backend.persistence.repository.UserRepository;

@Component
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (!userRepository.existsByEmail("admin@master.sk")) {
            log.info("Creating admin account");
            UserEntity admin = new UserEntity();
            admin.setName("Admin");
            admin.setEmail("admin@master.sk");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole(Role.ADMIN);
            admin.setEnabled(true);
            userRepository.save(admin);
        } else {
            log.debug("Admin account already exists, skipping seed.");
        }
    }
}
