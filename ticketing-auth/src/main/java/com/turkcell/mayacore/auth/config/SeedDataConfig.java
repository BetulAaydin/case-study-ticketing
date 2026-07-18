package com.turkcell.mayacore.auth.config;

import com.turkcell.mayacore.auth.domain.Role;
import com.turkcell.mayacore.auth.domain.User;
import com.turkcell.mayacore.auth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SeedDataConfig implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedDataConfig(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        createUserIfNotExists("admin@ticketing.com", "ChangeMe123!", Set.of(Role.ADMIN));
        createUserIfNotExists("organizer@ticketing.com", "ChangeMe123!", Set.of(Role.ORGANIZER));
        createUserIfNotExists("customer@ticketing.com", "ChangeMe123!", Set.of(Role.CUSTOMER));
    }

    private void createUserIfNotExists(String email, String password, Set<Role> roles) {
        if (!userRepository.existsByEmail(email)) {
            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRoles(roles);
            userRepository.save(user);
        }
    }
}
