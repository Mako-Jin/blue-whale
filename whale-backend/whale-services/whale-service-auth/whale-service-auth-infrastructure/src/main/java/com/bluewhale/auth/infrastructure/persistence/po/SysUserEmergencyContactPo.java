package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户紧急联系人子表实体（权限中心扩展 M3：对应 {@code sys_user_emergency_contact}，多条有序）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_user_emergency_contact")
public class SysUserEmergencyContactPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID */
    private Long tenantId;
    /** 用户 ID */
    private Long userId;
    /** 联系人姓名 */
    private String contactName;
    /** 与本人关系：PARENT / SPOUSE / CHILD / SIBLING / FRIEND / OTHER */
    private String relationship;
    /** 联系电话 */
    private String phone;
    /** 排序号 */
    private Integer sortNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
