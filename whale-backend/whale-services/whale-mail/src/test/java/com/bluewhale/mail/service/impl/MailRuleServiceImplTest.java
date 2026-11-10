package com.bluewhale.mail.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.mail.config.MailProperties;
import com.bluewhale.mail.dto.MailRuleSaveRequest;
import com.bluewhale.mail.entity.MailFolder;
import com.bluewhale.mail.entity.MailMessage;
import com.bluewhale.mail.entity.MailRule;
import com.bluewhale.mail.mapper.MailFolderMapper;
import com.bluewhale.mail.mapper.MailMessageMapper;
import com.bluewhale.mail.mapper.MailRuleMapper;
import com.bluewhale.mail.service.MailAccountService;
import com.bluewhale.mail.service.MailFolderService;
import com.bluewhale.mail.service.MailSearchService;
import com.bluewhale.mail.vo.MailRuleApplyVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收信规则测试。
 *
 * <p>覆盖四类容易出错的地方：多值条件是「或」而条件之间是「且」、命中即停、
 * 归档只改展示位置不改回源位置、以及会把邮件静默丢进回收站的配置必须被拒绝。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MailRuleServiceImplTest {

    /** 测试账号ID */
    private static final long ACCOUNT_ID = 7L;

    /** 用户ID */
    private static final long USER_ID = 9L;

    /** 收件箱ID */
    private static final long INBOX_ID = 100L;

    /** 归档文件夹ID */
    private static final long ARCHIVE_ID = 200L;

    @Mock
    private MailRuleMapper ruleMapper;

    @Mock
    private MailMessageMapper messageMapper;

    @Mock
    private MailFolderMapper folderMapper;

    @Mock
    private MailAccountService accountService;

    @Mock
    private MailFolderService folderService;

    @Mock
    private MailSearchService searchService;

    private MailRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MailRule.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MailFolder.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MailMessage.class);
        service = new MailRuleServiceImpl(ruleMapper, messageMapper, folderMapper,
                accountService, folderService, searchService, new MailProperties());
    }

    @Test
    @DisplayName("多值条件是或：任一值包含即命中")
    void shouldMatchAnyValueInOneCondition() {
        MailRule rule = rule("finance@corp.com, noreply@invoice.com", "发票", null);
        rule.setActionFolderId(ARCHIVE_ID);
        MailMessage message = message("noreply@invoice.com", "本月发票", "me@corp.com");

        MailRule hit = service.match(List.of(rule), message);

        assertNotNull(hit);
        assertEquals(ARCHIVE_ID, message.getFolderId());
    }

    @Test
    @DisplayName("条件之间是且：发件人不符则不命中")
    void shouldRequireAllConditions() {
        MailRule rule = rule("finance@corp.com", "发票", null);
        MailMessage message = message("someone@corp.com", "本月发票", "me@corp.com");

        assertNull(service.match(List.of(rule), message));
        assertEquals(INBOX_ID, message.getFolderId());
    }

    @Test
    @DisplayName("匹配不区分大小写")
    void shouldIgnoreCase() {
        MailRule rule = rule("Finance@Corp.COM", null, null);
        MailMessage message = message("finance@corp.com", "对账单", "me@corp.com");

        assertNotNull(service.match(List.of(rule), message));
    }

    @Test
    @DisplayName("收件人条件同时看抄送")
    void shouldMatchCcAsRecipient() {
        MailRule rule = rule(null, null, "team@corp.com");
        MailMessage message = message("boss@corp.com", "周报", "me@corp.com");
        message.setCcAddrs("team@corp.com");

        assertNotNull(service.match(List.of(rule), message));
    }

    @Test
    @DisplayName("仅带附件：无附件不命中")
    void shouldSkipWhenAttachmentRequired() {
        MailRule rule = rule(null, "invoice", null);
        rule.setOnlyWithAttachment(1);
        MailMessage message = message("a@b.com", "invoice attached", "me@corp.com");
        message.setHasAttachment(0);

        assertNull(service.match(List.of(rule), message));

        message.setHasAttachment(1);
        assertNotNull(service.match(List.of(rule), message));
    }

    @Test
    @DisplayName("命中即停：优先级靠前的规则先改写")
    void shouldStopAtFirstMatch() {
        MailRule first = rule("boss@corp.com", null, null);
        first.setId(1L);
        first.setSortNo(10);
        first.setActionFolderId(ARCHIVE_ID);
        first.setActionFlagged(1);
        MailRule second = rule("boss@corp.com", null, null);
        second.setId(2L);
        second.setSortNo(20);
        second.setActionMarkSeen(1);
        MailMessage message = message("boss@corp.com", "通知", "me@corp.com");

        MailRule hit = service.match(List.of(first, second), message);

        assertNotNull(hit);
        assertEquals(1L, hit.getId());
        assertEquals(ARCHIVE_ID, message.getFolderId());
        assertEquals(1, message.getFlagged());
        // 第二条没有参与，已读不变
        assertEquals(0, message.getSeen());
    }

    @Test
    @DisplayName("归档只改展示位置，不动回源位置")
    void shouldKeepImapFolderWhenFiling() {
        MailRule rule = rule(null, "发票", null);
        rule.setActionFolderId(ARCHIVE_ID);
        MailMessage message = message("a@b.com", "发票", "me@corp.com");

        service.match(List.of(rule), message);

        assertEquals(ARCHIVE_ID, message.getFolderId());
        assertEquals(INBOX_ID, message.getImapFolderId());
        assertEquals(15L, message.getUid());
    }

    @Test
    @DisplayName("无条件规则被拒绝：会命中所有邮件")
    void shouldRejectRuleWithoutCondition() {
        MailRuleSaveRequest request = request("空规则");
        request.setActionMarkSeen(true);
        when(accountService.requireDefaultAccountId(USER_ID)).thenReturn(ACCOUNT_ID);

        BizException error = assertThrows(BizException.class, () -> service.save(request, USER_ID));

        assertEquals("至少需要一个匹配条件，否则会命中所有邮件", error.getMessage());
    }

    @Test
    @DisplayName("没有动作的规则被拒绝")
    void shouldRejectRuleWithoutAction() {
        MailRuleSaveRequest request = request("只看不动");
        request.setMatchSubject("发票");
        when(accountService.requireDefaultAccountId(USER_ID)).thenReturn(ACCOUNT_ID);

        BizException error = assertThrows(BizException.class, () -> service.save(request, USER_ID));

        assertEquals("至少需要一个动作，否则规则不产生任何效果", error.getMessage());
    }

    @Test
    @DisplayName("目标不能是回收站")
    void shouldRejectTrashAsTarget() {
        MailRuleSaveRequest request = request("丢回收站");
        request.setMatchSubject("广告");
        request.setActionFolderId(300L);
        when(accountService.requireDefaultAccountId(USER_ID)).thenReturn(ACCOUNT_ID);
        when(folderMapper.selectById(300L)).thenReturn(folder(300L, "TRASH"));

        BizException error = assertThrows(BizException.class, () -> service.save(request, USER_ID));

        assertEquals("不能把规则目标设为回收站，删除需手动操作", error.getMessage());
    }

    @Test
    @DisplayName("回填只处理收件箱，命中后落库并重建索引")
    void shouldApplyBackfillOnInboxOnly() {
        MailRule rule = rule("noreply@invoice.com", null, null);
        rule.setId(5L);
        rule.setActionFolderId(ARCHIVE_ID);
        when(accountService.requireDefaultAccountId(USER_ID)).thenReturn(ACCOUNT_ID);
        when(folderService.findByCode(ACCOUNT_ID, "INBOX")).thenReturn(folder(INBOX_ID, "INBOX"));
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        MailMessage hit = message("noreply@invoice.com", "发票", "me@corp.com");
        MailMessage miss = message("friend@corp.com", "午饭", "me@corp.com");
        when(messageMapper.selectList(any())).thenReturn(List.of(hit, miss));

        MailRuleApplyVO result = service.applyToInbox(ACCOUNT_ID, USER_ID);

        assertEquals(2, result.getScanned());
        assertEquals(1, result.getChanged());
        assertEquals(false, result.getTruncated());
        verify(messageMapper).updateById(hit);
        verify(messageMapper, never()).updateById(miss);
        verify(searchService).index(hit);
        verify(folderService).refreshCounters(ARCHIVE_ID);
    }

    @Test
    @DisplayName("没有启用规则时回填直接返回，不扫表")
    void shouldSkipBackfillWhenNoRule() {
        when(accountService.requireDefaultAccountId(USER_ID)).thenReturn(ACCOUNT_ID);
        when(ruleMapper.selectList(any())).thenReturn(List.of());

        MailRuleApplyVO result = service.applyToInbox(ACCOUNT_ID, USER_ID);

        assertEquals(0, result.getScanned());
        assertEquals(0, result.getChanged());
        verify(messageMapper, never()).selectList(any());
        verify(folderService, never()).refreshCounters(anyLong());
    }

    /**
     * 构造规则
     *
     * @param from 发件人条件
     * @param subject 主题条件
     * @param to 收件人条件
     * @return 规则
     */
    private MailRule rule(String from, String subject, String to) {
        MailRule rule = new MailRule();
        rule.setId(1L);
        rule.setAccountId(ACCOUNT_ID);
        rule.setName("测试规则");
        rule.setSortNo(100);
        rule.setEnabled(1);
        rule.setMatchSubject(subject);
        rule.setMatchFrom(from);
        rule.setMatchTo(to);
        rule.setOnlyWithAttachment(0);
        rule.setActionMarkSeen(0);
        rule.setActionFlagged(0);
        rule.setHitCount(0L);
        return rule;
    }

    /**
     * 构造收件箱内的邮件
     *
     * @param from 发件人
     * @param subject 主题
     * @param to 收件人
     * @return 邮件
     */
    private MailMessage message(String from, String subject, String to) {
        MailMessage message = new MailMessage();
        message.setId(1000L);
        message.setAccountId(ACCOUNT_ID);
        message.setFolderId(INBOX_ID);
        message.setImapFolderId(INBOX_ID);
        message.setUid(15L);
        message.setFromAddr(from);
        message.setSubject(subject);
        message.setToAddrs(to);
        message.setSeen(0);
        message.setFlagged(0);
        message.setHasAttachment(0);
        return message;
    }

    /**
     * 构造文件夹
     *
     * @param id 文件夹ID
     * @param code 编码
     * @return 文件夹
     */
    private MailFolder folder(long id, String code) {
        MailFolder folder = new MailFolder();
        folder.setId(id);
        folder.setAccountId(ACCOUNT_ID);
        folder.setFolderCode(code);
        folder.setName(code);
        return folder;
    }

    /**
     * 构造保存请求
     *
     * @param name 规则名
     * @return 请求
     */
    private MailRuleSaveRequest request(String name) {
        MailRuleSaveRequest request = new MailRuleSaveRequest();
        request.setName(name);
        request.setAccountId(ACCOUNT_ID);
        return request;
    }
}
