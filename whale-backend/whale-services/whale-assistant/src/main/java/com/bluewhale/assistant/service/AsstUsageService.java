package com.bluewhale.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.assistant.dto.UsageVO;
import com.bluewhale.assistant.entity.AsstUsage;
import com.bluewhale.assistant.mapper.AsstUsageMapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用量日聚合服务：按 助手×天 覆盖式累加（幂等 upsert，不挂 Redis 锁——单行热点可接受）。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsstUsageService {

    /** 统计时区 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final AsstUsageMapper usageMapper;

    private final AsstDefinitionService definitionService;

    /**
     * 用量看板分页（助手×天聚合，管理视角）。
     *
     * @param assistantId 助手过滤（可空）
     * @param startDate   起始日期（含，ISO-8601，可空）
     * @param endDate     结束日期（含，可空）
     * @param current     当前页
     * @param size        每页条数
     * @return 分页结果（按日期倒序）
     */
    public PageResult<UsageVO> page(Long assistantId, String startDate, String endDate,
                                    long current, long size) {
        long pageNo = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<AsstUsage> wrapper = new LambdaQueryWrapper<>();
        if (assistantId != null) {
            wrapper.eq(AsstUsage::getAssistantId, assistantId);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(AsstUsage::getStatDate, parseDate(startDate, "startDate"));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(AsstUsage::getStatDate, parseDate(endDate, "endDate"));
        }
        wrapper.orderByDesc(AsstUsage::getStatDate).orderByDesc(AsstUsage::getAssistantId);
        Page<AsstUsage> page = usageMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Map<Long, String[]> assistantLabels = assistantLabels(page.getRecords());
        List<UsageVO> records = new ArrayList<>(page.getRecords().size());
        for (AsstUsage usage : page.getRecords()) {
            String[] label = assistantLabels.getOrDefault(usage.getAssistantId(),
                    new String[]{String.valueOf(usage.getAssistantId()), ""});
            records.add(UsageVO.builder()
                    .assistantId(String.valueOf(usage.getAssistantId()))
                    .assistantName(label[0])
                    .assistantCode(label[1])
                    .statDate(usage.getStatDate() == null ? null : usage.getStatDate().toString())
                    .sessionCount(usage.getSessionCount() == null ? 0 : usage.getSessionCount())
                    .messageCount(usage.getMessageCount() == null ? 0 : usage.getMessageCount())
                    .completionTokens(usage.getCompletionTokens() == null ? 0L : usage.getCompletionTokens())
                    .build());
        }
        return PageResult.of(records, page.getTotal(), pageNo, pageSize);
    }

    /**
     * 解析 ISO-8601 日期参数。
     *
     * @param value  日期串
     * @param source 参数名（报错用）
     * @return 日期
     */
    private static LocalDate parseDate(String value, String source) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "assistant.usage.date-format-invalid",
                    source + " 日期格式非法（应为 yyyy-MM-dd）", source);
        }
    }

    /**
     * 批量取助手名称与编码（助手被删时回退 ID 展示）。
     *
     * @param usages 用量行
     * @return 助手ID → [名称, 编码]
     */
    private Map<Long, String[]> assistantLabels(List<AsstUsage> usages) {
        Map<Long, String[]> labels = new HashMap<>();
        for (AsstUsage usage : usages) {
            Long assistantId = usage.getAssistantId();
            if (assistantId == null || labels.containsKey(assistantId)) {
                continue;
            }
            try {
                com.bluewhale.assistant.entity.AsstDefinition definition = definitionService.requireExists(assistantId);
                labels.put(assistantId, new String[]{definition.getName(), definition.getCode()});
            } catch (BizException e) {
                labels.put(assistantId, new String[]{String.valueOf(assistantId), ""});
            }
        }
        return labels;
    }

    /**
     * 记录一次回答用量（token 为 whale-ai 估算值，记入 completionTokens）。
     *
     * @param assistantId 助手ID
     * @param tokens      token 数
     */
    public void recordAnswer(Long assistantId, int tokens) {
        LocalDate today = LocalDate.now(ZONE);
        LambdaQueryWrapper<AsstUsage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AsstUsage::getAssistantId, assistantId);
        wrapper.eq(AsstUsage::getStatDate, today);
        AsstUsage usage = usageMapper.selectOne(wrapper);
        if (usage == null) {
            usage = new AsstUsage();
            usage.setAssistantId(assistantId);
            usage.setStatDate(today);
            usage.setSessionCount(0);
            usage.setMessageCount(1);
            usage.setPromptTokens(0L);
            usage.setCompletionTokens((long) Math.max(0, tokens));
            try {
                usageMapper.insert(usage);
            } catch (RuntimeException e) {
                // 并发首插撞唯一键：回落为累加更新
                log.debug("用量首插冲突，回落更新：assistant={}", assistantId);
                upsertExisting(assistantId, today, tokens);
            }
            return;
        }
        upsert(usage, tokens);
    }

    /**
     * 记录一次新建会话。
     *
     * @param assistantId 助手ID
     */
    public void recordSession(Long assistantId) {
        record(assistantId, true, 0);
    }

    /**
     * 通用记录入口。
     *
     * @param assistantId 助手ID
     * @param session     是否会话事件
     * @param tokens      token 数
     */
    private void record(Long assistantId, boolean session, int tokens) {
        LocalDate today = LocalDate.now(ZONE);
        LambdaQueryWrapper<AsstUsage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AsstUsage::getAssistantId, assistantId);
        wrapper.eq(AsstUsage::getStatDate, today);
        AsstUsage usage = usageMapper.selectOne(wrapper);
        if (usage == null) {
            usage = new AsstUsage();
            usage.setAssistantId(assistantId);
            usage.setStatDate(today);
            usage.setSessionCount(session ? 1 : 0);
            usage.setMessageCount(0);
            usage.setPromptTokens(0L);
            usage.setCompletionTokens((long) Math.max(0, tokens));
            try {
                usageMapper.insert(usage);
            } catch (RuntimeException e) {
                log.debug("用量首插冲突，回落更新：assistant={}", assistantId);
                upsertExisting(assistantId, today, session, tokens);
            }
            return;
        }
        if (session) {
            usage.setSessionCount((usage.getSessionCount() == null ? 0 : usage.getSessionCount()) + 1);
        }
        upsert(usage, tokens);
    }

    /**
     * 累加更新既有行。
     *
     * @param usage  用量行
     * @param tokens token 数
     */
    private void upsert(AsstUsage usage, int tokens) {
        usage.setMessageCount((usage.getMessageCount() == null ? 0 : usage.getMessageCount()) + 1);
        usage.setCompletionTokens(
                (usage.getCompletionTokens() == null ? 0L : usage.getCompletionTokens()) + Math.max(0, tokens));
        usageMapper.updateById(usage);
    }

    /**
     * 首插冲突后的累加更新。
     *
     * @param assistantId 助手ID
     * @param today       日期
     * @param tokens      token 数
     */
    private void upsertExisting(Long assistantId, LocalDate today, int tokens) {
        upsertExisting(assistantId, today, false, tokens);
    }

    /**
     * 首插冲突后的累加更新（含会话事件）。
     *
     * @param assistantId 助手ID
     * @param today       日期
     * @param session     是否会话事件
     * @param tokens      token 数
     */
    private void upsertExisting(Long assistantId, LocalDate today, boolean session, int tokens) {
        LambdaQueryWrapper<AsstUsage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AsstUsage::getAssistantId, assistantId);
        wrapper.eq(AsstUsage::getStatDate, today);
        AsstUsage usage = usageMapper.selectOne(wrapper);
        if (usage == null) {
            return;
        }
        if (session) {
            usage.setSessionCount((usage.getSessionCount() == null ? 0 : usage.getSessionCount()) + 1);
        }
        upsert(usage, tokens);
    }
}
