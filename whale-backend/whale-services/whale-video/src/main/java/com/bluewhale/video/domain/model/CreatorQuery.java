package com.bluewhale.video.domain.model;

import lombok.Data;

/**
 * 创作者分页查询条件（运营侧）。
 *
 * @author BlueWhale
 */
@Data
public class CreatorQuery {

    /** 昵称关键词 */
    private String keyword;

    /** 状态筛选：1 启用 / 0 停用，{@code null} 表示全部 */
    private Integer status;

    /** 当前页（从 1 开始） */
    private long current = 1L;

    /** 每页条数 */
    private long size = 20L;
}
