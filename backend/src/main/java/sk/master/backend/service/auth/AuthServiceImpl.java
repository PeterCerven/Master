package sk.master.backend.service.auth;

import org.springframework.stereotype.Service;
import sk.master.backend.persistence.repository.UserRepository;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;

    public AuthServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}
