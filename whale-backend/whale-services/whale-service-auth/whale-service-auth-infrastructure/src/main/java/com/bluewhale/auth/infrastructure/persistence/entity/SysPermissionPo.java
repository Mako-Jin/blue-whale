package com.bluewhale.auth.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限实体
 * 支持系统/菜单/接口/按钮/数据/属性层级
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_permission")
public class SysPermissionPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String permCode;
    private String permName;
    /** 权限类型：SYSTEM / MENU / API / BUTTON / DATA / ATTR */
    private String permType;
    private String path;
    private String method;
    private Integer sortNo;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
