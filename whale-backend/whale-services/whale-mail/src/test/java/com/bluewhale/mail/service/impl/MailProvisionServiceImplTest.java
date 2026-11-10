package com.bluewhale.mail.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mail.client.MailcowClient;
import com.bluewhale.mail.config.MailProperties;
import com.bluewhale.mail.dto.MailProvisionRequest;
import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.mapper.MailAccountMapper;
import com.bluewhale.mail.service.MailFolderService;
import com.bluewhale.mail.support.MailSecretCipher;
import com.bluewhale.mail.vo.MailProvisionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台代开邮箱状态机测试。
 *
 * <p>覆盖 5 个分支：未启用、已存在、正常开通、地址冲突、落库失败回滚。
 * 这 5 条覆盖了「重复建用户」与「半成品邮箱」两类线上最容易出的事故。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MailProvisionServiceImplTest {

    /** 测试域名 */
    private static final String DOMAIN = "example.com";

    @Mock
    private MailAccountMapper accountMapper;

    @Mock
    private MailFolderService folderService;

    @Mock
    private MailcowClient mailcowClient;

    @Mock
    private MailSecretCipher secretCipher;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private MailProperties properties;

    private MailProvisionServiceImpl service;

    /**
     * 构造被测服务：用真实 TransactionTemplate 配 mock 事务管理器，
     * 让回调体真的被执行，才能验证「漏建文件夹」这类问题
     */
    @BeforeEach
    void setUp() {
        properties = new MailProperties();
        properties.setDefaultDomain(DOMAIN);
        properties.setDefaultImapHost("mail.example.com");
        properties.setDefaultSmtpHost("mail.example.com");
        properties.getMailcow().setEnabled(true);
        properties.getMailcow().setQuotaMb(2048);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(mailcowClient.isEnabled()).thenReturn(true);
        service = new MailProvisionServiceImpl(accountMapper, folderService, properties,
                mailcowClient, secretCipher, transactionManager);
    }

    @Test
    @DisplayName("代开关闭时返回 DISABLED，不触碰任何数据")
    void shouldReturnDisabledWhenSwitchOff() {
        properties.getMailcow().setEnabled(false);
        MailProvisionVO vo = service.provision(request());
        assertEquals(MailProvisionVO.STATE_DISABLED, vo.getState());
        verify(accountMapper, never()).insert(any(MailAccount.class));
    }

    @Test
    @DisplayName("同一用户重复调用返回 EXISTS，不重复建邮箱")
    void shouldReturnExistsWhenUserAlreadyHasMailbox() {
        MailAccount existing = new MailAccount();
        existing.setEmail("zhangsan@example.com");
        when(accountMapper.selectList(any())).thenReturn(List.of(existing));
        MailProvisionVO vo = service.provision(request());
        assertEquals(MailProvisionVO.STATE_EXISTS, vo.getState());
        assertEquals("zhangsan@example.com", vo.getEmail());
        verify(mailcowClient, never()).createMailbox(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("正常开通：建 Mailcow 邮箱、落账号、初始化系统文件夹")
    void shouldCreateMailboxAndAccount() {
        when(accountMapper.selectList(any())).thenReturn(List.of());
        when(accountMapper.selectCount(any())).thenReturn(0L);
        when(mailcowClient.exists(anyString())).thenReturn(false);
        when(secretCipher.encrypt(anyString())).thenReturn("ENC(cipher)");
        MailProvisionVO vo = service.provision(request());
        assertEquals(MailProvisionVO.STATE_CREATED, vo.getState());
        assertEquals("zhangsan@example.com", vo.getEmail());
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailcowClient).createMailbox(eq("zhangsan"), eq(DOMAIN), eq("张三"), passwordCaptor.capture());
        assertTrue(passwordCaptor.getValue().length() >= 20, "初始口令长度不足");
        ArgumentCaptor<MailAccount> accountCaptor = ArgumentCaptor.forClass(MailAccount.class);
        verify(accountMapper).insert(accountCaptor.capture());
        MailAccount saved = accountCaptor.getValue();
        assertEquals("ENC(cipher)", saved.getSecretCipher());
        assertEquals("AUTO", saved.getProvisionSource());
        assertEquals(2048L * 1024 * 1024, saved.getQuotaBytes());
        verify(folderService).ensureSystemFolders(saved);
    }

    @Test
    @DisplayName("Mailcow 里已有同地址但平台无记录：返回 CONFLICT，绝不改它的口令")
    void shouldReturnConflictWhenMailboxExistsInMailcowOnly() {
        when(accountMapper.selectList(any())).thenReturn(List.of());
        when(accountMapper.selectCount(any())).thenReturn(0L);
        when(mailcowClient.exists(anyString())).thenReturn(true);
        MailProvisionVO vo = service.provision(request());
        assertEquals(MailProvisionVO.STATE_CONFLICT, vo.getState());
        verify(mailcowClient, never()).createMailbox(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("落库失败时回删 Mailcow 邮箱，避免留下无人认领的地址")
    void shouldRollbackMailboxWhenPersistFails() {
        when(accountMapper.selectList(any())).thenReturn(List.of());
        when(accountMapper.selectCount(any())).thenReturn(0L);
        when(mailcowClient.exists(anyString())).thenReturn(false);
        when(secretCipher.encrypt(anyString())).thenReturn("ENC(cipher)");
        when(accountMapper.insert(any(MailAccount.class))).thenThrow(new IllegalStateException("唯一键冲突"));
        assertThrows(BizException.class, () -> service.provision(request()));
        verify(mailcowClient).deleteMailboxQuietly("zhangsan@example.com");
    }

    @Test
    @DisplayName("用户名不可用时回退为 u{userId}，保证每个用户都有地址")
    void shouldFallbackToUserIdWhenUsernameNotUsable() {
        when(accountMapper.selectList(any())).thenReturn(List.of());
        when(accountMapper.selectCount(any())).thenReturn(0L);
        when(mailcowClient.exists(anyString())).thenReturn(false);
        when(secretCipher.encrypt(anyString())).thenReturn("ENC(cipher)");
        MailProvisionRequest request = request();
        request.setUsername("张三");
        MailProvisionVO vo = service.provision(request);
        assertEquals(MailProvisionVO.STATE_CREATED, vo.getState());
        verify(mailcowClient).createMailbox(eq("u1024"), eq(DOMAIN), anyString(), anyString());
    }

    /**
     * 构造默认请求
     *
     * @return 开通请求
     */
    private MailProvisionRequest request() {
        MailProvisionRequest request = new MailProvisionRequest();
        request.setTenantId(1L);
        request.setUserId(1024L);
        request.setUsername("zhangsan");
        request.setDisplayName("张三");
        return request;
    }
}
