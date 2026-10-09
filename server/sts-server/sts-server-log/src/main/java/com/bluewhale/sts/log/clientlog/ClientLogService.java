package com.bluewhale.sts.log.clientlog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.common.basic.enums.ResultCode;
import com.bluewhale.sts.common.basic.exception.BusinessException;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 客户端日志应用服务。
 *
 * <p><b>与审计日志的边界（重要）：</b>本服务处理的是**技术日志** —— 用于排查故障，
 * 特点是量大、短保留期、可采样、可丢弃。审计日志（谁在何时对哪个资源做了什么、
 * 成功还是拒绝）走另一条管道，且**权威来源必须是后端拦截器**：这些事实前端根本不知道
 * （前端不知道请求最终是否落库成功），而且客户端上报的内容不可信。
 * 详见 {@code docs/decisions/0004-前端日志上报架构.md}。
 */
@Service
public class ClientLogService {

    private static final Logger log = LoggerFactory.getLogger(ClientLogService.class);

    /** 消息正文入库上限。超出即截断 —— 见 {@link #truncate} 对"截断还是丢弃"的取舍说明 */
    private static final int MAX_MESSAGE_LENGTH = 4000;

    /** data / context 序列化后的长度上限 */
    private static final int MAX_JSON_LENGTH = 8000;

    /** 查询默认时间窗：7 天 */
    private static final long DEFAULT_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClientLogMapper clientLogMapper;

    private final ObjectMapper objectMapper;

    public ClientLogService(ClientLogMapper clientLogMapper, ObjectMapper objectMapper) {
        this.clientLogMapper = clientLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 批量接收上报。
     *
     * <p><b>逐条校验，而非整批拒绝：</b>日志上报是尽力而为的通道。若一条畸形数据能让整批 500 条
     * 全部被拒，那么客户端的日志会成片丢失，且丢的是"上报那一刻最需要的那批"。
     * 因此：信封级问题（app 为空、批次超限）由 Bean Validation 整体拒绝 ——
     * 那是调用方写错了代码，应当立刻暴露；条目级问题（级别非法、消息为空）逐条跳过并计数，
     * 由返回值告诉调用方丢了什么。
     */
    public ClientLogReportResultVO report(ClientLogReportRequest request, String ip) {
        long serverTime = System.currentTimeMillis();
        List<ClientLogEntity> entities = new ArrayList<>(request.getLogs().size());
        int rejected = 0;

        for (ClientLogItem item : request.getLogs()) {
            ClientLogLevel level = ClientLogLevel.parse(item.getLevel());
            if (level == null || !hasText(item.getMessage())) {
                rejected++;
                continue;
            }

            ClientLogEntity entity = new ClientLogEntity();
            entity.setApp(request.getApp());
            entity.setLevel(level.wireName());
            entity.setNamespace(truncate(item.getNamespace(), 255));
            entity.setMessage(truncate(item.getMessage(), MAX_MESSAGE_LENGTH));
            entity.setDataJson(toJson(item.getData(), MAX_JSON_LENGTH));
            entity.setContextJson(toJson(item.getContext(), MAX_JSON_LENGTH));
            entity.setTraceId(truncate(item.getTraceId(), 64));
            // 条目级字段优先，缺省回落到信封级 —— 让"整批同源"这一常见情形不必逐条重复
            entity.setSessionId(truncate(firstNonBlank(item.getSessionId(), request.getSessionId()), 64));
            entity.setRoute(truncate(item.getRoute(), 512));
            entity.setRelease(truncate(firstNonBlank(item.getRelease(), request.getRelease()), 64));
            entity.setClientTime(item.getTimestamp());
            entity.setServerTime(serverTime);
            entity.setIp(truncate(ip, 64));
            entities.add(entity);
        }

        if (!entities.isEmpty()) {
            clientLogMapper.insertBatch(entities);
        }
        if (rejected > 0) {
            // 记 WARN 而不是 ERROR：这是数据质量问题，不是故障。
            // 记 ERROR 会让"客户端上报了畸形日志"和"服务端挂了"进入同一个告警通道。
            log.warn("客户端日志上报存在被拒条目 | app={} | accepted={} | rejected={}",
                    request.getApp(), entities.size(), rejected);
        }
        return new ClientLogReportResultVO(entities.size(), rejected);
    }

    @Transactional(readOnly = true)
    public PageResult<ClientLogVO> page(ClientLogQuery query) {
        LambdaQueryWrapper<ClientLogEntity> wrapper = buildWrapper(query);

        Page<ClientLogEntity> page = clientLogMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        List<ClientLogVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, page.getTotal(), query.getPage(), query.getSize());
    }

    @Transactional(readOnly = true)
    public ClientLogVO detail(long id) {
        ClientLogEntity entity = clientLogMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "日志不存在或已被清理");
        }
        return toVO(entity);
    }

    /**
     * 按链路标识取整条链路。
     *
     * <p>这是日志服务最有价值的查询：一个用户操作会同时产生前端日志与后端日志，
     * 它们用同一个 {@code traceId} 串联。没有它，排查一次失败请求需要在两个系统里
     * 按时间戳手工对齐。
     *
     * <p>按时间**升序**返回（与其他接口相反）：链路的阅读顺序就是发生顺序，
     * 倒序会让调用方多写一次 reverse。
     */
    @Transactional(readOnly = true)
    public List<ClientLogVO> listByTraceId(String traceId) {
        if (!hasText(traceId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "traceId 不能为空");
        }
        List<ClientLogEntity> entities = clientLogMapper.selectList(
                Wrappers.<ClientLogEntity>lambdaQuery()
                        .eq(ClientLogEntity::getTraceId, traceId)
                        .orderByAsc(ClientLogEntity::getServerTime));
        return entities.stream().map(this::toVO).toList();
    }

    @Transactional(readOnly = true)
    public ClientLogStatsVO stats(ClientLogStatsQuery query) {
        long endTime = query.getEndTime() == null ? System.currentTimeMillis() : query.getEndTime();
        long startTime = query.getStartTime() == null ? endTime - DEFAULT_WINDOW_MILLIS : query.getStartTime();

        LambdaQueryWrapper<ClientLogEntity> wrapper = Wrappers.<ClientLogEntity>lambdaQuery()
                .eq(hasText(query.getApp()), ClientLogEntity::getApp, query.getApp())
                .ge(ClientLogEntity::getServerTime, startTime)
                .le(ClientLogEntity::getServerTime, endTime);

        long total = clientLogMapper.selectCount(wrapper);

        Map<String, Long> counted = new HashMap<>();
        for (ClientLogLevelCount row : clientLogMapper.countByLevel(query.getApp(), startTime, endTime)) {
            if (row.getLevel() != null) {
                counted.put(row.getLevel().toLowerCase(Locale.ROOT),
                        row.getLevelCount() == null ? 0L : row.getLevelCount());
            }
        }

        // 补齐没有数据的级别：绘图方不该为了"某级别当天没有日志"写一遍补零逻辑，
        // 而这种补零逻辑一旦落在每个消费方手里，就会出现"这个图表少了一根柱子"的诡异现象。
        List<ClientLogLevelCount> levels = new ArrayList<>(ClientLogLevel.values().length);
        for (ClientLogLevel level : ClientLogLevel.values()) {
            ClientLogLevelCount item = new ClientLogLevelCount();
            item.setLevel(level.wireName());
            item.setLevelCount(counted.getOrDefault(level.wireName(), 0L));
            levels.add(item);
        }

        return new ClientLogStatsVO(total, startTime, endTime, levels);
    }

    private LambdaQueryWrapper<ClientLogEntity> buildWrapper(ClientLogQuery query) {
        long endTime = query.getEndTime() == null ? System.currentTimeMillis() : query.getEndTime();
        long startTime = query.getStartTime() == null ? endTime - DEFAULT_WINDOW_MILLIS : query.getStartTime();

        return Wrappers.<ClientLogEntity>lambdaQuery()
                .eq(hasText(query.getApp()), ClientLogEntity::getApp, query.getApp())
                .eq(hasText(query.getLevel()), ClientLogEntity::getLevel,
                        query.getLevel() == null ? null : query.getLevel().toLowerCase(Locale.ROOT))
                .eq(hasText(query.getNamespace()), ClientLogEntity::getNamespace, query.getNamespace())
                .eq(hasText(query.getTraceId()), ClientLogEntity::getTraceId, query.getTraceId())
                .eq(hasText(query.getSessionId()), ClientLogEntity::getSessionId, query.getSessionId())
                .like(hasText(query.getKeyword()), ClientLogEntity::getMessage, query.getKeyword())
                .ge(ClientLogEntity::getServerTime, startTime)
                .le(ClientLogEntity::getServerTime, endTime)
                // 次级排序键用 id：同一毫秒内的多条记录若只按时间排序，翻页时顺序不稳定，
                // 会出现"某条日志在第 1 页和第 2 页都出现"或"哪一页都没有"。
                .orderByDesc(ClientLogEntity::getServerTime)
                .orderByDesc(ClientLogEntity::getId);
    }

    private ClientLogVO toVO(ClientLogEntity entity) {
        ClientLogVO vo = new ClientLogVO();
        vo.setId(entity.getId());
        vo.setApp(entity.getApp());
        vo.setLevel(entity.getLevel());
        vo.setNamespace(entity.getNamespace());
        vo.setMessage(entity.getMessage());
        vo.setData(readJson(entity.getDataJson(), LIST_TYPE, List.of()));
        vo.setContext(readJson(entity.getContextJson(), MAP_TYPE, Map.of()));
        vo.setTraceId(entity.getTraceId());
        vo.setSessionId(entity.getSessionId());
        vo.setRoute(entity.getRoute());
        vo.setRelease(entity.getRelease());
        vo.setTimestamp(entity.getClientTime());
        vo.setServerTime(entity.getServerTime());
        vo.setIp(entity.getIp());
        return vo;
    }

    private String toJson(Object value, int maxLength) {
        if (value == null) {
            return null;
        }
        try {
            return truncate(objectMapper.writeValueAsString(value), maxLength);
        } catch (Exception ex) {
            // 结构化数据序列化失败不该影响这条日志落库：正文才是排查的主体。
            log.warn("日志附加数据序列化失败，已丢弃该字段 | message={}", ex.getMessage());
            return null;
        }
    }

    private <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (!hasText(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            log.warn("日志附加数据反序列化失败，已按空值返回 | message={}", ex.getMessage());
            return fallback;
        }
    }

    /**
     * 截断超长文本。
     *
     * <p><b>为什么截断而不是丢弃整条：</b>超长的通常是消息里夹带了整段响应体或堆栈。
     * 丢掉整条等于丢掉"这里出过事"这个事实，而截断至少保留了开头 ——
     * 也就是最可能有价值的那部分。截断处留标记，避免读者以为原始内容就这么长。
     */
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "…(已截断)";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String first, String second) {
        return hasText(first) ? first : second;
    }

}
