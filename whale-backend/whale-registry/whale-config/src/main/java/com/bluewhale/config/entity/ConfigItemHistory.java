package com.bluewhale.config.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 配置变更历史实体，对应表 {@code config_item_history}。
 *
 * <p>只追加不修改：创建 / 更新 / 回滚 / 删除均落一行，{@code version} 记变更后版本号；
 * 回滚也产生新版本（不覆盖历史），保证审计链完整可追溯。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("config_item_history")
public class ConfigItemHistory extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 配置项ID（逻辑关联 config_item.id） */
    private Long itemId;

    /** 配置键（冗余存储，项删除后仍可追溯） */
    private String configKey;

    /** 操作类型：CREATE 创建 / UPDATE 更新 / ROLLBACK 回滚 / DELETE 删除 */
    private String operation;

    /** 变更前值（创建为 {@code null}） */
    private String oldValue;

    /** 变更后值（删除为 {@code null}） */
    private String newValue;

    /** 变更后版本号 */
    private Integer version;

    /** 操作人ID（网关 X-User-Id 注入，内部操作为 {@code null}） */
    private Long operatorId;

    /** 操作人用户名（冗余存储） */
    private String operator;
}
