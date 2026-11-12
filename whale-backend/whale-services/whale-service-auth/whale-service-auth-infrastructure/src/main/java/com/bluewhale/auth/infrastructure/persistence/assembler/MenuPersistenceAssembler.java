package com.bluewhale.auth.infrastructure.persistence.assembler;

import com.bluewhale.auth.domain.aggregate.MenuAggregate;
import com.bluewhale.auth.infrastructure.persistence.entity.SysMenuPo;

/**
 * Menu 聚合与其持久化对象（PO）的双向转换器（手写 Assembler，REQ-ARCH-001 / T-DDD-6）。
 *
 * <p>选择手写而非 MapStruct：显式转换更可控、可读；{@code parentId}/{@code sort}/{@code menuType}/
 * {@code visible}/{@code status} 的空值归一化（按默认值，与既有 Service 的口径一致）由
 * {@code MenuAggregate.reconstitute} 在领域侧完成，本类仅透传 PO 原值——历史数据中的
 * null {@code menuType} 在读入聚合时归一化为 MENU，首次保存写回时会被一并修正。
 * 未映射字段 {@code deleted} 不属于 Menu 聚合关键项，转换时保持 PO 原值不动。</p>
 *
 * @author BlueWhale
 */
public final class MenuPersistenceAssembler {

    /**
     * 私有构造器：工具类，禁止实例化
     */
    private MenuPersistenceAssembler() {
    }

    /**
     * PO → 领域对象：{@code parentId}/{@code sort}/{@code menuType}/{@code visible}/{@code status}
     * 为 null 时透传，由 {@code MenuAggregate.reconstitute} 按默认值归一化
     *
     * @param po 菜单持久化对象
     * @return 菜单聚合根
     */
    public static MenuAggregate toDomain(SysMenuPo po) {
        return MenuAggregate.reconstitute(
                po.getId(),
                po.getTenantId(),
                po.getParentId(),
                po.getName(),
                po.getPath(),
                po.getComponent(),
                po.getIcon(),
                po.getSort(),
                po.getMenuType(),
                po.getPermissionCode(),
                po.getVisible(),
                po.getStatus(),
                po.getCreateTime(),
                po.getUpdateTime());
    }

    /**
     * 领域对象 → PO：仅转换 Menu 聚合所承载的字段
     *
     * @param menu 菜单聚合根
     * @return 菜单持久化对象
     */
    public static SysMenuPo toPo(MenuAggregate menu) {
        SysMenuPo po = new SysMenuPo();
        po.setId(menu.getId());
        po.setTenantId(menu.getTenantId());
        po.setParentId(menu.getParentId());
        po.setName(menu.getName());
        po.setPath(menu.getPath());
        po.setComponent(menu.getComponent());
        po.setIcon(menu.getIcon());
        po.setSort(menu.getSort());
        po.setMenuType(menu.getMenuType());
        po.setPermissionCode(menu.getPermissionCode());
        po.setVisible(menu.getVisible());
        po.setStatus(menu.getStatus());
        po.setCreateTime(menu.getCreateTime());
        po.setUpdateTime(menu.getUpdateTime());
        return po;
    }
}
