package com.bluewhale.erp.scm.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 供应链单据分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmQuery extends PageQuery {

    /** 关键字：单号 / 物料编码 / 物料名称 */
    private String keyword;

    /** 单据状态 */
    private String status;

    /** 往来单位（客户或供应商）主键 */
    private Long partyId;

    /** 仓库主键 */
    private Long warehouseId;

    /** 物料主键（库存查询用） */
    private Long itemId;

    /** 单据日期起 */
    private LocalDate fromDate;

    /** 单据日期止 */
    private LocalDate toDate;
}
