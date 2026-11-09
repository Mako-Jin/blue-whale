package com.bluewhale.iot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.iot.dto.ShadowVO;
import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.mapper.IotDeviceMapper;
import com.bluewhale.iot.mapper.IotProductMapper;
import com.bluewhale.iot.support.EmqxPublishClient;
import com.bluewhale.iot.support.JsonCodec;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 设备影子服务：最新上报快照（reported）与平台期望值（desired）的读写与合并。
 *
 * <p>影子结构存 {@code iot_device.shadow_json}：
 * {@code {"reported":{...},"desired":{...},"version":n,"reportedTime":"...","updatedTime":"..."}}。
 * 版本号每次变更 +1，供设备侧判定新旧。</p>
 *
 * <p><b>合并口径</b>：设备上报命中期望值（键存在且取值相等）时从 desired 移除——
 * 期望值只在「未达成」时存在，避免 desired 永久堆积；设备上线时若仍有未达成期望，
 * 经 EMQX REST 发布 {@code down/shadow} 补推一次（设备随后按属性上报回执）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IotShadowService {

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 影子段：上报快照 */
    private static final String SEGMENT_REPORTED = "reported";

    /** 影子段：期望值 */
    private static final String SEGMENT_DESIRED = "desired";

    /** 下线主题模板前缀 */
    private static final String TOPIC_PREFIX = "bw/";

    private final IotDeviceMapper deviceMapper;

    private final IotProductMapper productMapper;

    private final EmqxPublishClient publishClient;

    /**
     * 读取设备影子（设备不存在抛业务异常）。
     *
     * @param deviceId 设备ID
     * @return 影子视图
     */
    public ShadowVO get(Long deviceId) {
        IotDevice device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BizException("设备不存在");
        }
        return toVo(device, read(device));
    }

    /**
     * 按三元组定位读取设备影子（内网只读接口，供数字孪生/AI 助手实时态查询）。
     *
     * @param productCode 产品编码
     * @param deviceName  设备名
     * @return 影子视图
     */
    public ShadowVO getByCode(String productCode, String deviceName) {
        IotProduct product = productMapper.selectOne(new LambdaQueryWrapper<IotProduct>()
                .eq(IotProduct::getCode, productCode)
                .last("LIMIT 1"));
        if (product == null) {
            throw new BizException("产品不存在");
        }
        IotDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<IotDevice>()
                .eq(IotDevice::getProductId, product.getId())
                .eq(IotDevice::getDeviceName, deviceName)
                .last("LIMIT 1"));
        if (device == null) {
            throw new BizException("设备不存在");
        }
        return toVo(device, read(device));
    }

    /**
     * 解析影子状态（非法/空 JSON 视为空影子）。
     *
     * @param device 设备实体
     * @return 影子状态
     */
    public ShadowState read(IotDevice device) {
        ShadowState state = new ShadowState();
        JsonNode root = JsonCodec.tree(device.getShadowJson());
        if (root == null) {
            return state;
        }
        JsonNode reported = root.path(SEGMENT_REPORTED);
        if (reported.isObject()) {
            reported.fields().forEachRemaining(entry ->
                    state.getReported().put(entry.getKey(), JsonCodec.toValue(entry.getValue())));
        }
        JsonNode desired = root.path(SEGMENT_DESIRED);
        if (desired.isObject()) {
            desired.fields().forEachRemaining(entry ->
                    state.getDesired().put(entry.getKey(), JsonCodec.toValue(entry.getValue())));
        }
        state.setVersion(root.path("version").asLong(0L));
        state.setReportedTime(parseTime(root.path("reportedTime").asText(null)));
        state.setUpdatedTime(parseTime(root.path("updatedTime").asText(null)));
        return state;
    }

    /**
     * 合并设备上报值：写入 reported、清理已达成的 desired、版本 +1 并落库。
     *
     * @param device 设备实体
     * @param values 上报值（属性标识符 → 取值）
     * @param ts     上报时间（空取当前时间）
     * @return 合并后的影子状态
     */
    public ShadowState applyReported(IotDevice device, Map<String, Object> values, LocalDateTime ts) {
        ShadowState state = read(device);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reportTime = ts == null ? now : ts;
        state.getReported().putAll(values);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object desired = state.getDesired().get(entry.getKey());
            if (desired != null && Objects.equals(String.valueOf(desired), String.valueOf(entry.getValue()))) {
                state.getDesired().remove(entry.getKey());
            }
        }
        state.setVersion(state.getVersion() + 1);
        state.setReportedTime(reportTime);
        state.setUpdatedTime(now);
        persist(device, state);
        return state;
    }

    /**
     * 写入期望值（指令下发路径调用）：合并进 desired、版本 +1 并落库。
     *
     * @param device 设备实体
     * @param values 期望值（属性标识符 → 取值）
     * @return 合并后的影子状态
     */
    public ShadowState markDesired(IotDevice device, Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return read(device);
        }
        ShadowState state = read(device);
        state.getDesired().putAll(values);
        state.setVersion(state.getVersion() + 1);
        state.setUpdatedTime(LocalDateTime.now());
        persist(device, state);
        return state;
    }

    /**
     * 设备上线时补推未达成的期望值（{@code down/shadow} 主题）。
     *
     * @param device 设备实体
     * @return true 已投递；false 无期望值或投递失败
     */
    public boolean syncDesiredOnConnect(IotDevice device) {
        ShadowState state = read(device);
        if (state.getDesired().isEmpty()) {
            return false;
        }
        IotProduct product = productMapper.selectOne(new LambdaQueryWrapper<IotProduct>()
                .eq(IotProduct::getId, device.getProductId())
                .last("LIMIT 1"));
        if (product == null) {
            log.warn("影子同步跳过：产品不存在，deviceId={}", device.getId());
            return false;
        }
        String topic = TOPIC_PREFIX + device.getTenantId() + "/" + product.getCode()
                + "/" + device.getDeviceName() + "/down/shadow";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", state.getVersion());
        payload.put("desired", state.getDesired());
        boolean ok = publishClient.publish(topic, JsonCodec.write(payload));
        if (ok) {
            log.info("设备上线补推期望值：deviceId={}, version={}, items={}",
                    device.getId(), state.getVersion(), state.getDesired().size());
        }
        return ok;
    }

    /**
     * 影子状态转视图对象。
     *
     * @param device 设备实体
     * @param state  影子状态
     * @return 视图对象
     */
    public ShadowVO toVo(IotDevice device, ShadowState state) {
        return ShadowVO.builder()
                .deviceId(String.valueOf(device.getId()))
                .deviceName(device.getDeviceName())
                .online(device.getOnline())
                .reported(state.getReported())
                .desired(state.getDesired())
                .version(state.getVersion())
                .reportedTime(format(state.getReportedTime()))
                .updatedTime(format(state.getUpdatedTime()))
                .build();
    }

    /**
     * 落库影子 JSON。
     *
     * @param device 设备实体
     * @param state  影子状态
     */
    private void persist(IotDevice device, ShadowState state) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put(SEGMENT_REPORTED, state.getReported());
        json.put(SEGMENT_DESIRED, state.getDesired());
        json.put("version", state.getVersion());
        json.put("reportedTime", format(state.getReportedTime()));
        json.put("updatedTime", format(state.getUpdatedTime()));
        device.setShadowJson(JsonCodec.write(json));
        deviceMapper.updateById(device);
    }

    /**
     * 解析时间字段。
     *
     * @param text ISO-8601 文本
     * @return 时间；空或非法返回 {@code null}
     */
    private static LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, TIME_FORMAT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 格式化时间。
     *
     * @param time 时间
     * @return ISO-8601 文本；空值返回 {@code null}
     */
    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }

    /**
     * 影子状态（reported + desired + 版本与时间）。
     *
     * @author BlueWhale
     */
    @Data
    public static class ShadowState {

        /** 最新上报快照 */
        private final Map<String, Object> reported = new LinkedHashMap<>();

        /** 期望值 */
        private final Map<String, Object> desired = new LinkedHashMap<>();

        /** 版本号（每次变更 +1） */
        private long version;

        /** 最近上报时间 */
        private LocalDateTime reportedTime;

        /** 最近更新时间 */
        private LocalDateTime updatedTime;
    }
}
