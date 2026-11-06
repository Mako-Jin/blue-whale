package com.bluewhale.erp.mfg.application.view;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MRP 运行记录视图。
 *
 * @author BlueWhale
 */
@Data
public class MrpRunView {

    /** 主键 */
    private Long id;

    /** 运行单号 */
    private String runNo;

    /** 状态：RUNNING / COMPLETED / FAILED */
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

    /** 输入需求行（仅详情返回） */
    private List<MrpRunLineView> lines;

    /** 计划订单概览（仅详情返回） */
    private List<MrpPlannedOrderView> orders;
}
