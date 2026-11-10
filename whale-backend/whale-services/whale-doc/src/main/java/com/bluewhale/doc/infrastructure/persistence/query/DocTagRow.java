package com.bluewhale.doc.infrastructure.persistence.query;

import lombok.Data;

/**
 * 标签及页面数量行。
 *
 * @author BlueWhale
 */
@Data
public class DocTagRow {

    /** 标签ID */
    private Long tagId;

    /** 标签名称 */
    private String name;

    /** 标签颜色 */
    private String color;

    /** 可读页面数量 */
    private Long pageCount;
}
