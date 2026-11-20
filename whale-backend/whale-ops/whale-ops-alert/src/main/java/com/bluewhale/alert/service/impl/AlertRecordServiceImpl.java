package com.bluewhale.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.alert.config.AlertIngestProperties;
import com.bluewhale.alert.converter.AlertConverter;
import com.bluewhale.alert.dto.AlertAckRequest;
import com.bluewhale.alert.dto.AlertRecordCreateRequest;
import com.bluewhale.alert.dto.AlertRecordQueryRequest;
import com.bluewhale.alert.dto.PrometheusWebhookPayload;
import com.bluewhale.alert.entity.AlertRecord;
import com.bluewhale.alert.mapper.AlertRecordMapper;
import com.bluewhale.alert.service.AlertRecordService;
import com.bluewhale.alert.support.AlertRecordSupport;
import com.bluewhale.alert.vo.AlertRecordStatsVO;
import com.bluewhale.alert.vo.AlertRecordVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警记录服务实现。
 *
 * <p>查询、统计与认领在本类完成；入站落库与内网创建委托 {@link AlertRecordWriter}，
 * 由后者按分片独立事务写入（避免超大批次拖长单事务），并在事务提交后异步派发通知。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRecordServiceImpl implements AlertRecordService {

    /** 告警级别字段 SQL 名 */
    private static final String COL_ALERT_LEVEL = "alert_level";

    /** 规则编码字段 SQL 名 */
    private static final String COL_RULE_CODE = "rule_code";

    /** 统计聚合的计数字段别名 */
    private static final String COL_COUNT = "cnt";

    private final AlertRecordMapper alertRecordMapper;

    private final AlertRecordWriter alertRecordWriter;

    private final AlertIngestProperties ingestProperties;

    private final MeterRegistry meterRegistry;

    @Override
    public PageResult<AlertRecordVO> page(AlertRecordQueryRequest request) {
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getRuleCode()), AlertRecord::getRuleCode, request.getRuleCode());
        wrapper.eq(StringUtils.hasText(request.getAlertLevel()), AlertRecord::getAlertLevel, request.getAlertLevel());
        wrapper.eq(StringUtils.hasText(request.getStatus()), AlertRecord::getStatus, request.getStatus());
        wrapper.eq(request.getAcknowledged() != null, AlertRecord::getAcknowledged,
                Boolean.TRUE.equals(request.getAcknowledged()) ? 1 : 0);
        wrapper.ge(request.getTriggerTimeStart() != null, AlertRecord::getTriggerTime,
                request.getTriggerTimeStart());
        wrapper.le(request.getTriggerTimeEnd() != null, AlertRecord::getTriggerTime,
                request.getTriggerTimeEnd());
        wrapper.like(StringUtils.hasText(request.getKeyword()), AlertRecord::getContent,
                request.getKeyword());
        String orderColumn = resolveSortColumn(request.getSortField());
        wrapper.last("ORDER BY " + orderColumn
                + ("asc".equalsIgnoreCase(request.getSortOrder()) ? " ASC" : " DESC"));
        Page<AlertRecord> page = alertRecordMapper.selectPage(request.toPage(), wrapper);
        List<AlertRecordVO> records = page.getRecords().stream().map(AlertRecordServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 解析排序字段（白名单，避免 SQL 注入）。
     *
     * @param sortField 请求排序字段
     * @return 数据库列名
     */
    private static String resolveSortColumn(String sortField) {
        if ("createTime".equals(sortField)) {
            return "create_time";
        }
        if ("recoverTime".equals(sortField)) {
            return "recover_time";
        }
        return "trigger_time";
    }

    @Override
    public int ingest(PrometheusWebhookPayload payload) {
        if (payload == null || payload.getAlerts() == null || payload.getAlerts().isEmpty()) {
            log.warn("Alertmanager 入站告警为空，忽略");
            return 0;
        }
        List<PrometheusWebhookPayload.PrometheusAlert> alerts = payload.getAlerts();
        if (alerts.size() > ingestProperties.getMaxAlerts()) {
            log.warn("入站告警条数超限: size={}, max={}", alerts.size(), ingestProperties.getMaxAlerts());
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int batchSize = Math.max(1, ingestProperties.getBatchSize());
        Timer.Sample sample = Timer.start(meterRegistry);
        int inserted = 0;
        for (int from = 0; from < alerts.size(); from += batchSize) {
            int to = Math.min(from + batchSize, alerts.size());
            inserted += alertRecordWriter.ingestChunk(alerts.subList(from, to));
        }
        sample.stop(Timer.builder("alert_ingest_duration").register(meterRegistry));
        log.info("Alertmanager 入站处理完成: received={}, inserted={}", alerts.size(), inserted);
        return inserted;
    }

    @Override
    public int create(AlertRecordCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getContent())) {
            log.warn("内网告警创建请求内容为空，忽略");
            return 0;
        }
        Long requestedTenant = request.getTenantId();
        if (requestedTenant != null && requestedTenant <= 0L) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        log.info("内网告警接入: source={}, tenantId={}, ruleCode={}",
                request.getSource(), requestedTenant, request.getRuleCode());
        return alertRecordWriter.createOne(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRecordVO ack(Long id, AlertAckRequest request) {
        AlertRecord existing = alertRecordMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (AlertRecordSupport.STATUS_RECOVERED.equals(existing.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        existing.setAcknowledged(1);
        existing.setAckUser(request == null ? null : request.getAckUser());
        existing.setAckNote(request == null ? null : request.getAckNote());
        existing.setAckTime(LocalDateTime.now());
        alertRecordMapper.updateById(existing);
        log.info("告警已认领: id={}, ruleCode={}, ackUser={}",
                id, existing.getRuleCode(), existing.getAckUser());
        return toVo(existing);
    }

    @Override
    public AlertRecordStatsVO stats(LocalDateTime start, LocalDateTime end) {
        AlertRecordStatsVO vo = new AlertRecordStatsVO();
        vo.setActiveCount(countByStatus(AlertRecordSupport.STATUS_FIRING, null, null));
        vo.setTotalCount(countInRange(start, end));
        vo.setRecoveredCount(countByStatus(AlertRecordSupport.STATUS_RECOVERED, start, end));
        vo.setSilencedCount(countByStatus(AlertRecordSupport.STATUS_SILENCED, start, end));
        vo.setByLevel(countGroupBy(COL_ALERT_LEVEL, start, end));
        vo.setTopRules(topRules(start, end));
        vo.setMttrSeconds(avgRecoverSeconds(start, end));
        return vo;
    }

    /**
     * 按状态统计记录数。
     *
     * @param status 状态
     * @param start  起始时间（可为 {@code null}）
     * @param end    截止时间（可为 {@code null}）
     * @return 记录数
     */
    private long countByStatus(String status, LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRecord::getStatus, status);
        applyRange(wrapper, start, end);
        Long count = alertRecordMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    /**
     * 统计时间范围内记录总数。
     *
     * @param start 起始时间
     * @param end   截止时间
     * @return 记录数
     */
    private long countInRange(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();
        applyRange(wrapper, start, end);
        Long count = alertRecordMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    /**
     * 按指定列分组统计。
     *
     * @param column 分组列
     * @param start  起始时间
     * @param end    截止时间
     * @return 分组计数（保持查询顺序）
     */
    private Map<String, Long> countGroupBy(String column, LocalDateTime start, LocalDateTime end) {
        QueryWrapper<AlertRecord> wrapper = new QueryWrapper<>();
        wrapper.select(column, "COUNT(*) AS " + COL_COUNT).groupBy(column);
        applyRange(wrapper, start, end);
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : alertRecordMapper.selectMaps(wrapper)) {
            Object key = row.get(column);
            result.put(key == null ? "UNKNOWN" : key.toString(), toLong(row.get(COL_COUNT)));
        }
        return result;
    }

    /**
     * 统计触发次数 TOP 10 规则。
     *
     * @param start 起始时间
     * @param end   截止时间
     * @return TOP 规则列表
     */
    private List<AlertRecordStatsVO.RuleCount> topRules(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<AlertRecord> wrapper = new QueryWrapper<>();
        wrapper.select(COL_RULE_CODE, "COUNT(*) AS " + COL_COUNT)
                .groupBy(COL_RULE_CODE)
                .orderByDesc(COL_COUNT)
                .last("LIMIT 10");
        applyRange(wrapper, start, end);
        return alertRecordMapper.selectMaps(wrapper).stream()
                .map(row -> new AlertRecordStatsVO.RuleCount(
                        row.get(COL_RULE_CODE) == null ? "UNKNOWN" : row.get(COL_RULE_CODE).toString(),
                        toLong(row.get(COL_COUNT))))
                .toList();
    }

    /**
     * 计算平均恢复时长（秒）。
     *
     * @param start 起始时间
     * @param end   截止时间
     * @return 平均秒数；无恢复记录时为 0
     */
    private double avgRecoverSeconds(LocalDateTime start, LocalDateTime end) {
        QueryWrapper<AlertRecord> wrapper = new QueryWrapper<>();
        wrapper.select("AVG(TIMESTAMPDIFF(SECOND, trigger_time, recover_time)) AS mttr")
                .eq("status", AlertRecordSupport.STATUS_RECOVERED)
                .isNotNull("recover_time");
        applyRange(wrapper, start, end);
        List<Map<String, Object>> rows = alertRecordMapper.selectMaps(wrapper);
        if (rows.isEmpty()) {
            return 0D;
        }
        Object value = rows.get(0).get("mttr");
        if (value instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    /**
     * 追加触发时间范围条件。
     *
     * @param wrapper 查询条件
     * @param start   起始时间
     * @param end     截止时间
     */
    private static void applyRange(LambdaQueryWrapper<AlertRecord> wrapper,
                                   LocalDateTime start, LocalDateTime end) {
        wrapper.ge(start != null, AlertRecord::getTriggerTime, start)
                .le(end != null, AlertRecord::getTriggerTime, end);
    }

    /**
     * 追加触发时间范围条件（聚合查询）。
     *
     * @param wrapper 查询条件
     * @param start   起始时间
     * @param end     截止时间
     */
    private static void applyRange(QueryWrapper<AlertRecord> wrapper,
                                   LocalDateTime start, LocalDateTime end) {
        wrapper.ge(start != null, "trigger_time", start)
                .le(end != null, "trigger_time", end);
    }

    /**
     * 计数结果转 long。
     *
     * @param value 计数值
     * @return 长整型计数
     */
    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * 记录实体转视图对象
     *
     * @param record 记录实体
     * @return 记录视图对象
     */
    private static AlertRecordVO toVo(AlertRecord record) {
        return AlertConverter.INSTANCE.toRecordVo(record);
    }
}
