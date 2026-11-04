package com.bluewhale.scene.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 场景可见性实体，对应表 {@code sc_visibility}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sc_visibility")
public class SceneVisibilityEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属场景 ID */
    private Long sceneId;

    /** 节点 ID；0 表示场景级规则 */
    private Long nodeId;

    /** 主体类型：ALL / ROLE / DEPT / USER */
    private String subjectType;

    /** 主体标识（ALL 为空） */
    private String subjectId;

    /** 效果：ALLOW / DENY */
    private String effect;
}
