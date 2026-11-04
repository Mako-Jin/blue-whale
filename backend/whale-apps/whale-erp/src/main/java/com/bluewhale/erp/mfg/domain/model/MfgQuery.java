package com.bluewhale.erp.mfg.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 生产计划分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MfgQuery extends PageQuery {

    /** 关键字：单号 / 物料编码 / 物料名称 */
    private String keyword;

    /** 单据状态 */
    private String status;

    /** 计划期间（YYYY-MM） */
    private String period;

    /** 物料主键 */
    private Long itemId;

    /** 领料或入库仓库主键 */
    private Long warehouseId;

    /** 计划开工日期起 */
    private LocalDate fromDate;

    /** 计划开工日期止 */
    private LocalDate toDate;
}
