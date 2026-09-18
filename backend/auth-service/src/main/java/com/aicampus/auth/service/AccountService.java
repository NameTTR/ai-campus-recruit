package com.aicampus.auth.service;

import com.aicampus.common.dto.AccountCreateRequest;
import com.aicampus.common.dto.AccountStatusUpdateRequest;
import com.aicampus.common.dto.AccountSummary;
import com.aicampus.common.dto.RegisterRequest;
import com.aicampus.common.demo.DemoDataFactory;
import com.aicampus.common.enums.AccountStatus;
import com.aicampus.common.enums.Role;
import com.aicampus.common.security.RolePermissionPolicy;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
public class AccountService {
    private final PasswordHasher passwordHasher;
    private final AccountPersistence persistence;
    private final Map<String, AccountRecord> accountsByUserId = new ConcurrentHashMap<>();
    private final Map<String, String> userIdByUsername = new ConcurrentHashMap<>();

    public AccountService(PasswordHasher passwordHasher, AccountPersistence persistence,
            @Value("${demo.seed.enabled:${DEMO_SEED_ENABLED:false}}") boolean seedEnabled,
            @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String bootstrapPassword) {
        this.passwordHasher = passwordHasher;
        this.persistence = persistence;
        persistence.load().forEach(record -> {
            accountsByUserId.put(record.userId(), record);
            userIdByUsername.put(record.username(), record.userId());
        });
        if (seedEnabled) {
            seed("S001", "student", "Student Demo", Role.STUDENT);
            seed("C001", "company", "Company HR", Role.COMPANY);
            seed("A001", "admin", "Admin Demo", Role.ADMIN);
            seedDemoAccounts();
        } else if (bootstrapPassword != null && !bootstrapPassword.isBlank()
                && accountsByUserId.values().stream().noneMatch(account -> account.role() == Role.ADMIN)) {
            create(new AccountCreateRequest("admin", bootstrapPassword, "管理员", Role.ADMIN, AccountStatus.ACTIVE));
        }
    }

    public AccountRecord authenticate(String username, String password) {
        AccountRecord account = findByUsername(username);
        if (!passwordHasher.matches(password, account.passwordHash())) {
            throw new AuthAuthenticationException("invalid username or password");
        }
        ensureActive(account.userId());
        return account;
    }

    public AccountRecord register(RegisterRequest request) {
        Role role = request.role() == null ? Role.STUDENT : request.role();
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("admin account must be created by an administrator");
        }
        return create(new AccountCreateRequest(
                request.username(),
                request.password(),
                request.displayName(),
                role,
                AccountStatus.ACTIVE));
    }

    public synchronized AccountRecord create(AccountCreateRequest request) {
        Role role = request.role() == null ? Role.STUDENT : request.role();
        AccountStatus status = request.status() == null ? AccountStatus.ACTIVE : request.status();
        String username = normalizeUsername(request.username());
        validatePassword(request.password());
        String userId = nextUserId(role);
        String displayName = defaultDisplayName(request.displayName(), role, userId);
        AccountRecord record = new AccountRecord(
                userId,
                username,
                displayName,
                role,
                status,
                passwordHasher.hash(request.password()),
                Instant.now(),
                Instant.now());
        if (userIdByUsername.containsKey(username)) {
            throw new IllegalArgumentException("username already exists");
        }
        persistence.insert(record);
        userIdByUsername.put(username, userId);
        accountsByUserId.put(userId, record);
        return record;
    }

    public List<AccountSummary> listAccounts() {
        return accountsByUserId.values().stream()
                .sorted(Comparator.comparing(AccountRecord::createdAt))
                .map(AccountRecord::toSummary)
                .toList();
    }

    public synchronized AccountSummary updateStatus(String userId, AccountStatusUpdateRequest request) {
        AccountStatus status = request == null || request.status() == null ? AccountStatus.ACTIVE : request.status();
        AccountRecord account = copyOf(findByUserId(userId));
        account.setStatus(status);
        persistUpdate(account);
        return account.toSummary();
    }

    public synchronized void changePassword(String userId, String currentPassword, String newPassword) {
        AccountRecord account = copyOf(findByUserId(userId));
        ensureActive(account.userId());
        if (!passwordHasher.matches(currentPassword, account.passwordHash())) {
            throw new AuthAuthenticationException("invalid current password");
        }
        validatePassword(newPassword);
        account.setPasswordHash(passwordHasher.hash(newPassword));
        persistUpdate(account);
    }

    public synchronized void resetPassword(String userId, String newPassword) {
        AccountRecord account = copyOf(findByUserId(userId));
        validatePassword(newPassword);
        account.setPasswordHash(passwordHasher.hash(newPassword));
        persistUpdate(account);
    }

    public AccountRecord ensureActive(String userId) {
        AccountRecord account = findByUserId(userId);
        if (account.status() != AccountStatus.ACTIVE) {
            throw new AuthAccessDeniedException("account is not active");
        }
        return account;
    }

    public AccountRecord findByUserId(String userId) {
        AccountRecord account = accountsByUserId.get(valueOr(userId).trim());
        if (account == null) {
            throw new AuthAuthenticationException("account not found");
        }
        return account;
    }

    private AccountRecord findByUsername(String username) {
        String userId = userIdByUsername.get(normalizeUsername(username));
        if (userId == null) {
            throw new AuthAuthenticationException("invalid username or password");
        }
        return findByUserId(userId);
    }

    private void seed(String userId, String username, String displayName, Role role) {
        if (accountsByUserId.containsKey(userId) || userIdByUsername.containsKey(username)) return;
        AccountRecord record = new AccountRecord(
                userId,
                username,
                displayName,
                role,
                AccountStatus.ACTIVE,
                passwordHasher.hash("123456"),
                Instant.now(),
                Instant.now());
        persistence.insert(record);
        accountsByUserId.put(userId, record);
        userIdByUsername.put(username, userId);
    }

    private void seedDemoAccounts() {
        DemoDataFactory.studentProfiles().forEach(profile ->
                seed(profile.userId(), "demo_student_" + profile.userId().substring(1).toLowerCase(Locale.ROOT),
                        profile.displayName(), Role.STUDENT));
        for (int i = 1; i <= 24; i++) {
            String userId = DemoDataFactory.companyId(i);
            seed(userId, "demo_company_" + "%03d".formatted(i), "Campus HR " + userId, Role.COMPANY);
        }
        for (int i = 2; i <= 12; i++) {
            String userId = "A" + "%03d".formatted(i);
            seed(userId, "demo_admin_" + "%03d".formatted(i), "Operations Admin " + userId, Role.ADMIN);
        }
    }

    private String nextUserId(Role role) {
        return role.name().substring(0, 1) + UUID.randomUUID().toString().replace("-", "");
    }

    private AccountRecord copyOf(AccountRecord record) {
        return new AccountRecord(record.userId(), record.username(), record.displayName(), record.role(),
                record.status(), record.passwordHash(), record.createdAt(), record.updatedAt());
    }

    private void persistUpdate(AccountRecord record) {
        persistence.update(record);
        accountsByUserId.put(record.userId(), record);
    }

    private String normalizeUsername(String username) {
        String normalized = valueOr(username).trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 3 || normalized.length() > 32) {
            throw new IllegalArgumentException("username length must be 3 to 32");
        }
        if (!normalized.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("username contains unsupported characters");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6 || password.length() > 72) {
            throw new IllegalArgumentException("password length must be 6 to 72");
        }
    }

    private String defaultDisplayName(String displayName, Role role, String userId) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        return switch (role) {
            case COMPANY -> "Company " + userId;
            case ADMIN -> "Admin " + userId;
            case STUDENT -> "Student " + userId;
        };
    }

    private String valueOr(String value) {
        return value == null ? "" : value;
    }

    public static final class AccountRecord {
        private final String userId;
        private final String username;
        private final String displayName;
        private final Role role;
        private final Instant createdAt;
        private volatile AccountStatus status;
        private volatile String passwordHash;
        private volatile Instant updatedAt;

        AccountRecord(
                String userId,
                String username,
                String displayName,
                Role role,
                AccountStatus status,
                String passwordHash,
                Instant createdAt,
                Instant updatedAt) {
            this.userId = userId;
            this.username = username;
            this.displayName = displayName;
            this.role = role;
            this.status = status;
            this.passwordHash = passwordHash;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public AccountSummary toSummary() {
            return new AccountSummary(
                    userId,
                    username,
                    displayName,
                    role,
                    status,
                    RolePermissionPolicy.permissionNames(role),
                    createdAt,
                    updatedAt);
        }

        public String userId() {
            return userId;
        }

        public String username() {
            return username;
        }

        public String displayName() {
            return displayName;
        }

        public Role role() {
            return role;
        }

        public AccountStatus status() {
            return status;
        }

        public String passwordHash() {
            return passwordHash;
        }

        public Instant createdAt() {
            return createdAt;
        }

        public Instant updatedAt() {
            return updatedAt;
        }

        private void setStatus(AccountStatus status) {
            this.status = status;
            this.updatedAt = Instant.now();
        }

        private void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            this.updatedAt = Instant.now();
        }
    }
}
