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
        @NotNull(message = "{validation.mes.item.seq-no-required}")
        @Min(value = 1, message = "{validation.mes.item.seq-no-min}")
        private Integer seqNo;

        /** 工序编码 */
        @NotBlank(message = "{validation.mes.item.operation-code-required}")
        @Size(max = 64, message = "{validation.mes.item.operation-code-size}")
        private String operationCode;

        /** 工序名称 */
        @Size(max = 128, message = "{validation.mes.item.operation-name-size}")
        private String operationName;

        /** 工作中心编码 */
        @Size(max = 64, message = "{validation.mes.item.work-center-code-size}")
        private String workCenterCode;

        /** 计划数量 */
        @NotNull(message = "{validation.mes.item.planned-quantity-required}")
        @DecimalMin(value = "0.0001", message = "{validation.mes.item.planned-quantity-min}")
        private BigDecimal plannedQuantity;

        /** 计划开工时间 */
        private LocalDateTime plannedStart;

        /** 计划完工时间 */
        private LocalDateTime plannedEnd;
    }
}
