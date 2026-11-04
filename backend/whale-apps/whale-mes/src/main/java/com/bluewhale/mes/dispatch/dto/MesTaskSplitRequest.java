package com.bluewhale.mes.dispatch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单拆分请求。
 *
 * <p>{@code items} 为空时按工单物料对应的已发布工艺路线自动拆分；工序顺序在同一工单内唯一，
 * 每个工序的计划数量之和必须等于工单计划数量。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesTaskSplitRequest {

    /** 自定义拆分项，为空时按工艺路线自动拆分 */
    @Valid
    private List<Item> items;

    /**
     * 拆分项
     */
    @Data
    public static class Item {

        /** 工序顺序 */
        @NotNull(message = "工序顺序不能为空")
        @Min(value = 1, message = "工序顺序必须大于 0")
        private Integer seqNo;

        /** 工序编码 */
        @NotBlank(message = "工序编码不能为空")
        @Size(max = 64, message = "工序编码长度不能超过 64")
        private String operationCode;

        /** 工序名称 */
        @Size(max = 128, message = "工序名称长度不能超过 128")
        private String operationName;

        /** 工作中心编码 */
        @Size(max = 64, message = "工作中心编码长度不能超过 64")
        private String workCenterCode;

        /** 计划数量 */
        @NotNull(message = "计划数量不能为空")
        @DecimalMin(value = "0.0001", message = "计划数量必须大于 0")
        private BigDecimal plannedQuantity;

        /** 计划开工时间 */
        private LocalDateTime plannedStart;

        /** 计划完工时间 */
        private LocalDateTime plannedEnd;
    }
}
