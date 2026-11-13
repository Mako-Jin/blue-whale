package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户联系方式子表实体（权限中心扩展 M3：对应 {@code sys_user_contact}）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_user_contact")
public class SysUserContactPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID */
    private Long tenantId;
    /** 用户 ID */
    private Long userId;
    /** 联系方式类型：MOBILE 手机 / EMAIL 邮箱 / WECHAT 微信 / DINGTALK 钉钉 / OTHER 其他 */
    private String contactType;
    /** 联系方式值 */
    private String contactValue;
    /** 是否主联系方式：1 是（同类型唯一，聚合根保证） */
    private Integer isPrimary;
    /** 已验证标记：0 未验证 / 1 已验证 */
    private Integer verified;
    /** 排序号（同类型内有序） */
    private Integer sortNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
