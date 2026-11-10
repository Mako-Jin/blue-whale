package com.bluewhale.twin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 孪生标注/热点实体，对应表 {@code twin_marker}。
 *
 * <p>场景内的三维位置锚点：文本说明（label）、可交互热点（hotspot）、
 * 告警定位（alarm）；{@code entityId} 为空表示场景级锚点，否则挂靠实体。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("twin_marker")
public class TwinMarker extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID（行级隔离） */
    private Long tenantId;

    /** 所属场景ID（twin_scene.id） */
    private Long sceneId;

    /** 挂靠实体ID（twin_entity.id，NULL 为场景级锚点） */
    private Long entityId;

    /** 类型：label 标签 / hotspot 热点 / alarm 告警定位 */
    private String markerType;

    /** 标注内容（文本或 JSON，前端约定） */
    private String content;

    /** 三维坐标 JSON（x/y/z） */
    private String positionJson;

    /** 点击跳转地址（可空） */
    private String linkUrl;

    /** 是否启用：0 停用 / 1 启用 */
    private Integer enabled;
}
