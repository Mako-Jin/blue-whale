package com.bluewhale.erp.mfg.domain.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MRP 运行记录聚合。
 *
 * <p>不变量：RUNNING 无结束时间；FAILED 必带失败原因；统计数非负。</p>
 *
 * @author BlueWhale
 */
@Data
public class MrpRun {

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

    /** 输入需求行 */
    private List<MrpRunLine> lines = new ArrayList<>();
}
