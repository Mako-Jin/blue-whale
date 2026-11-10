package com.bluewhale.scene.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户场景偏好实体，对应表 {@code sc_user_preference}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sc_user_preference")
public class UserPreferenceEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 视图模式：2D / 3D */
    private String viewMode;

    /** 默认场景标识 */
    private String defaultSceneKey;
}
