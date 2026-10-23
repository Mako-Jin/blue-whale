package com.bluewhale.auth.application.menu;

import com.bluewhale.auth.application.menu.command.SaveMenuCommand;
import com.bluewhale.auth.domain.menu.aggregate.Menu;
import com.bluewhale.auth.domain.menu.repository.MenuRepository;
import com.bluewhale.auth.vo.MenuVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单应用服务（AUTH-17 / T-DDD-9）
 *
 * <p>承载管理端菜单用例编排：菜单树 / 权限过滤菜单树 / 详情 / 新增 / 更新 / 删除。
 * 依赖仅限 {@link MenuRepository} 与既有 VO，返回 VO 而非领域对象；树组装与祖先保留
 * 语义与旧 {@code SysMenuServiceImpl} 逐行等价。</p>
 *
 * @author BlueWhale
 */
@Service
public class MenuAppService {

    /** Menu 聚合仓储 */
    private final MenuRepository menuRepository;

    /**
     * 构造器注入
     *
     * @param menuRepository 菜单聚合仓储
     */
    public MenuAppService(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    /**
     * 查询全量菜单树
     *
     * @return 顶级菜单节点列表（各节点含递归子节点）
     */
    public List<MenuVO> tree() {
        List<MenuVO> all = menuRepository.findAll().stream().map(MenuVO::from).toList();
        return buildChildren(all, Menu.ROOT_PARENT_ID);
    }

    /**
     * 按权限码过滤查询菜单树（命中菜单 + 祖先目录保留）
     *
     * @param permissionCodes 用户权限码集合；为 {@code null} 时不做过滤
     * @return 过滤后的顶级菜单节点列表（各节点含递归子节点）
     */
    public List<MenuVO> treeByPermissionCodes(Set<String> permissionCodes) {
        List<Menu> menus = menuRepository.findEnabledVisible();
        if (permissionCodes != null) {
            menus = retainGrantedWithAncestors(menus, permissionCodes);
        }
        return buildChildren(menus.stream().map(MenuVO::from).toList(), Menu.ROOT_PARENT_ID);
    }

    /**
     * 查询菜单详情
     *
     * @param id 菜单 ID
     * @return 菜单视图对象
     * @throws BizException 菜单不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    public MenuVO getDetail(Long id) {
        return menuRepository.findById(id)
                .map(MenuVO::from)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
    }

    /**
     * 新增菜单（默认值由 {@link Menu#create} 负责）
     *
     * @param command 菜单新增命令
     */
    public void create(SaveMenuCommand command) {
        Menu menu = Menu.create(command.parentId(), command.name(), command.path(), command.component(),
                command.icon(), command.sort(), command.menuType(), command.permissionCode(),
                command.visible(), command.status());
        menuRepository.save(menu);
    }

    /**
     * 更新菜单（默认值由 {@link Menu#applyChanges} 负责）
     *
     * @param id 菜单 ID
     * @param command 菜单更新命令
     * @throws BizException 菜单不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    public void update(Long id, SaveMenuCommand command) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        menu.applyChanges(command.parentId(), command.name(), command.path(), command.component(),
                command.icon(), command.sort(), command.menuType(), command.permissionCode(),
                command.visible(), command.status());
        menuRepository.save(menu);
    }

    /**
     * 删除菜单（存在子菜单时拒绝）
     *
     * @param id 菜单 ID
     * @throws BizException 存在子菜单时抛出 {@link ErrorCode#PARAM_ERROR}
     */
    public void delete(Long id) {
        if (menuRepository.countChildren(id) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在子菜单，不允许删除");
        }
        menuRepository.deleteById(id);
    }

    /**
     * 保留权限码命中的菜单及其祖先目录（目录自身 permissionCode 常为空，否则会整棵断裂）
     *
     * @param menus 启用且可见的菜单列表
     * @param permissionCodes 用户权限码集合
     * @return 命中菜单及其祖先目录的集合
     */
    private List<Menu> retainGrantedWithAncestors(List<Menu> menus, Set<String> permissionCodes) {
        Map<Long, Menu> byId = new HashMap<>();
        menus.forEach(menu -> byId.put(menu.getId(), menu));
        Map<Long, Menu> retained = new HashMap<>();
        for (Menu menu : menus) {
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
     * @param byId 菜单 ID 到聚合根的索引
     * @param retained 保留集合（就地写入祖先目录）
     */
    private void appendAncestors(Menu menu, Map<Long, Menu> byId, Map<Long, Menu> retained) {
        Long parentId = menu.getParentId();
        while (parentId != null && parentId != Menu.ROOT_PARENT_ID && !retained.containsKey(parentId)) {
            Menu parent = byId.get(parentId);
            if (parent == null) {
                return;
            }
            retained.put(parent.getId(), parent);
            parentId = parent.getParentId();
        }
    }

    /**
     * 简单递归组树（保持入参顺序，与旧实现一致）
     *
     * @param all 平铺的菜单视图列表
     * @param parentId 父菜单 ID
     * @return 该父菜单下的子节点列表
     */
    private List<MenuVO> buildChildren(List<MenuVO> all, Long parentId) {
        return all.stream()
                .filter(vo -> vo.getParentId() != null && parentId.equals(vo.getParentId()))
                .peek(vo -> vo.setChildren(buildChildren(all, vo.getId())))
                .toList();
    }
}
