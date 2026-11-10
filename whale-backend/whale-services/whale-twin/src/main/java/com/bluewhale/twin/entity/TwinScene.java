package com.bluewhale.twin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 孪生场景实体，对应表 {@code twin_scene}。
 *
 * <p>3D 孪生场景的定义载体：实体树、绑定、标注都挂在场景下；
 * 发布时整包冻结进 {@code twin_snapshot}。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("twin_scene")
public class TwinScene extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 场景编码（租户内唯一，如 plant-demo） */
    private String code;

    /** 场景名称 */
    private String name;

    /** 场景描述 */
    private String description;

    /** 缩略图文件ID（whale-file） */
    private Long thumbnailFileId;

    /** 场景级配置 JSON（背景/光照/雾效等，前端约定） */
    private String sceneConfig;

    /** 当前编辑版本号（发布时冻结进快照） */
    private Integer version;

    /** 状态：draft 草稿 / published 已发布 / disabled 已停用 */
    private String status;

    /** 创建人用户ID */
    private Long createdBy;
}
