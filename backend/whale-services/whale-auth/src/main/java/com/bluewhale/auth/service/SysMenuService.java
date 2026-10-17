package com.bluewhale.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.bluewhale.auth.dto.MenuRequest;
import com.bluewhale.auth.entity.SysMenu;
import com.bluewhale.auth.vo.MenuVO;

import java.util.List;
import java.util.Set;

/**
 * 系统菜单服务
 *
 * @author BlueWhale
 */
public interface SysMenuService extends IService<SysMenu> {

    /** 菜单树（parentId=0 为根，按 sort 升序） */
    List<MenuVO> tree();

    /**
     * 按权限码过滤的菜单树：仅保留权限码命中及其祖先目录的启用可见菜单
     *
     * @param permissionCodes 用户权限码集合；传 {@code null} 表示不过滤（返回全部启用可见菜单）
     */
    List<MenuVO> treeByPermissionCodes(Set<String> permissionCodes);

    MenuVO getDetail(Long id);

    void create(MenuRequest request);

    void update(Long id, MenuRequest request);

    void delete(Long id);
}
