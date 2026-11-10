package com.bluewhale.twin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 孪生数据源绑定实体，对应表 {@code twin_binding}。
 *
 * <p>把 3D 实体和数据源连起来：{@code iot_device} 源的 {@code bindKey} 是设备ID，
 * 由运行时轮询 whale-iot 内网实时态；{@code internal_api} 源的 {@code bindKey}
 * 是接口路径（M3 起接入轮询）。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("twin_binding")
public class TwinBinding extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID（行级隔离） */
    private Long tenantId;

    /** 孪生实体ID（twin_entity.id） */
    private Long entityId;

    /** 数据源类型：iot_device / internal_api */
    private String sourceType;

    /** 绑定键：iot_device 为设备ID；internal_api 为接口路径 */
    private String bindKey;

    /** 属性映射 JSON（源字段 → 实体状态键，可带阈值） */
    private String propertyMap;

    /** 轮询间隔毫秒（iot_device 生效；不小于运行时 tick） */
    private Integer pollIntervalMs;

    /** 是否启用：0 停用 / 1 启用 */
    private Integer enabled;
}
