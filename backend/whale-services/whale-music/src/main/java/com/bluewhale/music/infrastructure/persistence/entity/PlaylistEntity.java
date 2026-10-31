package com.bluewhale.music.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 歌单实体，对应表 {@code mu_playlist}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mu_playlist")
public class PlaylistEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所有者用户 ID，官方歌单为 0 */
    private Long ownerId;

    /** 标题 */
    private String title;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 简介 */
    private String description;

    /** 歌单类型：USER / OFFICIAL */
    private String playlistType;

    /** 可见性：PUBLIC / PRIVATE */
    private String visibility;

    /** 歌曲数量 */
    private Integer songCount;

    /** 播放次数 */
    private Long playCount;

    /** 状态：1 正常 / 0 停用 */
    private Integer status;
}
