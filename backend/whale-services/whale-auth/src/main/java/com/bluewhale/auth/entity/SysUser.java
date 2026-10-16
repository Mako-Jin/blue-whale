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
    private String username;
    /** 登录密码（BCrypt 密文），任何查询响应不得返回 */
    private String password;
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
