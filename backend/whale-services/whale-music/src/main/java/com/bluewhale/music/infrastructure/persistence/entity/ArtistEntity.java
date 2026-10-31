package com.bluewhale.music.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 歌手实体，对应表 {@code mu_artist}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mu_artist")
public class ArtistEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 艺名 */
    private String name;

    /** 头像文件 ID */
    private Long avatarFileId;

    /** 地区 */
    private String region;

    /** 简介 */
    private String bio;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;
}
