package com.bluewhale.erp.mfg.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * MRP 运行记录实体，对应表 {@code erp_mfg_mrp_run}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_mfg_mrp_run")
public class MrpRunEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 运行单号 */
    private String runNo;

    /** 状态 */
    private String status;

    /** 需求输入行数 */
    private Integer demandLineCount;

    /** BOM 快照总行数 */
    private Integer snapshotLineCount;

    /** 计划订单数 */
    private Integer plannedOrderCount;

    /** 失败原因 */
    private String errorMessage;

    /** 备注 */
    private String remark;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}
