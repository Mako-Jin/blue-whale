package com.bluewhale.alert.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Prometheus Alertmanager Webhook 请求体（version 4 兼容子集）。
 *
 * <p>仅声明告警中心落库与通知所需字段，未声明字段由 Jackson 忽略。</p>
 *
 * @author BlueWhale
 */
@Data
public class PrometheusWebhookPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Alertmanager 版本（当前为 4） */
    private String version;

    /** 批次状态：firing / resolved */
    private String status;

    /** 告警列表 */
    private List<PrometheusAlert> alerts;

    /**
     * 单条告警。
     *
     * @author BlueWhale
     */
    @Data
    public static class PrometheusAlert implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 告警状态：firing / resolved */
        private String status;

        /** 标签集合，含 alertname / severity / application 等 */
        private Map<String, String> labels;

        /** 注解集合，含 summary / description */
        private Map<String, String> annotations;

        /** 开始时间（RFC3339） */
        private String startsAt;

        /** 结束时间（RFC3339，触发中为 null） */
        private String endsAt;

        /** 告警指纹 */
        private String fingerprint;
    }
}
