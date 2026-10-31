package com.bluewhale.music.domain.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 专辑领域模型。
 *
 * @author BlueWhale
 */
@Data
public class Album {

    /** 主键 */
    private Long id;

    /** 歌手 ID */
    private Long artistId;

    /** 专辑标题 */
    private String title;

    /** 封面文件 ID（whale-file，必填） */
    private Long coverFileId;

    /** 发行日期 */
    private LocalDate releaseDate;

    /** 专辑类型：SINGLE / EP / ALBUM / COMPILATION */
    private String albumType;

    /** 简介 */
    private String intro;

    /** 发布状态 */
    private PublishState publishState;

    /** 排序 */
    private Integer sortNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 上架专辑：仅草稿或已下架状态可上架，重复上架幂等。
     */
    public void publish() {
        if (publishState == PublishState.PUBLISHED) {
            return;
        }
        publishState = PublishState.PUBLISHED;
    }

    /**
     * 下架专辑：重复下架幂等。
     */
    public void offline() {
        publishState = PublishState.OFFLINE;
    }
}
