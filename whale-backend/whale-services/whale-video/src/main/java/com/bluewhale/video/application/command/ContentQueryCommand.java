package com.bluewhale.video.application.command;

import lombok.Data;

/**
 * 内容分页查询命令（用户侧 / 我的作品 / 运营列表共用）。
 *
 * @author BlueWhale
 */
@Data
public class ContentQueryCommand {

    /** 内容类型 */
    private String contentType;

    /** 分类 ID */
    private Long categoryId;

    /** 标签 ID */
    private Long tagId;

    /** 上传者 ID（运营按人查询） */
    private Long uploaderId;

    /** 创作者 ID */
    private Long creatorId;

    /** 系列 ID */
    private Long seriesId;

    /** 发布状态（运营可用） */
    private String publishState;

    /** 可见性（运营可用） */
    private String visibility;

    /** 关键词 */
    private String keyword;

    /** 排序：LATEST / HOT */
    private String sortBy;

    /** 当前页 */
    private long current = 1L;

    /** 每页条数 */
    private long size = 20L;
}
