package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统菜单实体（前端导航：目录 / 菜单）
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_menu")
public class SysMenuPo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 所属租户ID（逻辑关联 sys_tenant.id） */
    private Long tenantId;
    /** 父菜单ID，0 表示顶级 */
    private Long parentId;
    private String name;
    /** 前端路由路径 */
    private String path;
    /** 前端组件路径 */
    private String component;
    private String icon;
    private Integer sort;
    /** 菜单类型：DIR 目录 / MENU 菜单 */
    private String menuType;
    /** 关联权限编码（逻辑关联 sys_permission.perm_code） */
    private String permissionCode;
    /** 是否可见：1 显示 / 0 隐藏 */
    private Integer visible;
    /** 状态：1 启用 / 0 停用 */
    private Integer status;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
