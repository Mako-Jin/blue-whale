package com.bluewhale.erp.masterdata.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 主数据分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MasterDataQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 关键字（编码或名称） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 科目类型（仅科目查询使用） */
    private String accountType;
}
