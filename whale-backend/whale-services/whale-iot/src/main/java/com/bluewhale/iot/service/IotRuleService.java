package com.bluewhale.iot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.iot.config.IotProperties;
import com.bluewhale.iot.dto.RuleActionConfig;
import com.bluewhale.iot.dto.RuleCondition;
import com.bluewhale.iot.dto.RuleSaveRequest;
import com.bluewhale.iot.dto.RuleVO;
import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.entity.IotRule;
import com.bluewhale.iot.mapper.IotDeviceMapper;
import com.bluewhale.iot.mapper.IotProductMapper;
import com.bluewhale.iot.mapper.IotRuleMapper;
import com.bluewhale.iot.support.JsonCodec;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则服务：规则 CRUD、启停与作用域校验。
 *
 * <p>规则作用域三级：产品级（{@code productId} 非空、{@code deviceId} 空）→ 覆盖该产品
 * 全部设备；设备级（两者皆非空）→ 仅命中该设备；全局（两者皆空）→ 全部设备。
 * 条件与动作以 JSON 落库，读取时反序列化回结构化对象供前端编辑。</p>
 *
 * <p>规则评估不在此类：见 {@link IotRuleEngine}（由遥测链路调用）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IotRuleService {

    /** 动作类型：告警 */
    public static final String ACTION_ALERT = "alert";

    /** 启用标志 */
    private static final int ENABLED = 1;

    /** 停用标志 */
    private static final int DISABLED = 0;

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final IotRuleMapper ruleMapper;

    private final IotProductMapper productMapper;

    private final IotDeviceMapper deviceMapper;

    private final IotProperties properties;

    /**
     * 创建规则。
     *
     * @param request 保存请求
     * @return 规则视图
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleVO create(RuleSaveRequest request) {
        validateScope(request);
        IotRule rule = new IotRule();
        rule.setName(request.getName().trim());
        rule.setProductId(request.getProductId());
        rule.setDeviceId(request.getDeviceId());
        rule.setConditionJson(JsonCodec.write(request.getCondition()));
        rule.setActionType(actionType(request));
        rule.setActionConfig(JsonCodec.write(actionConfig(request)));
        rule.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? DISABLED : ENABLED);
        rule.setFiredCount(0L);
        ruleMapper.insert(rule);
        log.info("IoT 规则创建: id={}, name={}, productId={}, deviceId={}",
                rule.getId(), rule.getName(), rule.getProductId(), rule.getDeviceId());
        return toVo(rule);
    }

    /**
     * 更新规则（作用域字段置空表示放开到更大范围）。
     *
     * @param id      规则ID
     * @param request 保存请求
     * @return 规则视图
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleVO update(Long id, RuleSaveRequest request) {
        requireRule(id);
        validateScope(request);
        LambdaUpdateWrapper<IotRule> wrapper = new LambdaUpdateWrapper<IotRule>()
                .eq(IotRule::getId, id)
                .set(IotRule::getName, request.getName().trim())
                .set(IotRule::getProductId, request.getProductId())
                .set(IotRule::getDeviceId, request.getDeviceId())
                .set(IotRule::getConditionJson, JsonCodec.write(request.getCondition()))
                .set(IotRule::getActionType, actionType(request))
                .set(IotRule::getActionConfig, JsonCodec.write(actionConfig(request)))
                .set(IotRule::getEnabled, Boolean.FALSE.equals(request.getEnabled()) ? DISABLED : ENABLED)
                .set(IotRule::getUpdateTime, LocalDateTime.now());
        ruleMapper.update(null, wrapper);
        log.info("IoT 规则更新: id={}, name={}", id, request.getName());
        return toVo(requireRule(id));
    }

    /**
     * 规则分页。
     *
     * @param productId 产品过滤（可空）
     * @param keyword   名称模糊过滤（可空）
     * @param current   当前页
     * @param size      每页条数
     * @return 分页结果
     */
    public PageResult<RuleVO> page(Long productId, String keyword, long current, long size) {
        LambdaQueryWrapper<IotRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(productId != null, IotRule::getProductId, productId);
        wrapper.like(StringUtils.hasText(keyword), IotRule::getName, keyword);
        wrapper.orderByDesc(IotRule::getCreateTime);
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        Page<IotRule> page = ruleMapper.selectPage(new Page<>(Math.max(current, 1L), pageSize), wrapper);
        List<RuleVO> records = new ArrayList<>(page.getRecords().size());
        for (IotRule rule : page.getRecords()) {
            records.add(toVo(rule));
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 规则详情。
     *
     * @param id 规则ID
     * @return 规则视图
     */
    public RuleVO get(Long id) {
        return toVo(requireRule(id));
    }

    /**
     * 启用/停用规则。
     *
     * @param id      规则ID
     * @param enabled true 启用 / false 停用
     * @return 规则视图
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleVO setEnabled(Long id, boolean enabled) {
        requireRule(id);
        LambdaUpdateWrapper<IotRule> wrapper = new LambdaUpdateWrapper<IotRule>()
                .eq(IotRule::getId, id)
                .set(IotRule::getEnabled, enabled ? ENABLED : DISABLED)
                .set(IotRule::getUpdateTime, LocalDateTime.now());
        ruleMapper.update(null, wrapper);
        log.info("IoT 规则{}: id={}", enabled ? "启用" : "停用", id);
        return toVo(requireRule(id));
    }

    /**
     * 删除规则（逻辑删除）。
     *
     * @param id 规则ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireRule(id);
        ruleMapper.deleteById(id);
        log.info("IoT 规则删除: id={}", id);
    }

    /**
     * 查询规则实体（不存在抛业务异常）。
     *
     * @param id 规则ID
     * @return 规则实体
     */
    private IotRule requireRule(Long id) {
        IotRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "iot.rule.not-found", "规则不存在");
        }
        return rule;
    }

    /**
     * 校验规则作用域：产品/设备需存在，且设备必须属于指定产品。
     *
     * @param request 保存请求
     */
    private void validateScope(RuleSaveRequest request) {
        IotProduct product = null;
        if (request.getProductId() != null) {
            product = productMapper.selectById(request.getProductId());
            if (product == null) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "iot.product.not-found", "产品不存在");
            }
        }
        if (request.getDeviceId() == null) {
            return;
        }
        IotDevice device = deviceMapper.selectById(request.getDeviceId());
        if (device == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "iot.device.not-found", "设备不存在");
        }
        if (product != null && !product.getId().equals(device.getProductId())) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "iot.rule.device-product-mismatch", "设备不属于所选产品");
        }
    }

    /**
     * 归一化动作类型（缺省 alert）。
     *
     * @param request 保存请求
     * @return 动作类型
     */
    private static String actionType(RuleSaveRequest request) {
        return StringUtils.hasText(request.getActionType()) ? request.getActionType() : ACTION_ALERT;
    }

    /**
     * 归一化动作配置（缺省 WARN + 全局静默期）。
     *
     * @param request 保存请求
     * @return 动作配置
     */
    private RuleActionConfig actionConfig(RuleSaveRequest request) {
        RuleActionConfig config = request.getActionConfig();
        if (config == null) {
            config = new RuleActionConfig();
        }
        if (!StringUtils.hasText(config.getLevel())) {
            config.setLevel("WARN");
        }
        if (config.getSilenceSeconds() == null) {
            config.setSilenceSeconds(properties.getRuleSilenceSeconds());
        }
        return config;
    }

    /**
     * 实体转视图对象（附产品/设备名称）。
     *
     * @param rule 规则实体
     * @return 规则视图
     */
    private RuleVO toVo(IotRule rule) {
        RuleCondition condition = readCondition(rule.getConditionJson());
        RuleActionConfig config = readActionConfig(rule.getActionConfig());
        String productName = null;
        if (rule.getProductId() != null) {
            IotProduct product = productMapper.selectById(rule.getProductId());
            productName = product == null ? null : product.getName();
        }
        String deviceName = null;
        if (rule.getDeviceId() != null) {
            IotDevice device = deviceMapper.selectById(rule.getDeviceId());
            deviceName = device == null ? null : device.getDeviceName();
        }
        return RuleVO.builder()
                .id(String.valueOf(rule.getId()))
                .name(rule.getName())
                .productId(rule.getProductId() == null ? null : String.valueOf(rule.getProductId()))
                .productName(productName)
                .deviceId(rule.getDeviceId() == null ? null : String.valueOf(rule.getDeviceId()))
                .deviceName(deviceName)
                .condition(condition)
                .actionType(rule.getActionType())
                .actionConfig(config)
                .enabled(rule.getEnabled())
                .lastFiredTime(rule.getLastFiredTime() == null ? null : TIME_FORMAT.format(rule.getLastFiredTime()))
                .firedCount(rule.getFiredCount())
                .createTime(rule.getCreateTime() == null ? null : TIME_FORMAT.format(rule.getCreateTime()))
                .build();
    }

    /**
     * 反序列化条件 JSON（脏数据返回 {@code null}，不阻断列表渲染）。
     *
     * @param json 条件 JSON
     * @return 条件对象
     */
    static RuleCondition readCondition(String json) {
        JsonNode node = JsonCodec.tree(json);
        if (node == null || !node.isObject()) {
            return null;
        }
        RuleCondition condition = new RuleCondition();
        condition.setIdentifier(node.path("identifier").asText(null));
        condition.setOp(node.path("op").asText(null));
        condition.setValue(node.path("value").isNumber() ? node.path("value").asDouble() : null);
        condition.setDurationSec(node.path("durationSec").asInt(0));
        return condition;
    }

    /**
     * 反序列化动作配置 JSON。
     *
     * @param json 动作配置 JSON
     * @return 动作配置（缺省 WARN）
     */
    static RuleActionConfig readActionConfig(String json) {
        JsonNode node = JsonCodec.tree(json);
        RuleActionConfig config = new RuleActionConfig();
        if (node == null || !node.isObject()) {
            return config;
        }
        if (node.hasNonNull("level")) {
            config.setLevel(node.path("level").asText());
        }
        if (node.path("silenceSeconds").isNumber()) {
            config.setSilenceSeconds(node.path("silenceSeconds").asInt());
        }
        return config;
    }
}
