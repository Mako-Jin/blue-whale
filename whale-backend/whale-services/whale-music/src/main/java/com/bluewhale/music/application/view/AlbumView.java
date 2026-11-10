package com.bluewhale.music.application.view;

import com.bluewhale.music.domain.model.PublishState;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 专辑视图。
 *
 * @author BlueWhale
 */
@Data
public class AlbumView {

    /** 主键 */
    private Long id;

    /** 歌手 ID */
    private Long artistId;

    /** 标题 */
    private String title;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 发行日期 */
    private LocalDate releaseDate;

    /** 专辑类型 */
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
}
