package com.bluewhale.crm.service.domain.model;

import lombok.Data;

/**
 * 知识引用查询条件。
 *
 * @author BlueWhale
 */
@Data
public class KbRefQuery {

    /** 当前页 */
    private Integer current = 1;

    /** 每页条数 */
    private Integer size = 10;

    /** 关键字（标题 / 产品标签 / 故障标签） */
    private String keyword;

    /** 分类 */
    private String category;

    /** 可见范围 */
    private String visibleScope;

    /** 状态 */
    private String status;
}
