package com.bluewhale.oa.document.domain.model;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公文分页查询。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DocQuery extends PageQuery {

    /** 关键字（文号/标题/来文单位） */
    private String keyword;

    /** 状态 */
    private String status;

    /** 单据类型/文种筛选 */
    private String docType;

    /** 密级 */
    private Integer securityLevel;
}
