package com.bluewhale.scene.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 布局版本快照实体，对应表 {@code sc_layout_version}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sc_layout_version")
public class LayoutVersionEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属场景 ID */
    private Long sceneId;

    /** 版本号 */
    private Integer version;

    /** 布局 JSON */
    private String layoutJson;

    /** SHA-256 校验和 */
    private String checksum;
}
