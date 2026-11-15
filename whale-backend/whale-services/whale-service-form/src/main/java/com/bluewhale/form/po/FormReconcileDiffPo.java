package com.bluewhale.form.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表单主从数据对账差异实体，对应表 {@code form_reconcile_diff}。
 *
 * <p>每条记录描述「某个对账批次下、某张物理表中、某条表单数据」在主表 {@code form_data}
 * 与物理表之间单向缺失的差异，供人工确认后修复。</p>
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/migration/V9__form_reconcile_diff.sql} 中的列逐字段对齐。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("form_reconcile_diff")
public class FormReconcileDiffPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID（行级隔离） */
    private Long tenantId;

    /** 对账批次号（{@code yyyyMMddHHmmss}） */
    private String batchNo;

    /** 物理表名 */
    private String physicalTable;

    /** 表单数据ID（{@code form_data.id}） */
    private Long dataId;

    /** 差异类型：1 主表有物理表无 / 2 物理表有主表无 */
    private Integer diffType;

    /** 状态：0 待处理 / 1 已修复 / 2 已忽略 */
    private Integer status;

    /** 差异说明 */
    private String detail;
}
