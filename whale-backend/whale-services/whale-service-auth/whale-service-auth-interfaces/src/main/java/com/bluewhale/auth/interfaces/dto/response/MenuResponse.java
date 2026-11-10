package com.bluewhale.auth.interfaces.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单树节点响应
 *
 * @author BlueWhale
 */
@Data
public class MenuResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String component;
    private String icon;
    private Integer sort;
    private String menuType;
    private String permissionCode;
    private Integer visible;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<MenuResponse> children;
}
