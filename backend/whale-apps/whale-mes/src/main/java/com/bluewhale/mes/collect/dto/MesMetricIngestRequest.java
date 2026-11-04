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
    @NotEmpty(message = "样本列表不能为空")
    private List<SampleItem> samples;

    /**
     * 单条样本
     */
    @Data
    public static class SampleItem {

        /** 指标值 */
        @NotNull(message = "指标值不能为空")
        @DecimalMin(value = "0.0000", message = "指标值不能为负")
        private BigDecimal metricValue;

        /** 采样时间 */
        @NotNull(message = "采样时间不能为空")
        private LocalDateTime sampledAt;

        /** 同采样时间的序号（幂等键组成，默认 1） */
        private Integer seq;

        /** 网关事件幂等键 */
        private String sourceEventId;

        /** 原始报文 */
        private String payload;
    }
}
