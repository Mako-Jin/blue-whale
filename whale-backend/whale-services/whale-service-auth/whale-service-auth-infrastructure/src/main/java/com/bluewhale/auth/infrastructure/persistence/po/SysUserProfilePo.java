package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户身份档案主表实体（权限中心扩展 M3：对应 {@code sys_user_profile}）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_user_profile")
public class SysUserProfilePo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID */
    private Long tenantId;
    /** 用户 ID（全局唯一，一用户一份档案） */
    private Long userId;
    /** 性别：MALE 男 / FEMALE 女 / UNKNOWN 未知 */
    private String gender;
    /** 出生日期 */
    private LocalDate birthDate;
    /** 籍贯/出生地 */
    private String birthPlace;
    /** 家庭住址 */
    private String homeAddress;
    /** 民族 */
    private String nation;
    /** 政治面貌 */
    private String politicalStatus;
    /** 婚姻状况：UNMARRIED/MARRIED/DIVORCED/WIDOWED */
    private String maritalStatus;
    /** 备注 */
    private String remarks;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
