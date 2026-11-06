package com.bluewhale.mail.service.impl;

import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.entity.MailDeliveryReport;
import com.bluewhale.mail.entity.MailFolder;
import com.bluewhale.mail.entity.MailMessage;
import com.bluewhale.mail.mapper.MailDeliveryReportMapper;
import com.bluewhale.mail.mapper.MailMessageMapper;
import com.bluewhale.mail.service.MailAccountService;
import com.bluewhale.mail.support.BounceRecipient;
import com.bluewhale.mail.support.BounceReport;
import com.bluewhale.mail.support.MailImapSupport;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退信回执服务单测。
 *
 * <p>覆盖三个判断点：状态码决定硬退还是软退、原邮件能按 Message-ID 关联上、
 * 重复同步同一封 DSN 时被唯一键挡住而不算失败。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MailDeliveryReportServiceImplTest {

    /** 账号ID */
    private static final long ACCOUNT_ID = 9L;

    /** 退信通知邮件ID */
    private static final long NOTIFY_ID = 100L;

    /** 原邮件ID */
    private static final long ORIGINAL_ID = 77L;

    @Mock
    private MailDeliveryReportMapper reportMapper;

    @Mock
    private MailMessageMapper messageMapper;

    @Mock
    private MailAccountService accountService;

    @Mock
    private MailImapSupport imapSupport;

    private MailDeliveryReportServiceImpl service;

    private MailAccount account;

    private MailFolder folder;

    private MailMessage notify;

    /**
     * 初始化被测对象与固定入参
     */
    @BeforeEach
    void setUp() {
        // LambdaQueryWrapper 依赖实体的 Lambda 缓存：纯单测没有 MyBatis 启动流程，需手工初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                MailMessage.class);
        service = new MailDeliveryReportServiceImpl(reportMapper, messageMapper, accountService, imapSupport);
        account = new MailAccount();
        account.setId(ACCOUNT_ID);
        folder = new MailFolder();
        folder.setId(3L);
        folder.setImapName("INBOX");
        notify = new MailMessage();
        notify.setId(NOTIFY_ID);
        notify.setUid(5L);
    }

    /**
     * 硬退按 5.x.x 判定、软退按 4.x.x 或 delayed 判定，并关联到原邮件
     */
    @Test
    @DisplayName("按状态码分类并关联原邮件")
    void shouldClassifyAndLinkOriginal() {
        MailMessage original = new MailMessage();
        original.setId(ORIGINAL_ID);
        when(messageMapper.selectList(any())).thenReturn(List.of(original));
        when(imapSupport.fetchBounce(account, folder, 5L)).thenReturn(Optional.of(new BounceReport(
                "original-id@corp", "dns; mx.bluewhale.local",
                List.of(new BounceRecipient("bad@corp.com", "failed", "5.1.1",
                                "smtp; 550 5.1.1 user unknown", "mx.corp.com"),
                        new BounceRecipient("slow@corp.com", "delayed", "4.2.2",
                                "smtp; 452 mailbox full", null)))));
        when(reportMapper.insert(any(MailDeliveryReport.class))).thenReturn(1);

        int saved = service.ingest(account, folder, notify);

        assertEquals(2, saved);
        ArgumentCaptor<MailDeliveryReport> captor = ArgumentCaptor.forClass(MailDeliveryReport.class);
        verify(reportMapper, times(2)).insert(captor.capture());
        List<MailDeliveryReport> rows = captor.getAllValues();
        assertEquals("HARD", rows.get(0).getBounceType());
        assertEquals("SOFT", rows.get(1).getBounceType());
        assertEquals(ORIGINAL_ID, rows.get(0).getOriginalMessageId());
        assertEquals("mx.corp.com", rows.get(0).getRemoteMta());
        assertEquals("original-id@corp", rows.get(0).getOriginalRfcId());
        assertEquals(NOTIFY_ID, rows.get(0).getNotifyMessageId());
    }

    /**
     * 状态码缺失但 Action 是 delayed 时按软退处理（很多 MTA 不给 4.x.x）
     */
    @Test
    @DisplayName("无状态码时按 Action 判定")
    void shouldClassifyByActionWhenStatusMissing() {
        when(imapSupport.fetchBounce(account, folder, 5L)).thenReturn(Optional.of(new BounceReport(
                null, null, List.of(new BounceRecipient("later@corp.com", "delayed", null, null, null)))));
        when(reportMapper.insert(any(MailDeliveryReport.class))).thenReturn(1);

        service.ingest(account, folder, notify);

        ArgumentCaptor<MailDeliveryReport> captor = ArgumentCaptor.forClass(MailDeliveryReport.class);
        verify(reportMapper).insert(captor.capture());
        assertEquals("SOFT", captor.getValue().getBounceType());
        assertNull(captor.getValue().getOriginalMessageId());
    }

    /**
     * 重复同步同一封 DSN：唯一键冲突不应中断，也不该计入新增
     */
    @Test
    @DisplayName("唯一键冲突时跳过")
    void shouldSkipDuplicate() {
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(imapSupport.fetchBounce(account, folder, 5L)).thenReturn(Optional.of(new BounceReport(
                "x@corp", null, List.of(new BounceRecipient("bad@corp.com", "failed", "5.0.0", "550", null)))));
        when(reportMapper.insert(any(MailDeliveryReport.class))).thenThrow(new DuplicateKeyException("uk_tenant_notify_rcpt"));

        assertEquals(0, service.ingest(account, folder, notify));
    }

    /**
     * 非退信邮件不回源解析，也不写库
     */
    @Test
    @DisplayName("非退信直接跳过")
    void shouldIgnoreNonBounce() {
        when(imapSupport.fetchBounce(account, folder, 5L)).thenReturn(Optional.empty());

        assertEquals(0, service.ingest(account, folder, notify));
        verify(reportMapper, never()).insert(any(MailDeliveryReport.class));
    }

    /**
     * 解析不出收件人时视为非退信，避免写入没有收件人的空记录
     */
    @Test
    @DisplayName("无收件人明细不入库")
    void shouldIgnoreEmptyRecipients() {
        when(imapSupport.fetchBounce(account, folder, 5L))
                .thenReturn(Optional.of(new BounceReport(null, null, List.of())));

        assertEquals(0, service.ingest(account, folder, notify));
        verify(reportMapper, never()).insert(any(MailDeliveryReport.class));
    }
}
