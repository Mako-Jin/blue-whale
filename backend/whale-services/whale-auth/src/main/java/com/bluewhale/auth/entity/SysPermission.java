package com.bluewhale.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 权限实体占位
 * 支持系统/菜单/接口/按钮/数据/属性层级
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_permission")
public class SysPermission implements Serializable {

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
    private Integer status;
    private Integer deleted;
}
