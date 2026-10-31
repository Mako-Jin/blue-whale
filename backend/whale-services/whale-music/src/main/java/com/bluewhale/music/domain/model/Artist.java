package com.bluewhale.music.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌手领域模型。
 *
 * @author BlueWhale
 */
@Data
public class Artist {

    /** 主键 */
    private Long id;

    /** 艺名（租户内唯一） */
    private String name;

    /** 头像文件 ID（whale-file） */
    private Long avatarFileId;

    /** 地区 */
    private String region;

    /** 简介 */
    private String bio;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 判断歌手是否启用。
     *
     * @return 启用返回 {@code true}
     */
    public boolean enabled() {
        return status != null && status == 1;
    }
}
