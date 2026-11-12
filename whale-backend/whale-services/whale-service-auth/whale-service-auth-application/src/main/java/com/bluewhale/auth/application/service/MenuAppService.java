package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.event.DomainEventPublisher;
import com.bluewhale.auth.application.model.command.SaveMenuCommand;
import com.bluewhale.auth.application.model.vo.MenuVO;
import com.bluewhale.auth.domain.aggregate.MenuAggregate;
import com.bluewhale.auth.domain.repository.MenuRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.List;
import java.util.Set;

/**
 * 菜单应用服务（AUTH-17 / T-DDD-9）
 *
 * <p>承载管理端菜单用例编排：菜单树 / 权限过滤菜单树 / 详情 / 新增 / 更新 / 删除。
 * 依赖仅限 {@link MenuRepository}、{@link DomainEventPublisher} 与既有 VO，返回 VO 而非领域对象；
 * 树组装与祖先保留语义与旧 {@code SysMenuServiceImpl} 逐行等价。</p>
 *
 * @author BlueWhale
 */
public interface MenuAppService {

    /**
     * 查询全量菜单树
     *
     * @return 顶级菜单节点列表（各节点含递归子节点）
     */
    List<MenuVO> tree();

    /**
     * 按权限码过滤查询菜单树（命中菜单 + 祖先目录保留）
     *
     * @param permissionCodes 用户权限码集合；为 {@code null} 时不做过滤
     * @return 过滤后的顶级菜单节点列表（各节点含递归子节点）
     */
    List<MenuVO> treeByPermissionCodes(Set<String> permissionCodes);

    /**
     * 查询菜单详情
     *
     * @param id 菜单 ID
     * @return 菜单视图对象
     * @throws BizException 菜单不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    MenuVO getDetail(Long id);

    /**
     * 新增菜单（默认值由 {@link MenuAggregate#create} 负责）
     *
     * @param command 菜单新增命令
     */
    void create(SaveMenuCommand command);

    /**
     * 更新菜单（PATCH 契约：command 中为 null 的字段保持原值，由 {@link MenuAggregate#applyUpdate} 负责）
     *
     * @param id 菜单 ID
     * @param command 菜单更新命令
     * @throws BizException 菜单不存在时抛出 {@link ErrorCode#NOT_FOUND}
     */
    void update(Long id, SaveMenuCommand command);

    /**
     * 删除菜单（存在子菜单时拒绝）
     *
     * @param id 菜单 ID
     * @throws BizException 存在子菜单时抛出 {@link ErrorCode#PARAM_ERROR}
     */
    void delete(Long id);
}
