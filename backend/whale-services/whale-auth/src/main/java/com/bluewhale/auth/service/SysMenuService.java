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

    /**
     * 菜单树（parentId=0 为根，按 sort 升序）
     *
     * @return 菜单树节点列表
     */
    List<MenuVO> tree();

    /**
     * 按权限码过滤的菜单树：仅保留权限码命中及其祖先目录的启用可见菜单
     *
     * @param permissionCodes 用户权限码集合；传 {@code null} 表示不过滤（返回全部启用可见菜单）
     * @return 过滤后的菜单树节点列表
     */
    List<MenuVO> treeByPermissionCodes(Set<String> permissionCodes);

    /**
     * 菜单详情
     *
     * @param id 菜单 ID
     * @return 菜单视图对象
     */
    MenuVO getDetail(Long id);

    /**
     * 创建菜单
     *
     * @param request 菜单请求参数
     */
    void create(MenuRequest request);

    /**
     * 更新菜单
     *
     * @param id 菜单 ID
     * @param request 菜单请求参数
     */
    void update(Long id, MenuRequest request);

    /**
     * 删除菜单
     *
     * @param id 菜单 ID
     */
    void delete(Long id);
}
