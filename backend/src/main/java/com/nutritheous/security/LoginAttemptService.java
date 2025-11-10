package com.nutritheous.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service to track failed login attempts and implement account lockout.
 * Prevents brute force attacks by temporarily locking accounts after excessive failed attempts.
 */
@Service
@Slf4j
public class LoginAttemptService {

    // Maximum failed attempts before lockout
    private static final int MAX_ATTEMPTS = 5;

    // Lockout duration in minutes
    private static final int LOCKOUT_DURATION_MINUTES = 15;

    // Track failed attempts: email -> attempt count
    private final ConcurrentMap<String, Integer> attemptsCache = new ConcurrentHashMap<>();

    // Track lockout time: email -> lockout timestamp
    private final ConcurrentMap<String, LocalDateTime> lockoutCache = new ConcurrentHashMap<>();

    /**
     * Records a successful login, clearing any failed attempt history.
     *
     * @param email User's email
     */
    public void loginSucceeded(String email) {
        attemptsCache.remove(email);
        lockoutCache.remove(email);
        log.debug("Login succeeded for: {}, cleared attempt history", email);
    }

    /**
     * Records a failed login attempt.
     * If max attempts exceeded, locks the account.
     *
     * @param email User's email
     */
    public void loginFailed(String email) {
        int attempts = attemptsCache.getOrDefault(email, 0) + 1;
        attemptsCache.put(email, attempts);

        log.warn("Login failed for: {}. Attempt {}/{}", email, attempts, MAX_ATTEMPTS);

        if (attempts >= MAX_ATTEMPTS) {
            LocalDateTime lockoutUntil = LocalDateTime.now().plusMinutes(LOCKOUT_DURATION_MINUTES);
            lockoutCache.put(email, lockoutUntil);

            log.warn("🔒 Account locked for: {}. Locked until: {} ({} minutes)",
                    email, lockoutUntil, LOCKOUT_DURATION_MINUTES);
        }
    }

    /**
     * Checks if an account is currently locked due to failed login attempts.
     *
     * @param email User's email
     * @return true if account is locked
     */
    public boolean isLocked(String email) {
        LocalDateTime lockoutUntil = lockoutCache.get(email);

        if (lockoutUntil == null) {
            return false;
        }

        // Check if lockout period has expired
        if (LocalDateTime.now().isAfter(lockoutUntil)) {
            // Lockout expired, clear the cache
            lockoutCache.remove(email);
            attemptsCache.remove(email);
            log.info("🔓 Lockout expired for: {}", email);
            return false;
        }

        log.warn("🔒 Account still locked for: {}. Locked until: {}", email, lockoutUntil);
        return true;
    }

    /**
     * Gets the remaining lockout time in minutes for a locked account.
     *
     * @param email User's email
     * @return minutes remaining, or 0 if not locked
     */
    public long getRemainingLockoutMinutes(String email) {
        LocalDateTime lockoutUntil = lockoutCache.get(email);

        if (lockoutUntil == null || LocalDateTime.now().isAfter(lockoutUntil)) {
            return 0;
        }

        return java.time.Duration.between(LocalDateTime.now(), lockoutUntil).toMinutes();
    }

    /**
     * Gets the number of failed attempts for an email.
     *
     * @param email User's email
     * @return number of failed attempts
     */
    public int getFailedAttempts(String email) {
        return attemptsCache.getOrDefault(email, 0);
    }

    /**
     * Manually unlocks an account (for admin use).
     *
     * @param email User's email
     */
    public void unlockAccount(String email) {
        attemptsCache.remove(email);
        lockoutCache.remove(email);
        log.info("🔓 Account manually unlocked for: {}", email);
    }
}
