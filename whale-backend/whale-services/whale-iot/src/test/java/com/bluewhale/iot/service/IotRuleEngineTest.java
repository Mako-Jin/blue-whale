package com.bluewhale.iot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.iot.config.IotProperties;
import com.bluewhale.iot.dto.RuleCondition;
import com.bluewhale.iot.dto.TelemetryPoint;
import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.entity.IotRule;
import com.bluewhale.iot.mapper.IotProductMapper;
import com.bluewhale.iot.mapper.IotRuleMapper;
import com.bluewhale.iot.support.AlertNotifyClient;
import com.bluewhale.iot.support.JsonCodec;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 规则引擎单测：命中判定、持续时长去抖、静默期与开关。
 *
 * @author BlueWhale
 */
class IotRuleEngineTest {

    private static final Long RULE_ID = 11L;

    private static final Long DEVICE_ID = 7L;

    private static final Long PRODUCT_ID = 3L;

    private IotRuleMapper ruleMapper;

    private IotProductMapper productMapper;

    private AlertNotifyClient alertNotifyClient;

    private IotProperties properties;

    private IotRuleEngine engine;

    /**
     * 初始化 MyBatis-Plus 表元数据，供 Lambda 更新条件解析列名。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), IotRule.class);
    }

    @BeforeEach
    void setUp() {
        ruleMapper = mock(IotRuleMapper.class);
        productMapper = mock(IotProductMapper.class);
        alertNotifyClient = mock(AlertNotifyClient.class);
        properties = new IotProperties();
        properties.setRuleEnabled(true);
        engine = new IotRuleEngine(ruleMapper, productMapper, alertNotifyClient, properties);
    }

    @Test
    void firesImmediatelyWhenThresholdCrossed() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(0, null)));
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product());
        when(alertNotifyClient.createRecord(anyString(), anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(true);

        int fired = engine.evaluate(device(), List.of(point("temperature", 85D)));

        assertEquals(1, fired);
        verify(alertNotifyClient).createRecord(eq("iot:rule:" + RULE_ID), eq("WARN"),
                contains("temperature"), anyString(), eq(1L), any(LocalDateTime.class));
        verify(ruleMapper).update(any(), any());
    }

    @Test
    void doesNotFireBelowThreshold() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(0, null)));

        int fired = engine.evaluate(device(), List.of(point("temperature", 60D)));

        assertEquals(0, fired);
        verify(alertNotifyClient, never()).createRecord(anyString(), anyString(), anyString(),
                anyString(), anyLong(), any());
    }

    @Test
    void waitsForDurationWindowBeforeFiring() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(60, null)));

        int first = engine.evaluate(device(), List.of(point("temperature", 85D)));
        int second = engine.evaluate(device(), List.of(point("temperature", 86D)));

        assertEquals(0, first);
        assertEquals(0, second);
        verify(alertNotifyClient, never()).createRecord(anyString(), anyString(), anyString(),
                anyString(), anyLong(), any());
    }

    @Test
    void skipsWhenStillInSilenceWindow() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(0, LocalDateTime.now())));

        int fired = engine.evaluate(device(), List.of(point("temperature", 85D)));

        assertEquals(0, fired);
        verify(alertNotifyClient, never()).createRecord(anyString(), anyString(), anyString(),
                anyString(), anyLong(), any());
    }

    @Test
    void ignoresUnrelatedIdentifier() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(0, null)));

        int fired = engine.evaluate(device(), List.of(point("humidity", 90D)));

        assertEquals(0, fired);
    }

    @Test
    void skipsEvaluationWhenEngineDisabled() {
        properties.setRuleEnabled(false);

        int fired = engine.evaluate(device(), List.of(point("temperature", 85D)));

        assertEquals(0, fired);
        verify(ruleMapper, never()).selectList(any());
    }

    @Test
    void keepsRuleSilentWhenAlertServiceRejects() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(0, null)));
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product());
        when(alertNotifyClient.createRecord(anyString(), anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(false);

        int fired = engine.evaluate(device(), List.of(point("temperature", 85D)));

        assertEquals(0, fired);
        verify(ruleMapper, never()).update(any(), any());
    }

    /**
     * 构造测试规则。
     *
     * @param durationSec   持续时长
     * @param lastFiredTime 最近触发时间（可空）
     * @return 规则实体
     */
    private static IotRule rule(int durationSec, LocalDateTime lastFiredTime) {
        RuleCondition condition = new RuleCondition();
        condition.setIdentifier("temperature");
        condition.setOp(">");
        condition.setValue(80D);
        condition.setDurationSec(durationSec);
        IotRule rule = new IotRule();
        rule.setId(RULE_ID);
        rule.setName("温度超限");
        rule.setProductId(PRODUCT_ID);
        rule.setConditionJson(JsonCodec.write(condition));
        rule.setActionConfig("{\"level\":\"WARN\",\"silenceSeconds\":300}");
        rule.setActionType("alert");
        rule.setEnabled(1);
        rule.setFiredCount(0L);
        rule.setLastFiredTime(lastFiredTime);
        return rule;
    }

    /**
     * 构造测试数据点。
     *
     * @param identifier 标识符
     * @param value      数值
     * @return 数据点
     */
    private static TelemetryPoint point(String identifier, Double value) {
        return TelemetryPoint.builder().identifier(identifier).value(value).ts(LocalDateTime.now()).build();
    }

    /**
     * 构造测试产品。
     *
     * @return 产品实体
     */
    private static IotProduct product() {
        IotProduct product = new IotProduct();
        product.setId(PRODUCT_ID);
        product.setCode("temp-sensor");
        product.setName("温度传感器");
        return product;
    }

    /**
     * 构造测试设备。
     *
     * @return 设备实体
     */
    private static IotDevice device() {
        IotDevice device = new IotDevice();
        device.setId(DEVICE_ID);
        device.setProductId(PRODUCT_ID);
        device.setDeviceName("sensor-001");
        device.setTenantId(1L);
        return device;
    }
}
