package com.bluewhale.video.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 视频创作者实体，对应表 {@code vd_creator}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vd_creator")
public class CreatorEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 关联用户 ID */
    private Long userId;

    /** 创作者昵称 */
    private String displayName;

    /** 头像文件 ID */
    private Long avatarFileId;

    /** 简介 */
    private String bio;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;
}
