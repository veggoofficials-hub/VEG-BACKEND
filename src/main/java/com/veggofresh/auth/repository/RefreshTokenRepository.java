package com.veggofresh.auth.repository;

import com.veggofresh.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    /** Every refresh token issued to this user -- account deletion deletes them all (logs out every device). */
    List<RefreshToken> findByUserId(UUID userId);
}
