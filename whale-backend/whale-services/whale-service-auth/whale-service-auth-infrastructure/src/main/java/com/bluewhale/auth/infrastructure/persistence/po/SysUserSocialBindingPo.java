package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 三方登录绑定实体（权限中心扩展 M2：对应 {@code sys_user_social_binding}）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_user_social_binding")
public class SysUserSocialBindingPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 用户归属租户 ID */
    private Long tenantId;
    /** 绑定用户 ID */
    private Long userId;
    /** 三方渠道：dingtalk / feishu / wecom */
    private String provider;
    /** 三方用户唯一标识 */
    private String providerUserId;
    /** 三方 unionId（可空） */
    private String unionId;
    /** 三方昵称快照 */
    private String nickname;
    /** 三方头像快照 */
    private String avatar;
    /** 状态：1 绑定生效 / 0 已解绑 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
