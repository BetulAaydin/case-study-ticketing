package com.turkcell.mayacore.auth.repository;

import com.turkcell.mayacore.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findAllByUserId(Long userId);

    void deleteByUserId(Long userId);

    void deleteByExpiresAtBefore(LocalDateTime now);
}
