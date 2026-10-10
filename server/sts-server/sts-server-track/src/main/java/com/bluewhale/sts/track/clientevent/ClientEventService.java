package com.bluewhale.sts.track.clientevent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.common.basic.enums.ResultCode;
import com.bluewhale.sts.common.basic.exception.BusinessException;
import com.bluewhale.sts.common.basic.model.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 客户端行为事件应用服务。
 *
 * <p><b>与客户端日志的边界（重要）：</b>日志是"技术切片"，用于排查故障，量大、可采样、可丢弃；
 * 行为事件是"业务事实"，用于产品/增长分析，要求可追溯、可去重、结构与语义稳定。
 * 因此本服务比日志域多做了三件事：<b>事件名强校验</b>（它是永久契约）、
 * <b>props 结构版本</b>（结构变了要有据可循）、<b>userId/deviceId 身份维度</b>（支撑身份缝合）。
 */
@Service
public class ClientEventService {

    private static final Logger log = LoggerFactory.getLogger(ClientEventService.class);

    /**
     * 事件名合法形态：小写、点分、至少两段，形如 {@code order.submit}。
     *
     * <p><b>为什么强制这个格式：</b>事件名是**永久契约** —— 一旦有数据落库，
     * 消费方（看板、漏斗、告警）就会按它来查询。改名意味着历史数据与新数据被劈成两半，
     * 且改名这件事往往只在"上游以为"的层面发生，下游要到某天发现图表断崖才察觉。
     * 从入口就统一形状，让"一个功能只可能有一个名字"成为可校验的约束，
     * 而不是靠评审时的口头约定。
     *
     * <p>要求至少两段（含点）是刻意的：单段名（如 {@code click}）无法表达归属，
     * 迟早出现 {@code click} / {@code clicked} / {@code Click} 三个近义名并存；
     * 强制 {@code 域.动作} 之后，新增事件时作者必须先想清楚它属于哪个域。
     */
    private static final Pattern EVENT_NAME_PATTERN =
            Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$");

    /** 事件名长度上限，与 {@code event_name VARCHAR(128)} 对齐 */
    private static final int MAX_EVENT_NAME_LENGTH = 128;

    /** props 序列化后的长度上限 */
    private static final int MAX_JSON_LENGTH = 8000;

    /** 查询默认时间窗：7 天 */
    private static final long DEFAULT_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClientEventMapper clientEventMapper;

    private final ObjectMapper objectMapper;

    public ClientEventService(ClientEventMapper clientEventMapper, ObjectMapper objectMapper) {
        this.clientEventMapper = clientEventMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 批量接收上报。
     *
     * <p><b>逐条校验，而非整批拒绝：</b>事件上报是尽力而为的通道。若一条畸形数据能让整批 500 条
     * 全部被拒，那么客户端的事件会成片丢失，且丢的是"上报那一刻最需要的那批"。
     * 因此：信封级问题（app 为空、批次超限）由 Bean Validation 整体拒绝 ——
     * 那是调用方写错了代码，应当立刻暴露；条目级问题（事件名为空/非法）逐条跳过并计数，
     * 由返回值告诉调用方丢了什么。
     */
    public ClientEventReportResultVO report(ClientEventReportRequest request, String ip) {
        long serverTime = System.currentTimeMillis();
        List<ClientEventEntity> entities = new ArrayList<>(request.getEvents().size());
        int rejected = 0;

        for (ClientEventItem item : request.getEvents()) {
            if (!isValidEventName(item.getEventName())) {
                rejected++;
                continue;
            }

            ClientEventEntity entity = new ClientEventEntity();
            entity.setEventId(truncate(item.getEventId(), 64));
            entity.setEventName(item.getEventName());
            entity.setApp(request.getApp());
            entity.setUserId(truncate(item.getUserId(), 64));
            entity.setDeviceId(truncate(item.getDeviceId(), 64));
            // 条目级字段优先，缺省回落到信封级 —— 让"整批同源"这一常见情形不必逐条重复
            entity.setSessionId(truncate(firstNonBlank(item.getSessionId(), request.getSessionId()), 64));
            entity.setRoute(truncate(item.getRoute(), 512));
            entity.setAppRelease(truncate(firstNonBlank(item.getRelease(), request.getRelease()), 64));
            entity.setPropsJson(toJson(item.getProps(), MAX_JSON_LENGTH));
            entity.setPropsVersion(item.getPropsVersion());
            entity.setClientTime(item.getTimestamp());
            // 双时间戳：客户端报的只作展示，排序/过滤一律用服务端接收时间，
            // 否则客户端时钟一偏，事件的先后顺序与时间窗查询就全是错的。
            entity.setServerTime(serverTime);
            entity.setIp(truncate(ip, 64));
            entities.add(entity);
        }

        if (!entities.isEmpty()) {
            clientEventMapper.insertBatch(entities);
        }
        if (rejected > 0) {
            // 记 WARN 而不是 ERROR：这是数据质量问题，不是故障。
            // 记 ERROR 会让"客户端上报了畸形事件"和"服务端挂了"进入同一个告警通道。
            log.warn("客户端事件上报存在被拒条目 | app={} | accepted={} | rejected={}",
                    request.getApp(), entities.size(), rejected);
        }
        return new ClientEventReportResultVO(entities.size(), rejected);
    }

    @Transactional(readOnly = true)
    public PageResult<ClientEventVO> page(ClientEventQuery query) {
        LambdaQueryWrapper<ClientEventEntity> wrapper = buildWrapper(query);

        Page<ClientEventEntity> page = clientEventMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        List<ClientEventVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, page.getTotal(), query.getPage(), query.getSize());
    }

    @Transactional(readOnly = true)
    public ClientEventVO detail(long id) {
        ClientEventEntity entity = clientEventMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "事件不存在或已被清理");
        }
        return toVO(entity);
    }

    @Transactional(readOnly = true)
    public ClientEventStatsVO stats(ClientEventStatsQuery query) {
        long endTime = query.getEndTime() == null ? System.currentTimeMillis() : query.getEndTime();
        long startTime = query.getStartTime() == null ? endTime - DEFAULT_WINDOW_MILLIS : query.getStartTime();

        LambdaQueryWrapper<ClientEventEntity> wrapper = Wrappers.<ClientEventEntity>lambdaQuery()
                .eq(hasText(query.getApp()), ClientEventEntity::getApp, query.getApp())
                .ge(ClientEventEntity::getServerTime, startTime)
                .le(ClientEventEntity::getServerTime, endTime);

        long total = clientEventMapper.selectCount(wrapper);
        List<ClientEventNameCount> names =
                clientEventMapper.countByEventName(query.getApp(), startTime, endTime);

        return new ClientEventStatsVO(total, startTime, endTime, names);
    }

    private LambdaQueryWrapper<ClientEventEntity> buildWrapper(ClientEventQuery query) {
        long endTime = query.getEndTime() == null ? System.currentTimeMillis() : query.getEndTime();
        long startTime = query.getStartTime() == null ? endTime - DEFAULT_WINDOW_MILLIS : query.getStartTime();

        return Wrappers.<ClientEventEntity>lambdaQuery()
                .eq(hasText(query.getApp()), ClientEventEntity::getApp, query.getApp())
                .eq(hasText(query.getEventName()), ClientEventEntity::getEventName, query.getEventName())
                .eq(hasText(query.getUserId()), ClientEventEntity::getUserId, query.getUserId())
                .eq(hasText(query.getDeviceId()), ClientEventEntity::getDeviceId, query.getDeviceId())
                .eq(hasText(query.getSessionId()), ClientEventEntity::getSessionId, query.getSessionId())
                .ge(ClientEventEntity::getServerTime, startTime)
                .le(ClientEventEntity::getServerTime, endTime)
                // 次级排序键用 id：同一毫秒内的多条记录若只按时间排序，翻页时顺序不稳定，
                // 会出现"某条事件在第 1 页和第 2 页都出现"或"哪一页都没有"。
                .orderByDesc(ClientEventEntity::getServerTime)
                .orderByDesc(ClientEventEntity::getId);
    }

    private ClientEventVO toVO(ClientEventEntity entity) {
        ClientEventVO vo = new ClientEventVO();
        vo.setId(entity.getId());
        vo.setEventId(entity.getEventId());
        vo.setEventName(entity.getEventName());
        vo.setApp(entity.getApp());
        vo.setUserId(entity.getUserId());
        vo.setDeviceId(entity.getDeviceId());
        vo.setSessionId(entity.getSessionId());
        vo.setRoute(entity.getRoute());
        // 实体属性叫 appRelease（列名 app_release），对外仍是 release，
        // 差异原因见 ClientEventEntity 的字段注释 —— 这里显式映射而不是靠属性名自动拷贝
        vo.setRelease(entity.getAppRelease());
        vo.setProps(readJson(entity.getPropsJson()));
        vo.setPropsVersion(entity.getPropsVersion());
        vo.setTimestamp(entity.getClientTime());
        vo.setServerTime(entity.getServerTime());
        vo.setIp(entity.getIp());
        return vo;
    }

    /**
     * 校验事件名。非空、长度不超限、且匹配 {@link #EVENT_NAME_PATTERN}。
     *
     * <p>不做大小写兜底（比如把 {@code Order.Submit} 转成小写放行）：事件名是永久契约，
     * 悄悄"纠正"一个拼错的名字，等于让"上游写错了名字"这件事永远暴露不出来，
     * 而错误的名字已经进了库。宁可拒收并计数，让调用方看到并修正。
     */
    static boolean isValidEventName(String name) {
        return hasText(name)
                && name.length() <= MAX_EVENT_NAME_LENGTH
                && EVENT_NAME_PATTERN.matcher(name).matches();
    }

    private String toJson(Object value, int maxLength) {
        if (value == null) {
            return null;
        }
        try {
            return truncate(objectMapper.writeValueAsString(value), maxLength);
        } catch (Exception ex) {
            // props 序列化失败不该影响这条事件落库：事件名与身份维度才是分析的主体，
            // 丢一个属性字段远好过丢一整条事件。
            log.warn("事件属性序列化失败，已丢弃该字段 | message={}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> readJson(String json) {
        if (!hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            log.warn("事件属性反序列化失败，已按空值返回 | message={}", ex.getMessage());
            return Map.of();
        }
    }

    /**
     * 截断超长文本。
     *
     * <p><b>为什么截断而不是丢弃整条：</b>超长的通常是属性里夹带了整段响应体或长文本。
     * 丢掉整条等于丢掉"用户做过这件事"这个事实，而截断至少保留了开头 ——
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
