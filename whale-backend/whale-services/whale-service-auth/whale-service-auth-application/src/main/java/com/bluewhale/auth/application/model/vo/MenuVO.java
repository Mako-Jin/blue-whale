package com.bluewhale.auth.application.model.vo;

import com.bluewhale.auth.domain.aggregate.Menu;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单树节点视图对象
 *
 * @author BlueWhale
 */
@Data
public class MenuVO implements Serializable {

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
    private List<MenuVO> children;

    /**
     * 由菜单聚合根转换为视图对象
     *
     * @param menu 菜单聚合根
     * @return 菜单视图对象
     */
    public static MenuVO from(Menu menu) {
        MenuVO vo = new MenuVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setName(menu.getName());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setIcon(menu.getIcon());
        vo.setSort(menu.getSort());
        vo.setMenuType(menu.getMenuType());
        vo.setPermissionCode(menu.getPermissionCode());
        vo.setVisible(menu.getVisible());
        vo.setStatus(menu.getStatus());
        vo.setCreateTime(menu.getCreateTime());
        vo.setUpdateTime(menu.getUpdateTime());
        return vo;
    }
}
