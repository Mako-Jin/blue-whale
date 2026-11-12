package com.bluewhale.auth.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户配置实体（权限中心通用配置存储）。
 *
 * <p>auth 只存取、不解释业务语义：{@code configKey} 按前缀分域
 * （如 {@code notify.twin.alert} / {@code ui.theme}），值统一为 JSON，
 * 结构由 config_key 所属业务域自行定义并解释。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_user_config")
public class SysUserConfigPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID（逻辑关联 sys_tenant.id） */
    private Long tenantId;
    /** 用户 ID（逻辑关联 sys_user.id） */
    private Long userId;
    /** 配置键（前缀分域，如 notify.twin.alert / ui.theme） */
    private String configKey;
    /** 配置值（JSON，结构由所属业务域定义） */
    private String configValue;
    /** 最后更新人用户 ID */
    private Long updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
