package com.bluewhale.auth.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增/更新菜单请求
 *
 * @author BlueWhale
 */
@Data
public class MenuRequest {

    /** 父菜单ID，0 表示顶级 */
    private Long parentId;

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 64, message = "菜单名称长度不能超过 64")
    private String name;

    private String path;
    private String component;
    private String icon;
    private Integer sort;
    /** 菜单类型：DIR 目录 / MENU 菜单，默认 MENU */
    private String menuType;
    private String permissionCode;
    private Integer visible;
    private Integer status;
}
