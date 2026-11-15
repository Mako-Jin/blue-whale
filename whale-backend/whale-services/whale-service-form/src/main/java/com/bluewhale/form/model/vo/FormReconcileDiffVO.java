package com.bluewhale.form.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表单主从数据对账差异视图对象（字段名为冻结的 JSON 契约，不得改名）。
 *
 * @author BlueWhale
 */
@Data
public class FormReconcileDiffVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 对账批次号（{@code yyyyMMddHHmmss}） */
    private String batchNo;

    /** 物理表名 */
    private String physicalTable;

    /** 表单数据ID */
    private Long dataId;

    /** 差异类型：1 主表有物理表无 / 2 物理表有主表无 */
    private Integer diffType;

    /** 状态：0 待处理 / 1 已修复 / 2 已忽略 */
    private Integer status;

    /** 差异说明 */
    private String detail;

    /** 创建时间 */
    private LocalDateTime createTime;
}
