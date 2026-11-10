package com.bluewhale.iot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.iot.config.IotProperties;
import com.bluewhale.iot.dto.RuleActionConfig;
import com.bluewhale.iot.dto.RuleCondition;
import com.bluewhale.iot.dto.TelemetryPoint;
import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.entity.IotRule;
import com.bluewhale.iot.mapper.IotProductMapper;
import com.bluewhale.iot.mapper.IotRuleMapper;
import com.bluewhale.iot.support.AlertNotifyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则引擎：遥测入库后评估启用规则，命中则派发告警动作。
 *
 * <p><b>评估时机</b>：{@link IotTelemetryService} 属性上报落库后同步调用（单批数据一次），
 * 引擎内部只做内存判断 + 命中时的异步无关副作用，脏数据/异常一律吞掉——规则是遥测链路的
 * 旁路，任何规则问题都不能阻断数据落库。</p>
 *
 * <p><b>去抖</b>：条件带 {@code durationSec} 时，需连续满足该时长才触发；计时状态存在
 * 进程内存（{@code ruleId:deviceId:identifier} → 首次满足时间），不落库——多实例部署时
 * 各实例独立计时，重复触发由静默期兜底。</p>
 *
 * <p><b>静默期</b>：命中后写 {@code last_fired_time}，同规则在静默期内不再重复告警，
 * 避免持续超限时刷屏。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IotRuleEngine {

    /** 规则启用标志 */
    private static final int RULE_ENABLED = 1;

    /** 静默期上限（秒），防止误配成「永不告警」 */
    private static final long MAX_SILENCE_SECONDS = 86400L;

    /** 浮点比较容差 */
    private static final double EPSILON = 1E-9;

    /** 默认告警级别 */
    private static final String DEFAULT_LEVEL = "WARN";

    private final IotRuleMapper ruleMapper;

    private final IotProductMapper productMapper;

    private final AlertNotifyClient alertNotifyClient;

    private final IotProperties properties;

    /** 连续满足计时（key = ruleId:deviceId:identifier，value = 首次满足的毫秒时间戳） */
    private final Map<String, Long> satisfiedSince = new ConcurrentHashMap<>();

    /**
     * 评估一批遥测数据点。
     *
     * @param device 上报设备
     * @param points 本批入库的数据点
     * @return 本次触发的规则条数
     */
    public int evaluate(IotDevice device, List<TelemetryPoint> points) {
        if (!properties.isRuleEnabled() || device == null || points == null || points.isEmpty()) {
            return 0;
        }
        List<IotRule> rules;
        try {
            rules = loadRules(device);
        } catch (RuntimeException e) {
            log.warn("规则加载失败（忽略本批评估）：deviceId={}, reason={}", device.getId(), e.getMessage());
            return 0;
        }
        if (rules.isEmpty()) {
            return 0;
        }
        int fired = 0;
        for (IotRule rule : rules) {
            RuleCondition condition = IotRuleService.readCondition(rule.getConditionJson());
            if (condition == null || !StringUtils.hasText(condition.getIdentifier())) {
                continue;
            }
            for (TelemetryPoint point : points) {
                if (!condition.getIdentifier().equals(point.getIdentifier())) {
                    continue;
                }
                Double value = numeric(point);
                if (value == null) {
                    continue;
                }
                try {
                    if (dispatch(rule, device, condition, value)) {
                        fired++;
                    }
                } catch (RuntimeException e) {
                    log.warn("规则触发失败（忽略）：ruleId={}, deviceId={}, reason={}",
                            rule.getId(), device.getId(), e.getMessage());
                }
            }
        }
        return fired;
    }

    /**
     * 查询作用于该设备的启用规则（设备级 / 产品级 / 全局）。
     *
     * @param device 设备
     * @return 规则列表
     */
    private List<IotRule> loadRules(IotDevice device) {
        LambdaQueryWrapper<IotRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IotRule::getEnabled, RULE_ENABLED);
        wrapper.and(w -> w.isNull(IotRule::getProductId)
                .or().eq(IotRule::getProductId, device.getProductId()));
        wrapper.and(w -> w.isNull(IotRule::getDeviceId)
                .or().eq(IotRule::getDeviceId, device.getId()));
        return ruleMapper.selectList(wrapper);
    }

    /**
     * 判断是否触发并按需派发动作。
     *
     * @param rule      命中的规则
     * @param device    设备
     * @param condition 触发条件
     * @param value     当前取值
     * @return true 本次触发
     */
    private boolean dispatch(IotRule rule, IotDevice device, RuleCondition condition, Double value) {
        String key = rule.getId() + ":" + device.getId() + ":" + condition.getIdentifier();
        long now = System.currentTimeMillis();
        boolean[] fire = {false};
        satisfiedSince.compute(key, (k, since) -> {
            if (!matches(condition, value)) {
                return null;
            }
            long first = since == null ? now : since;
            long durationMs = Math.max(0, durationSec(condition)) * 1000L;
            if (now - first < durationMs) {
                return first;
            }
            if (inSilence(rule, now)) {
                return first;
            }
            fire[0] = true;
            return null;
        });
        if (!fire[0]) {
            return false;
        }
        boolean accepted = alertNotifyClient.createRecord(
                "iot:rule:" + rule.getId(),
                resolveLevel(rule),
                buildContent(rule, device, condition, value),
                fingerprint(rule, device, condition),
                device.getTenantId(),
                LocalDateTime.now());
        if (!accepted) {
            return false;
        }
        rule.setLastFiredTime(LocalDateTime.now());
        markFired(rule);
        log.info("IoT 规则触发: ruleId={}, deviceId={}, identifier={}, value={}",
                rule.getId(), device.getId(), condition.getIdentifier(), value);
        return true;
    }

    /**
     * 判断取值是否满足条件。
     *
     * @param condition 条件
     * @param value     取值
     * @return 是否满足
     */
    private static boolean matches(RuleCondition condition, double value) {
        Double threshold = condition.getValue();
        if (threshold == null || !StringUtils.hasText(condition.getOp())) {
            return false;
        }
        double target = threshold;
        String op = condition.getOp().trim();
        return switch (op) {
            case ">" -> value > target;
            case ">=" -> value >= target;
            case "<" -> value < target;
            case "<=" -> value <= target;
            case "==" -> Math.abs(value - target) < EPSILON;
            case "!=" -> Math.abs(value - target) >= EPSILON;
            default -> false;
        };
    }

    /**
     * 判断是否仍在静默期。
     *
     * @param rule 规则
     * @param now  当前毫秒时间戳
     * @return true 静默中
     */
    private boolean inSilence(IotRule rule, long now) {
        LocalDateTime last = rule.getLastFiredTime();
        if (last == null) {
            return false;
        }
        long silence = silenceSeconds(rule);
        if (silence <= 0L) {
            return false;
        }
        long elapsed = Duration.between(last, LocalDateTime.now()).getSeconds();
        return elapsed < silence;
    }

    /**
     * 取规则静默期（动作配置优先，其次全局配置，钳制到上限）。
     *
     * @param rule 规则
     * @return 静默期秒数
     */
    private long silenceSeconds(IotRule rule) {
        RuleActionConfig config = IotRuleService.readActionConfig(rule.getActionConfig());
        Integer configured = config.getSilenceSeconds();
        long value = configured == null ? properties.getRuleSilenceSeconds() : configured;
        return Math.min(Math.max(value, 0L), MAX_SILENCE_SECONDS);
    }

    /**
     * 取条件的持续时长（秒）。
     *
     * @param condition 条件
     * @return 持续时长
     */
    private static int durationSec(RuleCondition condition) {
        return condition.getDurationSec() == null ? 0 : condition.getDurationSec();
    }

    /**
     * 取数据点的数值（文本型尝试解析，非数值返回 {@code null}）。
     *
     * @param point 数据点
     * @return 数值；无法比较时为 {@code null}
     */
    private static Double numeric(TelemetryPoint point) {
        if (point.getValue() != null) {
            return point.getValue();
        }
        if (!StringUtils.hasText(point.getValueText())) {
            return null;
        }
        try {
            return Double.valueOf(point.getValueText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 取告警级别。
     *
     * @param rule 规则
     * @return 归一化级别
     */
    private static String resolveLevel(IotRule rule) {
        RuleActionConfig config = IotRuleService.readActionConfig(rule.getActionConfig());
        String level = config.getLevel();
        return StringUtils.hasText(level) ? level.toUpperCase(Locale.ROOT) : DEFAULT_LEVEL;
    }

    /**
     * 构造告警内容（人可读的一句话，含规则/设备/取值/阈值）。
     *
     * @param rule      规则
     * @param device    设备
     * @param condition 条件
     * @param value     触发取值
     * @return 告警内容
     */
    private String buildContent(IotRule rule, IotDevice device, RuleCondition condition, double value) {
        String productCode = "";
        if (device.getProductId() != null) {
            IotProduct product = productMapper.selectById(device.getProductId());
            productCode = product == null ? "" : product.getCode();
        }
        return String.format(Locale.ROOT,
                "IoT 规则「%s」触发：设备 %s%s 属性 %s = %s %s %s（持续 %ds）",
                rule.getName(),
                productCode.isEmpty() ? "" : productCode + "/",
                device.getDeviceName(),
                condition.getIdentifier(),
                trimNumber(value),
                condition.getOp(),
                trimNumber(condition.getValue() == null ? 0D : condition.getValue()),
                durationSec(condition));
    }

    /**
     * 数值转紧凑文本（去掉多余小数位）。
     *
     * @param value 数值
     * @return 文本
     */
    private static String trimNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /**
     * 计算告警指纹（同规则同设备同属性恒定，供告警侧窗口去重）。
     *
     * @param rule      规则
     * @param device    设备
     * @param condition 条件
     * @return SHA-256 十六进制指纹（64 字符）
     */
    private static String fingerprint(IotRule rule, IotDevice device, RuleCondition condition) {
        String canonical = "iot|" + rule.getId() + "|" + device.getId() + "|" + condition.getIdentifier();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            log.warn("指纹计算失败（SHA-256 不可用），退回规则键文本");
            return canonical;
        }
    }

    /**
     * 记录触发（次数 +1、更新时间），失败只记日志。
     *
     * @param rule 规则
     */
    private void markFired(IotRule rule) {
        try {
            LambdaUpdateWrapper<IotRule> wrapper = new LambdaUpdateWrapper<IotRule>()
                    .eq(IotRule::getId, rule.getId())
                    .set(IotRule::getLastFiredTime, rule.getLastFiredTime())
                    .setSql("fired_count = fired_count + 1");
            ruleMapper.update(null, wrapper);
        } catch (RuntimeException e) {
            log.warn("规则触发计数更新失败：ruleId={}, reason={}", rule.getId(), e.getMessage());
        }
    }
}
