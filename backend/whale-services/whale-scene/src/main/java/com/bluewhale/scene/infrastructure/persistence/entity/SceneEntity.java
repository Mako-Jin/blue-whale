package com.bluewhale.scene.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 场景实体，对应表 {@code sc_scene}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sc_scene")
public class SceneEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 场景标识（租户内唯一） */
    private String sceneKey;

    /** 场景名称 */
    private String name;

    /** 场景说明 */
    private String description;

    /** 状态：DRAFT / PUBLISHED / OFFLINE */
    private String status;

    /** 当前已发布布局版本号 */
    private Integer layoutVersion;

    /** 最近发布时间 */
    private LocalDateTime publishedAt;
}
