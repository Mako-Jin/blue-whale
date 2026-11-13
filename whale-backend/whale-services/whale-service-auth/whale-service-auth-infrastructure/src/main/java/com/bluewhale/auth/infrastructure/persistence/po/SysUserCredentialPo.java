package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户证件子表实体（权限中心扩展 M3：对应 {@code sys_user_credential}，证件号密文存储）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_user_credential")
public class SysUserCredentialPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID */
    private Long tenantId;
    /** 用户 ID */
    private Long userId;
    /** 证件类型：ID_CARD 身份证 / PASSPORT 护照 / HK_MO_PASS 港澳通行证 / OTHER 其他 */
    private String credentialType;
    /** 证件号（AES-GCM 密文 Base64，禁止明文） */
    private String credentialNo;
    /** 证件持有人姓名 */
    private String holderName;
    /** 是否主证件：1 是（同类型唯一，聚合根保证） */
    private Integer isPrimary;
    /** 签发日期 */
    private LocalDate issueDate;
    /** 有效期至 */
    private LocalDate expiryDate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
