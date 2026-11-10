package com.bluewhale.twin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.twin.dto.BindingAlertVO;
import com.bluewhale.twin.dto.TwinAlertVO;
import com.bluewhale.twin.entity.TwinAlert;
import com.bluewhale.twin.mapper.TwinAlertMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 孪生阈值告警服务（Wave 6.06 M1）：告警落库与查询。
 *
 * <p>落库口径（与 SSE {@code alert} 帧同源）：非 normal 级别插入新行
 * （{@code resolved=0}；同绑定同状态键的旧未恢复行标为已被新级别替代）；
 * 恢复到 normal 时回填未恢复行的恢复时间，不插恢复行——恢复点体现为
 * {@code resolveTime}。同绑定同状态键的未恢复行至多一条。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwinAlertService {

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** JSON 解析器（thresholds 序列化/反序列化） */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 级别常量（与评估器一致） */
    private static final String LEVEL_NORMAL = "normal";

    private final TwinAlertMapper alertMapper;

    /**
     * 记录告警（级别变化时由运行时调用）。
     *
     * <p>normal 视为恢复信号：回填未恢复行；其余级别插入新行。</p>
     *
     * @param sceneId  场景ID
     * @param tenantId 租户ID
     * @param alert    告警内容（SSE 帧条目）
     */
    public void record(Long sceneId, Long tenantId, BindingAlertVO alert) {
        boolean recovery = LEVEL_NORMAL.equals(alert.getLevel());
        if (recovery) {
            resolveOpenAlert(sceneId, alert);
            return;
        }
        // 旧未恢复行被新级别替代（如 warn → critical），收口为已恢复
        resolveOpenAlert(sceneId, alert);
        TwinAlert entity = new TwinAlert();
        entity.setTenantId(tenantId);
        entity.setSceneId(sceneId);
        entity.setEntityId(alert.getEntityId() == null ? null : Long.valueOf(alert.getEntityId()));
        entity.setEntityCode(alert.getEntityCode());
        entity.setBindingId(Long.valueOf(alert.getBindingId()));
        entity.setStateKey(alert.getStateKey());
        entity.setAlertValue(alert.getValue() == null ? null : String.valueOf(alert.getValue()));
        entity.setPreviousLevel(alert.getPrevious());
        entity.setLevel(alert.getLevel());
        entity.setThresholdsJson(toJson(alert.getThresholds()));
        entity.setResolved(0);
        alertMapper.insert(entity);
    }

    /**
     * 场景告警历史（按触发时间倒序）。
     *
     * @param sceneId  场景ID
     * @param level    级别过滤（可空 = 全部）
     * @param resolved 恢复状态过滤（可空 = 全部）
     * @param acked    确认状态过滤（可空 = 全部）
     * @param limit    返回条数上限（空则默认 50，最大 200）
     * @return 告警视图列表
     */
    public List<TwinAlertVO> list(Long sceneId, String level, Boolean resolved,
                                  Boolean acked, Integer limit) {
        int capped = limit == null ? 50 : Math.min(200, Math.max(1, limit));
        LambdaQueryWrapper<TwinAlert> query = new LambdaQueryWrapper<TwinAlert>()
                .eq(TwinAlert::getSceneId, sceneId)
                .orderByDesc(TwinAlert::getCreateTime)
                .orderByDesc(TwinAlert::getId)
                .last("LIMIT " + capped);
        if (StringUtils.hasText(level)) {
            query.eq(TwinAlert::getLevel, level);
        }
        if (resolved != null) {
            query.eq(TwinAlert::getResolved, resolved ? 1 : 0);
        }
        if (acked != null) {
            query.eq(TwinAlert::getAck, acked ? 1 : 0);
        }
        List<TwinAlert> rows = alertMapper.selectList(query);
        List<TwinAlertVO> result = new ArrayList<>(rows.size());
        for (TwinAlert row : rows) {
            result.add(toVo(row));
        }
        return result;
    }

    /**
     * 确认告警（人工知悉；重复确认与告警不存在均报业务异常）。
     *
     * @param sceneId 场景ID（防跨场景确认）
     * @param alertId 告警ID
     * @param userId  确认人用户ID
     * @return 更新后的告警视图
     */
    public TwinAlertVO ack(Long sceneId, Long alertId, Long userId) {
        TwinAlert row = alertMapper.selectOne(new LambdaQueryWrapper<TwinAlert>()
                .eq(TwinAlert::getSceneId, sceneId)
                .eq(TwinAlert::getId, alertId)
                .last("LIMIT 1"));
        if (row == null) {
            throw new BizException("告警不存在");
        }
        if (row.getAck() != null && row.getAck() == 1) {
            throw new BizException("该告警已确认");
        }
        LocalDateTime now = LocalDateTime.now();
        alertMapper.update(null, new LambdaUpdateWrapper<TwinAlert>()
                .eq(TwinAlert::getId, alertId)
                .set(TwinAlert::getAck, 1)
                .set(TwinAlert::getAckTime, now)
                .set(TwinAlert::getAckBy, userId));
        row.setAck(1);
        row.setAckTime(now);
        row.setAckBy(userId);
        return toVo(row);
    }

    /**
     * 场景未恢复告警列表（按触发时间倒序）。
     *
     * @param sceneId 场景ID
     * @return 告警视图列表
     */
    public List<TwinAlertVO> listUnresolved(Long sceneId) {
        List<TwinAlert> rows = alertMapper.selectList(new LambdaQueryWrapper<TwinAlert>()
                .eq(TwinAlert::getSceneId, sceneId)
                .eq(TwinAlert::getResolved, 0)
                .orderByDesc(TwinAlert::getCreateTime)
                .orderByDesc(TwinAlert::getId));
        List<TwinAlertVO> result = new ArrayList<>(rows.size());
        for (TwinAlert row : rows) {
            result.add(toVo(row));
        }
        return result;
    }

    /**
     * 收口同场景、同绑定、同状态键的未恢复行（恢复或被新级别替代）。
     *
     * @param sceneId 场景ID
     * @param alert   告警内容（定位 binding + stateKey）
     */
    private void resolveOpenAlert(Long sceneId, BindingAlertVO alert) {
        try {
            int updated = alertMapper.update(null, new LambdaUpdateWrapper<TwinAlert>()
                    .eq(TwinAlert::getSceneId, sceneId)
                    .eq(TwinAlert::getBindingId, Long.valueOf(alert.getBindingId()))
                    .eq(TwinAlert::getStateKey, alert.getStateKey())
                    .eq(TwinAlert::getResolved, 0)
                    .set(TwinAlert::getResolved, 1)
                    .set(TwinAlert::getResolveTime, LocalDateTime.now()));
            if (updated > 0 && log.isDebugEnabled()) {
                log.debug("告警已收口：sceneId={}, bindingId={}, stateKey={}, rows={}",
                        sceneId, alert.getBindingId(), alert.getStateKey(), updated);
            }
        } catch (RuntimeException e) {
            // 落库失败不阻塞 SSE 主链路（SSE 帧已发出），下个级别变化再补
            log.warn("告警落库失败（忽略）：sceneId={}, bindingId={}, reason={}",
                    sceneId, alert.getBindingId(), e.getMessage());
        }
    }

    /**
     * 阈值列表序列化为 JSON 数组字符串。
     *
     * @param thresholds 阈值（可空）
     * @return JSON 字符串；序列化失败返回 {@code null}
     */
    private String toJson(List<BigDecimal> thresholds) {
        if (thresholds == null || thresholds.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(thresholds);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 阈值 JSON 数组字符串反序列化。
     *
     * @param json JSON 字符串（可空）
     * @return 阈值列表；解析失败或为空返回空列表
     */
    private List<BigDecimal> fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = JSON.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<BigDecimal> thresholds = new ArrayList<>(node.size());
            node.forEach(item -> thresholds.add(new BigDecimal(item.asText())));
            return List.copyOf(thresholds);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 实体转视图。
     *
     * @param row 告警实体
     * @return 视图对象
     */
    private TwinAlertVO toVo(TwinAlert row) {
        return TwinAlertVO.builder()
                .id(String.valueOf(row.getId()))
                .entityId(row.getEntityId() == null ? null : String.valueOf(row.getEntityId()))
                .entityCode(row.getEntityCode())
                .bindingId(String.valueOf(row.getBindingId()))
                .stateKey(row.getStateKey())
                .value(row.getAlertValue())
                .previousLevel(row.getPreviousLevel())
                .level(row.getLevel())
                .thresholds(fromJson(row.getThresholdsJson()))
                .resolved(row.getResolved() != null && row.getResolved() == 1)
                .resolveTime(row.getResolveTime() == null ? null : TIME_FORMAT.format(row.getResolveTime()))
                .createTime(row.getCreateTime() == null ? null : TIME_FORMAT.format(row.getCreateTime()))
                .acked(row.getAck() != null && row.getAck() == 1)
                .ackTime(row.getAckTime() == null ? null : TIME_FORMAT.format(row.getAckTime()))
                .ackBy(row.getAckBy() == null ? null : String.valueOf(row.getAckBy()))
                .build();
    }
}
