package com.bluewhale.mes.collect.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 网关样本批量上报请求（内网接口按采集点编码定位）
 *
 * @author BlueWhale
 */
@Data
public class MesMetricIngestRequest {

    /** 样本列表 */
    @Valid
    @NotEmpty(message = "{validation.mes.mes-metric-ingest.samples-required}")
    private List<SampleItem> samples;

    /**
     * 单条样本
     */
    @Data
    public static class SampleItem {

        /** 指标值 */
        @NotNull(message = "{validation.mes.sample-item.metric-value-required}")
        @DecimalMin(value = "0.0000", message = "{validation.mes.sample-item.metric-value-min}")
        private BigDecimal metricValue;

        /** 采样时间 */
        @NotNull(message = "{validation.mes.sample-item.sampled-at-required}")
        private LocalDateTime sampledAt;

        /** 同采样时间的序号（幂等键组成，默认 1） */
        private Integer seq;

        /** 网关事件幂等键 */
        private String sourceEventId;

        /** 原始报文 */
        private String payload;
    }
}
