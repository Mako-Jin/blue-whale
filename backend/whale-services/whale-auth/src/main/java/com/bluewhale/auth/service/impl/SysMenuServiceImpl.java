package com.bluewhale.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.bluewhale.auth.dto.MenuRequest;
import com.bluewhale.auth.entity.SysMenu;
import com.bluewhale.auth.mapper.SysMenuMapper;
import com.bluewhale.auth.service.SysMenuService;
import com.bluewhale.auth.vo.MenuVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统菜单服务实现
 *
 * @author BlueWhale
 */
@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    /** 顶级菜单的父 ID */
    private static final long ROOT_PARENT_ID = 0L;

    @Override
    public List<MenuVO> tree() {
        List<MenuVO> all = list(new LambdaQueryWrapper<SysMenu>()
                        .orderByAsc(SysMenu::getSort)
                        .orderByAsc(SysMenu::getId))
                .stream().map(MenuVO::from).toList();
        return buildChildren(all, ROOT_PARENT_ID);
    }

    @Override
    public List<MenuVO> treeByPermissionCodes(Set<String> permissionCodes) {
        List<SysMenu> menus = list(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getStatus, 1)
                .eq(SysMenu::getVisible, 1)
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getId));
        if (permissionCodes != null) {
            menus = retainGrantedWithAncestors(menus, permissionCodes);
        }
        return buildChildren(menus.stream().map(MenuVO::from).toList(), ROOT_PARENT_ID);
    }

    /**
     * 保留权限码命中的菜单及其祖先目录（目录自身 permissionCode 常为空，否则会整棵断裂）
     *
     * @param menus 启用且可见的菜单列表
     * @param permissionCodes 用户权限码集合
     * @return 命中菜单及其祖先目录的集合
     */
    private List<SysMenu> retainGrantedWithAncestors(List<SysMenu> menus, Set<String> permissionCodes) {
        Map<Long, SysMenu> byId = new HashMap<>();
        menus.forEach(menu -> byId.put(menu.getId(), menu));
        Map<Long, SysMenu> retained = new HashMap<>();
        for (SysMenu menu : menus) {
            if (menu.getPermissionCode() != null && permissionCodes.contains(menu.getPermissionCode())) {
                retained.put(menu.getId(), menu);
                appendAncestors(menu, byId, retained);
            }
        }
        return new ArrayList<>(retained.values());
    }

    /**
     * 自底向上补齐祖先目录到保留集合，遇根节点或已保留节点即终止
     *
     * @param menu 命中权限码的菜单
     * @param byId 菜单 ID 到实体的索引
     * @param retained 保留集合（就地写入祖先目录）
     */
    private void appendAncestors(SysMenu menu, Map<Long, SysMenu> byId, Map<Long, SysMenu> retained) {
        Long parentId = menu.getParentId();
        while (parentId != null && parentId != ROOT_PARENT_ID && !retained.containsKey(parentId)) {
            SysMenu parent = byId.get(parentId);
            if (parent == null) {
                return;
            }
            retained.put(parent.getId(), parent);
            parentId = parent.getParentId();
        }
    }

    /**
     * 简单递归组树
     *
     * @param all 平铺的菜单列表
     * @param parentId 父菜单 ID
     * @return 该父菜单下的子节点列表
     */
    private List<MenuVO> buildChildren(List<MenuVO> all, Long parentId) {
        return all.stream()
                .filter(vo -> vo.getParentId() != null && parentId.equals(vo.getParentId()))
                .peek(vo -> vo.setChildren(buildChildren(all, vo.getId())))
                .toList();
    }

    @Override
    public MenuVO getDetail(Long id) {
        SysMenu menu = getById(id);
        if (menu == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return MenuVO.from(menu);
    }

    @Override
    public void create(MenuRequest request) {
        SysMenu menu = new SysMenu();
        copyProperties(request, menu);
        save(menu);
    }

    @Override
    public void update(Long id, MenuRequest request) {
        SysMenu menu = getById(id);
        if (menu == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        copyProperties(request, menu);
        updateById(menu);
    }

    @Override
    public void delete(Long id) {
        Long children = baseMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在子菜单，不允许删除");
        }
        removeById(id);
    }

    /**
     * 将请求参数拷贝到菜单实体（空值取默认：parentId=0、sort=0、menuType=MENU、visible=1、status=1）
     *
     * @param request 菜单请求参数
     * @param menu 目标菜单实体
     */
    private void copyProperties(MenuRequest request, SysMenu menu) {
        menu.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        menu.setName(request.getName());
        menu.setPath(request.getPath());
        menu.setComponent(request.getComponent());
        menu.setIcon(request.getIcon());
        menu.setSort(request.getSort() == null ? 0 : request.getSort());
        menu.setMenuType(StringUtils.hasText(request.getMenuType()) ? request.getMenuType() : "MENU");
        menu.setPermissionCode(request.getPermissionCode());
        menu.setVisible(request.getVisible() == null ? 1 : request.getVisible());
        menu.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }
}
