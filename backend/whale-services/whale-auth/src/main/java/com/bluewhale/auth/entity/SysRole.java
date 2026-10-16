package com.bluewhale.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色实体占位
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_role")
public class SysRole implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer sortNo;
    private Integer status;
    private Integer deleted;
}
