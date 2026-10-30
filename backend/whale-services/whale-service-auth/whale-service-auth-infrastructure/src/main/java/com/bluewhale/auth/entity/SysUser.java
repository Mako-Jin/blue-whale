package com.bluewhale.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户实体
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 所属租户ID（逻辑关联 sys_tenant.id） */
    private Long tenantId;
    private String username;
    /** 登录密码（BCrypt 密文），任何查询响应不得返回 */
    private String password;
    /** 是否强制首登改密（1=是 / 0=否，T-03：出厂默认口令轮换后置位，改密成功后清除） */
    private Integer mustChangePassword;
    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    /** 所属部门ID（逻辑关联 sys_dept.id） */
    private Long deptId;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    private LocalDateTime lastLoginTime;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
