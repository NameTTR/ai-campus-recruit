package com.aicampus.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aicampus.common.dto.AccountCreateRequest;
import com.aicampus.common.dto.AccountStatusUpdateRequest;
import com.aicampus.common.enums.AccountStatus;
import com.aicampus.common.enums.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountPersistenceTest {
    @Test
    void accountsPasswordAndStatusSurviveServiceRecreation() {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        var repository = new AccountPersistence(true, url, "sa", "");
        var first = new AccountService(new PasswordHasher(), repository, false, "");
        var created = first.create(new AccountCreateRequest("student_one", "password1", "同学", Role.STUDENT, AccountStatus.ACTIVE));
        first.changePassword(created.userId(), "password1", "password2");
        var restarted = new AccountService(new PasswordHasher(), new AccountPersistence(true, url, "sa", ""), false, "");
        assertThat(restarted.authenticate("student_one", "password2").userId()).isEqualTo(created.userId());
        assertThatThrownBy(() -> restarted.authenticate("student_one", "password1")).isInstanceOf(AuthAuthenticationException.class);
        restarted.updateStatus(created.userId(), new AccountStatusUpdateRequest(AccountStatus.DISABLED));
        var again = new AccountService(new PasswordHasher(), new AccountPersistence(true, url, "sa", ""), false, "");
        assertThatThrownBy(() -> again.authenticate("student_one", "password2")).isInstanceOf(AuthAccessDeniedException.class);
        assertThat(again.listAccounts()).hasSize(1);
    }

    @Test
    void failedWriteDoesNotChangeAnActiveAccountInMemory() {
        var persistence = mock(AccountPersistence.class);
        var service = new AccountService(new PasswordHasher(), persistence, false, "");
        var created = service.create(new AccountCreateRequest("student_two", "password1", "同学", Role.STUDENT, AccountStatus.ACTIVE));
        doThrow(new IllegalStateException("offline")).when(persistence).update(any());
        assertThatThrownBy(() -> service.resetPassword(created.userId(), "password2")).isInstanceOf(IllegalStateException.class);
        assertThat(service.authenticate("student_two", "password1").userId()).isEqualTo(created.userId());
    }
}
