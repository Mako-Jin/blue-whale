package com.bluewhale.party.content.domain.model;

import lombok.Data;

/**
 * 党建园地阅读列表查询条件（仅已发布且当前用户可见）。
 *
 * @author BlueWhale
 */
@Data
public class ContentPortalQuery {

    /** 关键字（标题 / 分类） */
    private String keyword;

    /** 分类 */
    private String category;

    /** 页码（从 1 起） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 10L;
}
