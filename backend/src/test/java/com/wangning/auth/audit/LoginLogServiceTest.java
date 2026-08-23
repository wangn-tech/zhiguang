package com.wangning.auth.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginLogServiceTest {

    @Mock
    private LoginLogMapper loginLogMapper;

    private LoginLogService loginLogService;

    @BeforeEach
    void setUp() {
        loginLogService = new LoginLogService(loginLogMapper);
    }

    @Test
    void shouldNormalizeAndPersistLoginAudit() {
        when(loginLogMapper.insert(any(LoginLog.class))).thenReturn(1);

        loginLogService.record(
                7L,
                " user@example.com ",
                LoginChannel.CODE,
                " 127.0.0.1 ",
                "A".repeat(600),
                LoginStatus.SUCCESS
        );

        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogMapper).insert(captor.capture());
        LoginLog loginLog = captor.getValue();
        assertThat(loginLog.getUserId()).isEqualTo(7L);
        assertThat(loginLog.getIdentifier()).isEqualTo("user@example.com");
        assertThat(loginLog.getChannel()).isEqualTo("CODE");
        assertThat(loginLog.getIp()).isEqualTo("127.0.0.1");
        assertThat(loginLog.getUserAgent()).hasSize(512);
        assertThat(loginLog.getStatus()).isEqualTo("SUCCESS");
        assertThat(loginLog.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldAllowMissingUserIdForFailedUnknownAccountLogin() {
        when(loginLogMapper.insert(any(LoginLog.class))).thenReturn(1);

        loginLogService.record(
                null,
                "missing@example.com",
                LoginChannel.PASSWORD,
                null,
                null,
                LoginStatus.FAILED
        );

        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
    }

    @Test
    void shouldNotPropagateAuditPersistenceFailure() {
        when(loginLogMapper.insert(any(LoginLog.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(() -> loginLogService.record(
                7L,
                "user@example.com",
                LoginChannel.REGISTER,
                "127.0.0.1",
                "JUnit",
                LoginStatus.SUCCESS
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldSkipInvalidAuditEvent() {
        loginLogService.record(
                null,
                " ",
                null,
                null,
                null,
                null
        );

        verifyNoInteractions(loginLogMapper);
    }
}
