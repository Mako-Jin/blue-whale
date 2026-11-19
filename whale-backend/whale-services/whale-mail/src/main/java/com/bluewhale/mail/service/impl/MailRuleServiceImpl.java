package com.bluewhale.mail.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
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
import com.bluewhale.mail.service.MailRuleService;
import com.bluewhale.mail.service.MailSearchService;
import com.bluewhale.mail.vo.MailRuleApplyVO;
import com.bluewhale.mail.vo.MailRuleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 收信规则服务实现。
 *
 * <p>匹配语义刻意做得简单：条件之间是「与」，同一条件内逗号分隔的多个值是「或」（包含匹配），
 * 按优先级升序取<b>第一个命中</b>的规则，不叠加。原因是一旦允许叠加，用户就无法回答
 * 「这封邮件为什么在这个文件夹里」——排在前面的规则改了什么、后面的又改了什么，
 * 只能靠逐步排查。命中即停，规则列表读起来就是执行顺序。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailRuleServiceImpl implements MailRuleService {

    /** 启用状态 */
    private static final int ENABLED_ON = 1;

    /** 关闭状态 */
    private static final int FLAG_OFF = 0;

    /** 打开状态 */
    private static final int FLAG_ON = 1;

    /** 回收站编码（规则不得把邮件直接丢进回收站） */
    private static final String FOLDER_TRASH = "TRASH";

    /** 收件箱编码（规则只对收件箱生效） */
    private static final String FOLDER_INBOX = "INBOX";

    /** 默认优先级 */
    private static final int DEFAULT_SORT_NO = 100;

    /** 多值条件分隔符 */
    private static final String VALUE_SEPARATOR = ",";

    private static final int MAX_NAME_LENGTH = 64;

    private final MailRuleMapper ruleMapper;

    private final MailMessageMapper messageMapper;

    private final MailFolderMapper folderMapper;

    private final MailAccountService accountService;

    private final MailFolderService folderService;

    private final MailSearchService searchService;

    private final MailProperties properties;

    @Override
    public List<MailRuleVO> list(Long accountId, Long userId) {
        Long resolved = accountId == null ? accountService.requireDefaultAccountId(userId) : accountId;
        accountService.requireOwned(resolved, userId);
        List<MailRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<MailRule>()
                .eq(MailRule::getAccountId, resolved)
                .orderByAsc(MailRule::getSortNo)
                .orderByAsc(MailRule::getId));
        Map<Long, String> folderNames = folderNames(resolved);
        return rules.stream().map(rule -> toVo(rule, folderNames)).toList();
    }

    @Override
    public MailRuleVO save(MailRuleSaveRequest request, Long userId) {
        Long resolved = request.getAccountId() == null
                ? accountService.requireDefaultAccountId(userId) : request.getAccountId();
        accountService.requireOwned(resolved, userId);
        MailRule rule = request.getId() == null ? new MailRule() : requireOwned(request.getId(), userId);
        if (request.getId() != null && !Objects.equals(rule.getAccountId(), resolved)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "mail.rule.not-owned", "规则不属于该邮箱账号");
        }
        rule.setAccountId(resolved);
        rule.setName(request.getName() == null ? "" : request.getName().trim());
        rule.setSortNo(request.getSortNo() == null || request.getSortNo() <= 0
                ? DEFAULT_SORT_NO : request.getSortNo());
        rule.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? FLAG_OFF : ENABLED_ON);
        rule.setMatchFrom(trimToNull(request.getMatchFrom()));
        rule.setMatchSubject(trimToNull(request.getMatchSubject()));
        rule.setMatchTo(trimToNull(request.getMatchTo()));
        rule.setOnlyWithAttachment(Boolean.TRUE.equals(request.getOnlyWithAttachment()) ? FLAG_ON : FLAG_OFF);
        rule.setActionFolderId(request.getActionFolderId());
        rule.setActionMarkSeen(Boolean.TRUE.equals(request.getActionMarkSeen()) ? FLAG_ON : FLAG_OFF);
        rule.setActionFlagged(Boolean.TRUE.equals(request.getActionFlagged()) ? FLAG_ON : FLAG_OFF);
        validate(rule);
        if (request.getId() == null) {
            rule.setHitCount(0L);
            ruleMapper.insert(rule);
        } else {
            ruleMapper.updateById(rule);
        }
        return toVo(rule, folderNames(resolved));
    }

    @Override
    public void remove(Long id, Long userId) {
        MailRule rule = requireOwned(id, userId);
        ruleMapper.deleteById(rule.getId());
    }

    @Override
    public void setEnabled(Long id, Boolean enabled, Long userId) {
        MailRule rule = requireOwned(id, userId);
        MailRule patch = new MailRule();
        patch.setId(rule.getId());
        patch.setEnabled(Boolean.TRUE.equals(enabled) ? ENABLED_ON : FLAG_OFF);
        ruleMapper.updateById(patch);
    }

    @Override
    public List<MailRule> loadEnabled(Long accountId) {
        return ruleMapper.selectList(new LambdaQueryWrapper<MailRule>()
                .eq(MailRule::getAccountId, accountId)
                .eq(MailRule::getEnabled, ENABLED_ON)
                .orderByAsc(MailRule::getSortNo)
                .orderByAsc(MailRule::getId));
    }

    @Override
    public MailRule match(List<MailRule> rules, MailMessage message) {
        if (rules == null || rules.isEmpty() || message == null) {
            return null;
        }
        for (MailRule rule : rules) {
            if (!matches(rule, message)) {
                continue;
            }
            apply(rule, message);
            return rule;
        }
        return null;
    }

    @Override
    public MailRuleApplyVO applyToInbox(Long accountId, Long userId) {
        Long resolved = accountId == null ? accountService.requireDefaultAccountId(userId) : accountId;
        accountService.requireOwned(resolved, userId);
        MailRuleApplyVO result = new MailRuleApplyVO();
        result.setScanned(0);
        result.setChanged(0);
        result.setTruncated(false);
        List<MailRule> rules = loadEnabled(resolved);
        MailFolder inbox = folderService.findByCode(resolved, FOLDER_INBOX);
        if (rules.isEmpty() || inbox == null) {
            return result;
        }
        int limit = properties.getRule().getBackfillLimit();
        List<MailMessage> pending = messageMapper.selectList(new LambdaQueryWrapper<MailMessage>()
                .eq(MailMessage::getAccountId, resolved)
                .eq(MailMessage::getFolderId, inbox.getId())
                .orderByDesc(MailMessage::getSentTime)
                .last("LIMIT " + limit));
        result.setScanned(pending.size());
        result.setTruncated(pending.size() >= limit);
        Map<Long, Integer> hits = new HashMap<>();
        Set<Long> touchedFolders = new LinkedHashSet<>();
        touchedFolders.add(inbox.getId());
        int changed = 0;
        for (MailMessage message : pending) {
            Long beforeFolder = message.getFolderId();
            Integer beforeSeen = message.getSeen();
            Integer beforeFlagged = message.getFlagged();
            MailRule hit = match(rules, message);
            if (hit == null || !isChanged(message, beforeFolder, beforeSeen, beforeFlagged)) {
                continue;
            }
            messageMapper.updateById(message);
            // 归类位置变了就重写索引，否则「在当前文件夹里搜」会把已经归走的邮件漏掉
            searchService.index(message);
            touchedFolders.add(message.getFolderId());
            hits.merge(hit.getId(), 1, Integer::sum);
            changed++;
        }
        for (Map.Entry<Long, Integer> entry : hits.entrySet()) {
            recordHit(entry.getKey(), entry.getValue());
        }
        for (Long folderId : touchedFolders) {
            folderService.refreshCounters(folderId);
        }
        result.setChanged(changed);
        if (changed > 0) {
            log.info("规则回填完成: accountId={}, scanned={}, changed={}", resolved, pending.size(), changed);
        }
        return result;
    }

    @Override
    public void recordHit(MailRule rule) {
        if (rule == null || rule.getId() == null) {
            return;
        }
        recordHit(rule.getId(), 1);
    }

    /**
     * 累计命中：用 {@code hit_count = hit_count + n} 而不是先读后写，
     * 避免同步线程与回填请求同时命中同一条规则时丢计数。
     *
     * @param ruleId 规则ID
     * @param count 本次命中封数
     */
    private void recordHit(Long ruleId, int count) {
        ruleMapper.update(null, new LambdaUpdateWrapper<MailRule>()
                .eq(MailRule::getId, ruleId)
                .setSql("hit_count = hit_count + " + count)
                .set(MailRule::getLastHitTime, LocalDateTime.now()));
    }

    /**
     * 判断规则是否命中所属邮件
     *
     * @param rule 规则
     * @param message 邮件
     * @return 命中返回 {@code true}
     */
    private boolean matches(MailRule rule, MailMessage message) {
        if (rule.getOnlyWithAttachment() != null && rule.getOnlyWithAttachment() == FLAG_ON
                && !Objects.equals(message.getHasAttachment(), FLAG_ON)) {
            return false;
        }
        if (!containsAny(rule.getMatchFrom(), message.getFromAddr())) {
            return false;
        }
        if (!containsAny(rule.getMatchSubject(), message.getSubject())) {
            return false;
        }
        // 收件人条件同时看收件与抄送：多数归档规则的真实意图是「发给这个组的邮件」，
        // 而组地址经常出现在抄送里
        return containsAny(rule.getMatchTo(), joinAddresses(message.getToAddrs(), message.getCcAddrs()));
    }

    /**
     * 多值包含匹配：条件为空视为不限制，任一值被包含即命中
     *
     * @param condition 规则条件（逗号分隔）
     * @param target 被检查的文本
     * @return 命中返回 {@code true}
     */
    private boolean containsAny(String condition, String target) {
        if (!StringUtils.hasText(condition)) {
            return true;
        }
        if (!StringUtils.hasText(target)) {
            return false;
        }
        String haystack = target.toLowerCase(Locale.ROOT);
        for (String raw : condition.split(VALUE_SEPARATOR)) {
            String needle = raw.trim().toLowerCase(Locale.ROOT);
            if (!needle.isEmpty() && haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 拼接收件人与抄送
     *
     * @param toAddrs 收件人
     * @param ccAddrs 抄送
     * @return 拼接结果
     */
    private String joinAddresses(String toAddrs, String ccAddrs) {
        String to = toAddrs == null ? "" : toAddrs;
        String cc = ccAddrs == null ? "" : ccAddrs;
        return to + "," + cc;
    }

    /**
     * 规则动作落到邮件上（只改内存）
     *
     * @param rule 规则
     * @param message 邮件
     */
    private void apply(MailRule rule, MailMessage message) {
        if (rule.getActionFolderId() != null && !Objects.equals(rule.getActionFolderId(), message.getFolderId())) {
            message.setFolderId(rule.getActionFolderId());
        }
        if (rule.getActionMarkSeen() != null && rule.getActionMarkSeen() == FLAG_ON) {
            message.setSeen(FLAG_ON);
        }
        if (rule.getActionFlagged() != null && rule.getActionFlagged() == FLAG_ON) {
            message.setFlagged(FLAG_ON);
        }
    }

    /**
     * 判断邮件是否真的被改写（用于判断是否值得落库与计一次命中）
     *
     * @param message 邮件
     * @param beforeFolder 改写前文件夹
     * @param beforeSeen 改写前已读
     * @param beforeFlagged 改写前星标
     * @return 有变化返回 {@code true}
     */
    private boolean isChanged(MailMessage message, Long beforeFolder, Integer beforeSeen, Integer beforeFlagged) {
        return !Objects.equals(beforeFolder, message.getFolderId())
                || !Objects.equals(beforeSeen, message.getSeen())
                || !Objects.equals(beforeFlagged, message.getFlagged());
    }

    /**
     * 校验规则可用性
     *
     * @param rule 规则
     */
    private void validate(MailRule rule) {
        if (!StringUtils.hasText(rule.getName())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "mail.rule.name-empty", "规则名称不能为空");
        }
        if (rule.getName().length() > MAX_NAME_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR, "mail.rule.name-too-long", "规则名称过长");
        }
        if (!hasCondition(rule)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "mail.rule.condition-required",
                    "至少需要一个匹配条件，否则会命中所有邮件");
        }
        if (!hasAction(rule)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "mail.rule.action-required",
                    "至少需要一个动作，否则规则不产生任何效果");
        }
        if (rule.getActionFolderId() != null) {
            validateTargetFolder(rule);
        }
        if (rule.getId() == null) {
            validateQuota(rule.getAccountId());
        }
    }

    /**
     * 是否配置了匹配条件
     *
     * @param rule 规则
     * @return 有任一条件返回 {@code true}
     */
    private boolean hasCondition(MailRule rule) {
        return StringUtils.hasText(rule.getMatchFrom())
                || StringUtils.hasText(rule.getMatchSubject())
                || StringUtils.hasText(rule.getMatchTo())
                || Objects.equals(rule.getOnlyWithAttachment(), FLAG_ON);
    }

    /**
     * 是否配置了动作
     *
     * @param rule 规则
     * @return 有任一动作返回 {@code true}
     */
    private boolean hasAction(MailRule rule) {
        return rule.getActionFolderId() != null
                || Objects.equals(rule.getActionMarkSeen(), FLAG_ON)
                || Objects.equals(rule.getActionFlagged(), FLAG_ON);
    }

    /**
     * 校验目标文件夹：必须属于同一账号，且不能是回收站
     *
     * @param rule 规则
     */
    private void validateTargetFolder(MailRule rule) {
        MailFolder folder = folderMapper.selectById(rule.getActionFolderId());
        if (folder == null || !Objects.equals(folder.getAccountId(), rule.getAccountId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "mail.rule.target-folder-not-found", "目标文件夹不存在");
        }
        if (FOLDER_TRASH.equals(folder.getFolderCode())) {
            // 删信必须是人点的动作：规则一旦能静默丢回收站，误配的后果是「邮件莫名消失」
            throw new BizException(ErrorCode.PARAM_ERROR, "mail.rule.trash-target-forbidden",
                    "不能把规则目标设为回收站，删除需手动操作");
        }
    }

    /**
     * 校验规则条数配额
     *
     * @param accountId 账号ID
     */
    private void validateQuota(Long accountId) {
        Long count = ruleMapper.selectCount(new LambdaQueryWrapper<MailRule>()
                .eq(MailRule::getAccountId, accountId));
        int limit = properties.getRule().getMaxRulesPerAccount();
        if (count != null && count >= limit) {
            throw new BizException(ErrorCode.PARAM_ERROR, "mail.rule.limit-exceeded",
                    "规则数量已达上限 " + limit + " 条", limit);
        }
    }

    /**
     * 校验规则归属
     *
     * @param id 规则ID
     * @param userId 当前用户ID
     * @return 规则实体
     */
    private MailRule requireOwned(Long id, Long userId) {
        MailRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "mail.rule.not-found", "规则不存在");
        }
        accountService.requireOwned(rule.getAccountId(), userId);
        return rule;
    }

    /**
     * 账号下文件夹名称表
     *
     * @param accountId 账号ID
     * @return 文件夹ID → 名称
     */
    private Map<Long, String> folderNames(Long accountId) {
        List<MailFolder> folders = folderMapper.selectList(new LambdaQueryWrapper<MailFolder>()
                .eq(MailFolder::getAccountId, accountId));
        Map<Long, String> names = new HashMap<>();
        for (MailFolder folder : folders) {
            names.put(folder.getId(), folder.getName());
        }
        return names;
    }

    /**
     * 实体转视图
     *
     * @param rule 规则
     * @param folderNames 文件夹名称表
     * @return 视图
     */
    private MailRuleVO toVo(MailRule rule, Map<Long, String> folderNames) {
        MailRuleVO vo = new MailRuleVO();
        vo.setId(rule.getId());
        vo.setName(rule.getName());
        vo.setSortNo(rule.getSortNo());
        vo.setEnabled(Objects.equals(rule.getEnabled(), ENABLED_ON));
        vo.setMatchFrom(rule.getMatchFrom());
        vo.setMatchSubject(rule.getMatchSubject());
        vo.setMatchTo(rule.getMatchTo());
        vo.setOnlyWithAttachment(Objects.equals(rule.getOnlyWithAttachment(), FLAG_ON));
        vo.setActionFolderId(rule.getActionFolderId());
        vo.setActionFolderName(rule.getActionFolderId() == null
                ? null : folderNames.get(rule.getActionFolderId()));
        vo.setActionMarkSeen(Objects.equals(rule.getActionMarkSeen(), FLAG_ON));
        vo.setActionFlagged(Objects.equals(rule.getActionFlagged(), FLAG_ON));
        vo.setHitCount(rule.getHitCount() == null ? 0L : rule.getHitCount());
        vo.setLastHitTime(rule.getLastHitTime());
        return vo;
    }

    /**
     * 去掉首尾空白，空串归一为 {@code null}
     *
     * @param value 原值
     * @return 归一结果
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
