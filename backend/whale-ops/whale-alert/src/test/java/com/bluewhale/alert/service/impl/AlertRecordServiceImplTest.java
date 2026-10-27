package com.bluewhale.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.alert.config.AlertNotifyProperties;
import com.bluewhale.alert.dto.AlertRecordQueryRequest;
import com.bluewhale.alert.dto.PrometheusWebhookPayload;
import com.bluewhale.alert.entity.AlertRecord;
import com.bluewhale.alert.entity.AlertRule;
import com.bluewhale.alert.mapper.AlertRecordMapper;
import com.bluewhale.alert.mapper.AlertRuleMapper;
import com.bluewhale.alert.service.AlertNotifyService;
import com.bluewhale.alert.vo.AlertRecordVO;
import com.bluewhale.common.core.result.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertRecordServiceImpl} 分页查询、字段映射与 Alertmanager 入站单元测试。
 *
 * @author BlueWhale
 */
class AlertRecordServiceImplTest {

    private final AlertRecordMapper alertRecordMapper = mock(AlertRecordMapper.class);

    private final AlertRuleMapper alertRuleMapper = mock(AlertRuleMapper.class);

    private final AlertNotifyService alertNotifyService = mock(AlertNotifyService.class);

    private final AlertNotifyProperties notifyProperties = new AlertNotifyProperties();

    private final AlertRecordServiceImpl alertRecordService =
            new AlertRecordServiceImpl(alertRecordMapper, alertRuleMapper, alertNotifyService, notifyProperties);

    @Test
    @DisplayName("page：按条件调用分页查询并映射结果")
    void shouldPageWithConditions() {
        AlertRecord entity = new AlertRecord();
        entity.setId(1L);
        entity.setRuleCode("cpu_high");
        entity.setAlertLevel("WARN");
        entity.setStatus("FIRING");
        entity.setContent("CPU 使用率 92% 超过阈值 80%");
        Page<AlertRecord> page = new Page<>(1L, 10L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(alertRecordMapper.selectPage(ArgumentMatchers.<Page<AlertRecord>>any(),
                ArgumentMatchers.<Wrapper<AlertRecord>>any())).thenReturn(page);

        AlertRecordQueryRequest request = new AlertRecordQueryRequest();
        request.setRuleCode("cpu_high");
        request.setAlertLevel("WARN");
        PageResult<AlertRecordVO> result = alertRecordService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        AlertRecordVO vo = result.getRecords().get(0);
        assertEquals("cpu_high", vo.getRuleCode());
        assertEquals("WARN", vo.getAlertLevel());
        assertEquals("FIRING", vo.getStatus());
    }

    @Test
    @DisplayName("page：单页条数收敛到上限 200")
    void shouldClampPageSize() {
        Page<AlertRecord> page = new Page<>(1L, 200L);
        page.setRecords(List.of());
        page.setTotal(0L);
        when(alertRecordMapper.selectPage(ArgumentMatchers.<Page<AlertRecord>>any(),
                ArgumentMatchers.<Wrapper<AlertRecord>>any())).thenReturn(page);

        AlertRecordQueryRequest request = new AlertRecordQueryRequest();
        request.setSize(9999L);
        PageResult<AlertRecordVO> result = alertRecordService.page(request);

        assertEquals(200L, result.getSize());
    }

    @Test
    @DisplayName("ingest：firing 告警落库并按命中规则通知一次")
    void shouldIngestFiringAndNotify() {
        AlertRule rule = enabledRule("cpu_high", "EMAIL");
        when(alertRuleMapper.selectOne(any())).thenReturn(rule);
        PrometheusWebhookPayload payload = payload(alert("firing", "cpu_high", "critical"));

        int inserted = alertRecordService.ingest(payload);

        assertEquals(1, inserted);
        ArgumentCaptor<AlertRecord> captor = ArgumentCaptor.forClass(AlertRecord.class);
        verify(alertRecordMapper).insert(captor.capture());
        AlertRecord saved = captor.getValue();
        assertEquals("cpu_high", saved.getRuleCode());
        assertEquals(9L, saved.getRuleId());
        assertEquals("CRITICAL", saved.getAlertLevel());
        assertEquals("FIRING", saved.getStatus());
        assertEquals("CPU 使用率 92%", saved.getContent());
        verify(alertNotifyService).notify(rule, saved);
    }

    @Test
    @DisplayName("ingest：resolved 告警落库为已恢复且不通知")
    void shouldIngestResolvedWithoutNotify() {
        AlertRule rule = enabledRule("cpu_high", "EMAIL");
        when(alertRuleMapper.selectOne(any())).thenReturn(rule);
        PrometheusWebhookPayload payload = payload(alert("resolved", "cpu_high", "warning"));

        int inserted = alertRecordService.ingest(payload);

        assertEquals(1, inserted);
        ArgumentCaptor<AlertRecord> captor = ArgumentCaptor.forClass(AlertRecord.class);
        verify(alertRecordMapper).insert(captor.capture());
        AlertRecord saved = captor.getValue();
        assertEquals("RECOVERED", saved.getStatus());
        assertEquals("WARN", saved.getAlertLevel());
        assertNotNull(saved.getRecoverTime());
        verify(alertNotifyService, never()).notify(any(), any());
    }

    @Test
    @DisplayName("ingest：窗口内同 fingerprint 的 firing 告警去重，不落库不通知")
    void shouldSkipDuplicateFiringInWindow() {
        AlertRule rule = enabledRule("cpu_high", "EMAIL");
        when(alertRuleMapper.selectOne(any())).thenReturn(rule);
        when(alertRecordMapper.selectCount(ArgumentMatchers.<Wrapper<AlertRecord>>any())).thenReturn(1L);

        int inserted = alertRecordService.ingest(payload(alert("firing", "cpu_high", "critical")));

        assertEquals(0, inserted);
        verify(alertRecordMapper, never()).insert(ArgumentMatchers.<AlertRecord>any());
        verify(alertNotifyService, never()).notify(any(), any());
    }

    @Test
    @DisplayName("ingest：告警标签 tenant_id 决定落库与通知的租户上下文")
    void shouldUseTenantLabelAsContext() {
        AlertRule rule = enabledRule("cpu_high", "EMAIL");
        when(alertRuleMapper.selectOne(any())).thenReturn(rule);
        java.util.concurrent.atomic.AtomicLong notifiedTenant = new java.util.concurrent.atomic.AtomicLong();
        org.mockito.Mockito.doAnswer(invocation -> {
            notifiedTenant.set(com.bluewhale.common.tenant.TenantContext.get());
            return null;
        }).when(alertNotifyService).notify(any(), any());
        PrometheusWebhookPayload.PrometheusAlert alert = alert("firing", "cpu_high", "critical");
        alert.setLabels(Map.of("alertname", "cpu_high", "severity", "critical", "tenant_id", "7"));

        int inserted = alertRecordService.ingest(payload(alert));

        assertEquals(1, inserted);
        assertEquals(7L, notifiedTenant.get());
    }

    @Test
    @DisplayName("ingest：租户内无规则时回落到平台租户规则并保持通知")
    void shouldFallbackToPlatformRuleForTenantAlert() {
        AlertRule rule = enabledRule("cpu_high", "EMAIL");
        when(alertRuleMapper.selectOne(any())).thenReturn(null, rule);
        PrometheusWebhookPayload.PrometheusAlert alert = alert("firing", "cpu_high", "critical");
        alert.setLabels(Map.of("alertname", "cpu_high", "severity", "critical", "tenant_id", "7"));

        int inserted = alertRecordService.ingest(payload(alert));

        assertEquals(1, inserted);
        verify(alertRuleMapper, org.mockito.Mockito.times(2)).selectOne(any());
        verify(alertNotifyService).notify(org.mockito.ArgumentMatchers.eq(rule), any());
        assertNull(com.bluewhale.common.tenant.TenantContext.get());
    }

    @Test
    @DisplayName("ingest：缺少 alertname 标签时跳过")
    void shouldSkipAlertWithoutName() {
        PrometheusWebhookPayload.PrometheusAlert alert = alert("firing", null, "warning");

        int inserted = alertRecordService.ingest(payload(alert));

        assertEquals(0, inserted);
        verify(alertRecordMapper, never()).insert(ArgumentMatchers.<AlertRecord>any());
        verify(alertNotifyService, never()).notify(any(), any());
    }

    /**
     * 构造启用规则桩对象。
     *
     * @param ruleCode 规则编码
     * @param channel  通知通道
     * @return 规则实体
     */
    private static AlertRule enabledRule(String ruleCode, String channel) {
        AlertRule rule = new AlertRule();
        rule.setId(9L);
        rule.setRuleCode(ruleCode);
        rule.setRuleName("CPU 使用率过高");
        rule.setLevel("WARN");
        rule.setChannel(channel);
        rule.setStatus(1);
        return rule;
    }

    /**
     * 构造单条 Alertmanager 告警。
     *
     * @param status   告警状态
     * @param name     alertname 标签
     * @param severity severity 标签
     * @return 告警对象
     */
    private static PrometheusWebhookPayload.PrometheusAlert alert(String status, String name,
                                                                  String severity) {
        PrometheusWebhookPayload.PrometheusAlert alert = new PrometheusWebhookPayload.PrometheusAlert();
        alert.setStatus(status);
        alert.setLabels(name == null
                ? Map.of("severity", severity)
                : Map.of("alertname", name, "severity", severity));
        alert.setAnnotations(Map.of("summary", "CPU 使用率 92%"));
        alert.setStartsAt("2026-10-25T10:00:00+08:00");
        if ("resolved".equals(status)) {
            alert.setEndsAt("2026-10-25T10:05:00+08:00");
        }
        return alert;
    }

    /**
     * 构造 Alertmanager 请求体。
     *
     * @param alert 单条告警
     * @return 请求体
     */
    private static PrometheusWebhookPayload payload(PrometheusWebhookPayload.PrometheusAlert alert) {
        PrometheusWebhookPayload payload = new PrometheusWebhookPayload();
        payload.setVersion("4");
        payload.setStatus(alert.getStatus());
        payload.setAlerts(List.of(alert));
        return payload;
    }
}
