package com.veggofresh.auth.service.impl;

import com.veggofresh.auth.entity.User;
import com.veggofresh.auth.repository.UserRepository;
import com.veggofresh.auth.service.AccountAccessService;
import com.veggofresh.platform.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountAccessServiceImpl implements AccountAccessService {

    private static final String CONTACT_ADMIN = "Please contact admin through the complaint section.";

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public void requireNotBlocked(UUID userId, String action) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isBlocked()) {
            return;
        }
        throw new BusinessException("ACCOUNT_BLOCKED",
                "Your account is blocked, so you can't " + action + "." + reasonSentence(user) + " " + CONTACT_ADMIN,
                HttpStatus.FORBIDDEN);
    }

    @Override
    @Transactional(readOnly = true)
    public void requirePayoutsNotOnHold(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isBlocked() || !user.isPayoutsOnHold()) {
            return;
        }
        throw new BusinessException("PAYOUTS_ON_HOLD",
                "Payouts are on hold for your account." + reasonSentence(user) + " " + CONTACT_ADMIN,
                HttpStatus.FORBIDDEN);
    }

    private static String reasonSentence(User user) {
        String reason = user.getBlockedReason();
        return (reason == null || reason.isBlank()) ? "" : " Reason: " + reason.trim();
    }
}
