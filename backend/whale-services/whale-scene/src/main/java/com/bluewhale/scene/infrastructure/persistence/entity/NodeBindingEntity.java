package com.bluewhale.scene.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 节点入口绑定实体，对应表 {@code sc_node_binding}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sc_node_binding")
public class NodeBindingEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 节点 ID */
    private Long nodeId;

    /** 入口类型：SYSTEM / PAGE / KNOWLEDGE / EXTERNAL */
    private String entryType;

    /** 目标地址 */
    private String targetPath;

    /** 访问所需权限码 */
    private String permissionCode;

    /** 是否启用 */
    private Integer enabled;
}
