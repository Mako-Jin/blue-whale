package com.bluewhale.party.content.domain.model;

import lombok.Data;

/**
 * 党建内容管理列表查询条件（含未审稿）。
 *
 * @author BlueWhale
 */
@Data
public class ContentQuery {

    /** 关键字（标题 / 分类） */
    private String keyword;

    /** 分类 */
    private String category;

    /** 状态 */
    private String status;

    /** 可见范围 */
    private String visibility;

    /** 页码（从 1 起） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 10L;

    /**
     * 数据范围：非审核人只能看到「自己的稿件 + 全部已发布」。
     *
     * <p>由应用服务按操作人身份填充，接口参数不暴露该字段；
     * 为空表示审核人（可看全部状态）。</p>
     */
    private Long scopeAuthorId;
}
