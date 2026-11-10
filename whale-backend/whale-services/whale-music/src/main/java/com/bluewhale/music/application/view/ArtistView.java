package com.bluewhale.music.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌手视图。
 *
 * @author BlueWhale
 */
@Data
public class ArtistView {

    /** 主键 */
    private Long id;

    /** 艺名 */
    private String name;

    /** 头像文件 ID */
    private Long avatarFileId;

    /** 地区 */
    private String region;

    /** 简介 */
    private String bio;

    /** 状态 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
