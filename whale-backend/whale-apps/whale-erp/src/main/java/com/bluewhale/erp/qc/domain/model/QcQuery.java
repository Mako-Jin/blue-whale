package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 质量与设备模块分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QcQuery extends PageQuery {

    /** 关键字：单号 / 物料 / 设备 / 量具 */
    private String keyword;

    /** 状态 */
    private String status;

    /** 检验类型：IQC / IPQC / FQC */
    private String inspectionType;

    /** 来源类型 */
    private String sourceType;

    /** 物料编码 */
    private String itemCode;

    /** 批次号 */
    private String lotNo;

    /** 供应商编码 */
    private String supplierCode;

    /** 设备编码 */
    private String equipmentNo;

    /** 量具编号 */
    private String toolNo;

    /** 成本类型 */
    private String costType;

    /** 日期起（检验日期 / 统计日期 / 计划日期） */
    private LocalDate fromDate;

    /** 日期止 */
    private LocalDate toDate;
}
