package com.bluewhale.video.domain.model;

import lombok.Data;

/**
 * 电影片单分页查询条件（运营侧 / 用户侧共用）。
 *
 * @author BlueWhale
 */
@Data
public class CollectionQuery {

    /** 关键词（标题模糊匹配） */
    private String keyword;

    /** 发布状态筛选（用户侧由服务端强制为已上架） */
    private PublishState publishState;

    /** 当前页（从 1 开始） */
    private long current = 1L;

    /** 每页条数 */
    private long size = 20L;
}
