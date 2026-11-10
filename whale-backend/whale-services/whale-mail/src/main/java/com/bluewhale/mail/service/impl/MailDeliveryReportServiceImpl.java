package com.bluewhale.mail.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.entity.MailDeliveryReport;
import com.bluewhale.mail.entity.MailFolder;
import com.bluewhale.mail.entity.MailMessage;
import com.bluewhale.mail.mapper.MailDeliveryReportMapper;
import com.bluewhale.mail.mapper.MailMessageMapper;
import com.bluewhale.mail.service.MailAccountService;
import com.bluewhale.mail.service.MailDeliveryReportService;
import com.bluewhale.mail.support.BounceRecipient;
import com.bluewhale.mail.support.BounceReport;
import com.bluewhale.mail.support.MailImapSupport;
import com.bluewhale.mail.vo.MailDeliveryReportVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 退信回执服务实现。
 *
 * <p>三个刻意的取舍：</p>
 * <ol>
 *   <li><b>只在识别出 DSN 时才回源整封邮件</b>：同步阶段已经拿到了顶层 Content-Type，
 *       非退信邮件一次多余的 IMAP 取信都不做。</li>
 *   <li><b>入库幂等靠唯一键</b>：同一封 DSN 可能因位点回退被重复同步，
 *       {@code (tenant, account, notify_message, recipient)} 冲突即视为已处理。</li>
 *   <li><b>关联不到原邮件也入库</b>：原邮件可能已经被删或不在本账号，
 *       但「发给谁、为什么失败」这个事实本身仍然有价值。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailDeliveryReportServiceImpl implements MailDeliveryReportService {

    /** 永久失败的状态码前缀（RFC 3463 的 5.x.x） */
    private static final String HARD_PREFIX = "5";

    /** 临时失败的状态码前缀（4.x.x） */
    private static final String SOFT_PREFIX = "4";

    /** 退信类型：永久 */
    private static final String TYPE_HARD = "HARD";

    /** 退信类型：临时 */
    private static final String TYPE_SOFT = "SOFT";

    /** 退信类型：无法判定 */
    private static final String TYPE_UNKNOWN = "UNKNOWN";

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final MailDeliveryReportMapper reportMapper;

    private final MailMessageMapper messageMapper;

    private final MailAccountService accountService;

    private final MailImapSupport imapSupport;

    @Override
    public int ingest(MailAccount account, MailFolder folder, MailMessage notify) {
        if (notify == null || notify.getUid() == null || notify.getUid() <= 0) {
            return 0;
        }
        Optional<BounceReport> parsed = imapSupport.fetchBounce(account, folder, notify.getUid());
        if (parsed.isEmpty()) {
            return 0;
        }
        BounceReport report = parsed.get();
        if (report.isEmpty()) {
            return 0;
        }
        Long originalId = findOriginalMessageId(account.getId(), report.originalMessageId());
        int saved = 0;
        for (BounceRecipient recipient : report.recipients()) {
            MailDeliveryReport entity = new MailDeliveryReport();
            entity.setAccountId(account.getId());
            entity.setNotifyMessageId(notify.getId());
            entity.setOriginalMessageId(originalId);
            entity.setOriginalRfcId(report.originalMessageId());
            entity.setRecipient(trimToLength(recipient.recipient(), 190));
            entity.setAction(trimToLength(recipient.action(), 16));
            entity.setStatusCode(trimToLength(recipient.statusCode(), 16));
            entity.setDiagnostic(trimToLength(recipient.diagnostic(), 1000));
            entity.setBounceType(classify(recipient));
            entity.setReportingMta(trimToLength(report.reportingMta(), 190));
            entity.setRemoteMta(trimToLength(recipient.remoteMta(), 190));
            try {
                reportMapper.insert(entity);
                saved++;
            } catch (DuplicateKeyException e) {
                // 位点回退导致同一封 DSN 被重复同步：唯一键兜住，不算失败
                log.debug("退信回执已存在: notify={}, recipient={}", notify.getId(), recipient.recipient());
            }
        }
        if (saved > 0) {
            log.info("退信回执入库: accountId={}, notify={}, 收件人={}, 新增={}",
                    account.getId(), notify.getId(), report.recipients().size(), saved);
        }
        return saved;
    }

    @Override
    public PageResult<MailDeliveryReportVO> page(Long accountId, Long userId, long current, long size) {
        Long resolved = accountId == null ? accountService.requireDefaultAccountId(userId) : accountId;
        accountService.requireOwned(resolved, userId);
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        Page<MailDeliveryReport> page = reportMapper.selectPage(new Page<>(Math.max(current, 1L), pageSize),
                new LambdaQueryWrapper<MailDeliveryReport>()
                        .eq(MailDeliveryReport::getAccountId, resolved)
                        .orderByDesc(MailDeliveryReport::getCreateTime)
                        .orderByDesc(MailDeliveryReport::getId));
        return PageResult.of(page.getRecords().stream().map(MailDeliveryReportServiceImpl::toVo).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<MailDeliveryReportVO> listByNotify(Long notifyMessageId) {
        if (notifyMessageId == null) {
            return List.of();
        }
        return reportMapper.selectList(new LambdaQueryWrapper<MailDeliveryReport>()
                        .eq(MailDeliveryReport::getNotifyMessageId, notifyMessageId)
                        .orderByAsc(MailDeliveryReport::getRecipient))
                .stream()
                .map(MailDeliveryReportServiceImpl::toVo)
                .toList();
    }

    /**
     * 按 Message-ID 反查原邮件。
     *
     * <p>两种写法都试：本库同步时存的 {@code message_id} 是 IMAP 头里的原样值（通常带尖括号），
     * 而 DSN 里给的是不带括号的形式，两边不统一会导致永远关联不上。</p>
     *
     * @param accountId 账号ID
     * @param rfcId 原邮件 Message-ID
     * @return 原邮件ID；未命中返回 {@code null}
     */
    private Long findOriginalMessageId(Long accountId, String rfcId) {
        if (rfcId == null || rfcId.isBlank()) {
            return null;
        }
        String bare = rfcId.trim();
        String bracketed = bare.startsWith("<") ? bare : "<" + bare + ">";
        List<MailMessage> found = messageMapper.selectList(new LambdaQueryWrapper<MailMessage>()
                .eq(MailMessage::getAccountId, accountId)
                .select(MailMessage::getId)
                .and(w -> w.eq(MailMessage::getMessageId, bare)
                        .or().eq(MailMessage::getMessageId, bracketed))
                .last("LIMIT 1"));
        return found.isEmpty() ? null : found.get(0).getId();
    }

    /**
     * 判定退信类型：状态码优先，其次看 DSN 的 Action。
     *
     * @param recipient 退信明细
     * @return HARD / SOFT / UNKNOWN
     */
    private static String classify(BounceRecipient recipient) {
        String status = recipient.statusCode() == null ? "" : recipient.statusCode().trim();
        if (status.startsWith(HARD_PREFIX)) {
            return TYPE_HARD;
        }
        if (status.startsWith(SOFT_PREFIX)) {
            return TYPE_SOFT;
        }
        if ("delayed".equalsIgnoreCase(recipient.action())) {
            return TYPE_SOFT;
        }
        return TYPE_UNKNOWN;
    }

    /**
     * 截断到列宽，避免远端返回的超长诊断信息导致插入失败。
     *
     * @param value 原值
     * @param max 最大长度
     * @return 截断后的值
     */
    private static String trimToLength(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /**
     * 实体转视图。
     *
     * @param entity 实体
     * @return 视图
     */
    private static MailDeliveryReportVO toVo(MailDeliveryReport entity) {
        MailDeliveryReportVO vo = new MailDeliveryReportVO();
        vo.setId(entity.getId());
        vo.setNotifyMessageId(entity.getNotifyMessageId());
        vo.setOriginalMessageId(entity.getOriginalMessageId());
        vo.setOriginalRfcId(entity.getOriginalRfcId());
        vo.setRecipient(entity.getRecipient());
        vo.setAction(entity.getAction());
        vo.setStatusCode(entity.getStatusCode());
        vo.setDiagnostic(entity.getDiagnostic());
        vo.setBounceType(entity.getBounceType());
        vo.setReportingMta(entity.getReportingMta());
        vo.setRemoteMta(entity.getRemoteMta());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
