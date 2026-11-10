package com.bluewhale.twin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 孪生实体实体，对应表 {@code twin_entity}。
 *
 * <p>场景树节点：模型资产（GLB/GLTF 存 whale-file，本表只存 file_id）
 * + 变换 + 父节点；{@code parentId} 为空表示根节点。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("twin_entity")
public class TwinEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID（行级隔离） */
    private Long tenantId;

    /** 所属场景ID（twin_scene.id） */
    private Long sceneId;

    /** 实体编码（场景内唯一，如 boiler-01） */
    private String entityCode;

    /** 实体名称 */
    private String name;

    /** 父节点ID（twin_entity.id，NULL 为根节点） */
    private Long parentId;

    /** 模型资产文件ID（whale-file，GLB/GLTF） */
    private Long modelFileId;

    /** 变换 JSON（position/rotation/scale，前端约定） */
    private String transformJson;

    /** 实体级扩展配置 JSON（如强调色/动画预设） */
    private String extraConfig;

    /** 同级排序号 */
    private Integer sortOrder;
}
