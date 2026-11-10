package com.bluewhale.doc.interfaces.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 空间分页查询参数。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DocSpaceQueryRequest extends PageQuery {

    /** 名称/描述关键字 */
    private String keyword;

    /** 范围：all 全部可见 / mine 我参与的（默认 all） */
    private String scope = "all";

    /** 可见性过滤：PUBLIC / MEMBER */
    private String visibility;
}
