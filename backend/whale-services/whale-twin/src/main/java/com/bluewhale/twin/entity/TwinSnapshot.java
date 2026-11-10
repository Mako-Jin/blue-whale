package com.bluewhale.twin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 孪生发布快照实体，对应表 {@code twin_snapshot}。
 *
 * <p>场景发布时把「场景配置 + 实体树 + 绑定 + 标注」整包冻结为 JSON，
 * 版本号取发布时刻的场景 {@code version}（scene_id + version 唯一）；
 * 回滚时按快照重建工作副本。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("twin_snapshot")
public class TwinSnapshot extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID（行级隔离） */
    private Long tenantId;

    /** 所属场景ID（twin_scene.id） */
    private Long sceneId;

    /** 快照版本号（同场景内递增，取发布时场景 version） */
    private Integer version;

    /** 快照内容（场景配置 + 实体树 + 绑定 + 标注的整包 JSON） */
    private String snapshotJson;

    /** 发布说明 */
    private String remark;

    /** 发布时间（DDL 默认当前时间） */
    private LocalDateTime publishTime;

    /** 发布人用户ID */
    private Long publishedBy;
}
