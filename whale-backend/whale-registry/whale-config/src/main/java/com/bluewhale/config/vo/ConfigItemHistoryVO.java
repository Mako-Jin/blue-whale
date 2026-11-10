package com.bluewhale.config.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 配置变更历史视图对象（字段与 {@code config_item_history} 表一致）
 *
 * @author BlueWhale
 */
@Data
public class ConfigItemHistoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 配置项ID */
    private Long itemId;

    /** 配置键 */
    private String configKey;

    /** 操作类型：CREATE 创建 / UPDATE 更新 / ROLLBACK 回滚 / DELETE 删除 */
    private String operation;

    /** 变更前值 */
    private String oldValue;

    /** 变更后值 */
    private String newValue;

    /** 变更后版本号 */
    private Integer version;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人用户名 */
    private String operator;

    /** 创建时间 */
    private LocalDateTime createTime;
}
