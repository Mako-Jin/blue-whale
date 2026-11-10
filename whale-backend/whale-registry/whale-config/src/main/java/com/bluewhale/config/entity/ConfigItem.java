package com.bluewhale.config.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务配置项实体，对应表 {@code config_item}。
 *
 * <p>公共字段（id / create_time / update_time / deleted）继承自 {@link BaseEntity}，
 * 其余字段与 {@code db/init.sql} 中的 {@code config_item} 列逐字段对齐；
 * {@code tenant_id} 由租户行级插件自动追加，实体不声明。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("config_item")
public class ConfigItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 配置键（租户内唯一，如 order.pay.timeout） */
    private String configKey;

    /** 配置值（按 valueType 解释，JSON 原文存储） */
    private String configValue;

    /** 值类型：STRING / NUMBER / BOOLEAN / JSON */
    private String valueType;

    /** 配置说明 */
    private String description;

    /** 当前版本号（创建为 1，每次值变更 +1） */
    private Integer version;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;
}
