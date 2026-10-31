package com.bluewhale.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.dto.FactVO;
import com.bluewhale.agent.entity.AgentFact;
import com.bluewhale.agent.mapper.AgentFactMapper;
import com.bluewhale.agent.support.facts.FactCollector;
import com.bluewhale.agent.support.facts.FactRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 结构化事实仓储：调用各 {@link FactCollector} 采集并按类型全量替换落库。
 *
 * <p>重建采用「按类型物理删除 + 批量插入」保证幂等，避免逻辑删除行累积；
 * 查询按 {@code factType + factKey} 精确命中，供双通道回答的事实通道使用。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FactStoreService {

    /** 平台统一使用 Jackson 2 静态实例（Spring Boot 4 自动配置仅提供 Jackson 3 Bean） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 查询默认条数 */
    private static final int DEFAULT_LIMIT = 50;

    /** 查询最大条数 */
    private static final int MAX_LIMIT = 500;

    /** 运行时事实类型（受 {@code sys:agent:runtime:read} 权限保护） */
    public static final String RUNTIME_SERVICE = "RUNTIME_SERVICE";

    /** 采集时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 支持的事实类型（重建前统一清空，避免采集器失败时残留旧数据） */
    private static final List<String> FACT_TYPES = List.of(
            "SERVICE", "ROUTE", "PORT", "DEPENDENCY", "TABLE", "COLUMN", "COMMIT", "ENDPOINT", RUNTIME_SERVICE);

    private final List<FactCollector> collectors;

    private final AgentFactMapper factMapper;

    private final AgentProperties properties;

    private final AtomicBoolean collecting = new AtomicBoolean(false);

    private volatile int lastFactCount;

    /**
     * 全量重建结构化事实（同步执行）。
     *
     * @return 写入事实条数；已有任务在执行返回 {@code -1}
     */
    public int rebuild() {
        if (!collecting.compareAndSet(false, true)) {
            log.info("已有事实采集任务在执行，跳过本次重建");
            return -1;
        }
        long start = System.currentTimeMillis();
        try {
            List<FactRecord> records = new ArrayList<>();
            for (FactCollector collector : collectors) {
                try {
                    long collectorStart = System.currentTimeMillis();
                    List<FactRecord> collected = collector.collect(properties.resolveRepoRoot());
                    if (collected != null) {
                        records.addAll(collected);
                    }
                    log.debug("事实采集器 {} 完成：{} 条，耗时={}ms", collector.getClass().getSimpleName(),
                            collected == null ? 0 : collected.size(), System.currentTimeMillis() - collectorStart);
                } catch (RuntimeException e) {
                    log.warn("事实采集器执行失败，跳过: {}", collector.getClass().getSimpleName(), e);
                }
            }
            int written = replaceAll(records);
            lastFactCount = written;
            log.info("结构化事实重建完成：{} 条，耗时={}ms", written, System.currentTimeMillis() - start);
            return written;
        } finally {
            collecting.set(false);
        }
    }

    /**
     * 当前事实条数。
     *
     * @return 最近一次采集写入条数
     */
    public int lastFactCount() {
        return lastFactCount;
    }

    /**
     * 按类型与键查询事实。
     *
     * @param factType 事实类型（可空，空表示不限）
     * @param factKey  事实键（可空，空表示不限）
     * @param limit    返回条数（可空）
     * @return 事实列表
     */
    public List<AgentFact> query(String factType, String factKey, Integer limit) {
        return query(factType, factKey, limit, true);
    }

    /**
     * 按类型与键查询事实，可排除运行时事实。
     *
     * <p>调用方无 {@code sys:agent:runtime:read} 权限时传 {@code includeRuntime = false}：
     * 未指定类型（全量浏览）场景下剔除 {@link #RUNTIME_SERVICE}；指定类型由调用方自行拒绝。</p>
     *
     * @param factType       事实类型（可空，空表示不限）
     * @param factKey        事实键（可空，空表示不限）
     * @param limit          返回条数（可空）
     * @param includeRuntime 是否包含运行时事实
     * @return 事实列表
     */
    public List<AgentFact> query(String factType, String factKey, Integer limit, boolean includeRuntime) {
        LambdaQueryWrapper<AgentFact> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(factType), AgentFact::getFactType, factType);
        wrapper.eq(StringUtils.hasText(factKey), AgentFact::getFactKey, factKey);
        if (!includeRuntime && !StringUtils.hasText(factType)) {
            wrapper.ne(AgentFact::getFactType, RUNTIME_SERVICE);
        }
        wrapper.orderByAsc(AgentFact::getFactType).orderByAsc(AgentFact::getFactKey);
        wrapper.last("LIMIT " + normalizeLimit(limit));
        return factMapper.selectList(wrapper);
    }

    /**
     * 按类型与键查询事实并装配为视图对象。
     *
     * <p>响应装配下沉到服务层，避免 controller 直接依赖 {@link AgentFact} 实体（T-18 分层约束）。</p>
     *
     * @param factType       事实类型（可空，空表示不限）
     * @param factKey        事实键（可空，空表示不限）
     * @param limit          返回条数（可空）
     * @param includeRuntime 是否包含运行时事实
     * @return 事实视图对象列表
     */
    public List<FactVO> queryVo(String factType, String factKey, Integer limit, boolean includeRuntime) {
        List<AgentFact> facts = query(factType, factKey, limit, includeRuntime);
        List<FactVO> result = new ArrayList<>(facts.size());
        for (AgentFact fact : facts) {
            result.add(FactVO.builder()
                    .id(String.valueOf(fact.getId()))
                    .factType(fact.getFactType())
                    .factKey(fact.getFactKey())
                    .value(parseValue(fact.getValueJson()))
                    .sourcePath(fact.getSourcePath())
                    .capturedAt(formatTime(fact.getCapturedAt()))
                    .build());
        }
        return result;
    }

    /**
     * 按类型与键前缀/关键词模糊查询事实。
     *
     * @param factType 事实类型
     * @param keyword  键关键词（SQL LIKE，自动包裹 {@code %}）
     * @param limit    返回条数
     * @return 事实列表
     */
    public List<AgentFact> queryLike(String factType, String keyword, Integer limit) {
        if (!StringUtils.hasText(factType) || !StringUtils.hasText(keyword)) {
            return List.of();
        }
        LambdaQueryWrapper<AgentFact> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentFact::getFactType, factType);
        wrapper.like(AgentFact::getFactKey, keyword);
        wrapper.orderByAsc(AgentFact::getFactKey);
        wrapper.last("LIMIT " + normalizeLimit(limit));
        return factMapper.selectList(wrapper);
    }

    /**
     * 按类型与值关键词模糊查询事实。
     *
     * @param factType 事实类型
     * @param keyword  值关键词（SQL LIKE，自动包裹 {@code %}）
     * @param limit    返回条数
     * @return 事实列表
     */
    public List<AgentFact> queryValueLike(String factType, String keyword, Integer limit) {
        if (!StringUtils.hasText(factType) || !StringUtils.hasText(keyword)) {
            return List.of();
        }
        LambdaQueryWrapper<AgentFact> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentFact::getFactType, factType);
        wrapper.like(AgentFact::getValueJson, keyword);
        wrapper.orderByAsc(AgentFact::getFactKey);
        wrapper.last("LIMIT " + normalizeLimit(limit));
        return factMapper.selectList(wrapper);
    }

    /**
     * 事实值 JSON 转对象（失败时返回原始字符串）。
     *
     * @param valueJson 事实值 JSON
     * @return 解析结果
     */
    public Object parseValue(String valueJson) {
        if (!StringUtils.hasText(valueJson)) {
            return null;
        }
        try {
            return MAPPER.readValue(valueJson, Object.class);
        } catch (JsonProcessingException e) {
            return valueJson;
        }
    }

    /**
     * 采集时间格式化。
     *
     * @param capturedAt 采集时间
     * @return ISO-8601 字符串；空值返回 {@code null}
     */
    public static String formatTime(LocalDateTime capturedAt) {
        return capturedAt == null ? null : capturedAt.format(TIME_FORMAT);
    }

    /**
     * 按类型清空并写入全部事实。
     *
     * @param records 事实记录
     * @return 写入条数
     */
    private int replaceAll(List<FactRecord> records) {
        for (String factType : FACT_TYPES) {
            factMapper.purgeByType(factType);
        }
        Map<String, List<FactRecord>> grouped = new LinkedHashMap<>();
        for (FactRecord record : records) {
            grouped.computeIfAbsent(record.factType(), ignored -> new ArrayList<>()).add(record);
        }
        LocalDateTime capturedAt = LocalDateTime.now();
        int written = 0;
        for (Map.Entry<String, List<FactRecord>> entry : grouped.entrySet()) {
            for (FactRecord record : entry.getValue()) {
                AgentFact fact = new AgentFact();
                fact.setFactType(record.factType());
                fact.setFactKey(record.factKey());
                fact.setValueJson(toJson(record));
                fact.setSourcePath(record.sourcePath());
                fact.setCapturedAt(capturedAt);
                factMapper.insert(fact);
                written++;
            }
        }
        return written;
    }

    /**
     * 事实值序列化为 JSON。
     *
     * @param record 事实记录
     * @return JSON 字符串
     */
    private String toJson(FactRecord record) {
        try {
            return MAPPER.writeValueAsString(record.value());
        } catch (JsonProcessingException e) {
            log.warn("事实值序列化失败: {}#{}", record.factType(), record.factKey(), e);
            return "{}";
        }
    }

    /**
     * 归一化查询条数。
     *
     * @param limit 原始条数
     * @return 归一化条数
     */
    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
