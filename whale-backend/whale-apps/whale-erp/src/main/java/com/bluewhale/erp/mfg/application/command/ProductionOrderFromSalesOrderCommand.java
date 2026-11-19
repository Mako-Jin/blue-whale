package com.bluewhale.erp.mfg.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 销售订单行转生产订单命令。
 *
 * <p>产出物料与计划数量取自销售订单行，不在请求中重复传递；
 * 领料/完工仓库与计划日期由用户选择。</p>
 *
 * @author BlueWhale
 */
@Data
public class ProductionOrderFromSalesOrderCommand {

    /** 领料仓库 */
    @NotNull
    private Long issueWarehouseId;

    /** 完工入库仓库 */
    @NotNull
    private Long targetWarehouseId;

    /** 计划开工日期 */
    @NotNull
    private LocalDate planStartDate;

    /** 计划完工日期 */
    private LocalDate planEndDate;

    /** 备注 */
    private String remark;

    /** 用料 */
    @Valid
    @NotEmpty
    private List<ProductionOrderCreateCommand.Material> materials;
}
