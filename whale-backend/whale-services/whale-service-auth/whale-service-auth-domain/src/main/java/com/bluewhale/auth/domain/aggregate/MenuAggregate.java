package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;

/**
 * Menu 聚合根（对应 {@code sys_menu}，REQ-ARCH-001 / T-DDD-6）。
 *
 * <p><b>不变量</b>：① {@code parentId} 非空（{@code 0} 表示顶级）；② {@code name} 非空；
 * ③ {@code menuType ∈ {DIR 目录, MENU 菜单}}；④ {@code visible ∈ {1 显示, 0 隐藏}}；
 * ⑤ {@code status ∈ {1 启用, 0 停用}}。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。写路径接入留待 AUTH-17。</p>
 *
 * @author BlueWhale
 */
public class MenuAggregate implements Entity<Long> {

    /** 菜单类型：目录 */
    public static final String MENU_TYPE_DIR = "DIR";
    /** 菜单类型：菜单 */
    public static final String MENU_TYPE_MENU = "MENU";
    /** 可见性：显示 */
    public static final int VISIBLE_SHOWN = 1;
    /** 可见性：隐藏 */
    public static final int VISIBLE_HIDDEN = 0;
    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;
    /** 顶级菜单的父 ID */
    public static final long ROOT_PARENT_ID = 0L;

    private final Long id;
    private final Long tenantId;
    private Long parentId;
    private String name;
    private String path;
    private String component;
    private String icon;
    private int sort;
    private String menuType;
    private String permissionCode;
    private int visible;
    private int status;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量
     *
     * @param id 菜单 ID（新建时为 {@code null}）
     * @param tenantId 所属租户 ID
     * @param parentId 父菜单 ID（0 表示顶级）
     * @param name 菜单名称
     * @param path 前端路由路径
     * @param component 前端组件路径
     * @param icon 图标
     * @param sort 排序号
     * @param menuType 菜单类型（DIR / MENU）
     * @param permissionCode 关联权限编码
     * @param visible 是否可见（1 显示 / 0 隐藏）
     * @param status 状态（1 启用 / 0 停用）
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private MenuAggregate(Long id, Long tenantId, Long parentId, String name, String path, String component, String icon,
                          int sort, String menuType, String permissionCode, int visible, int status,
                          LocalDateTime createTime, LocalDateTime updateTime) {
        if (tenantId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "菜单所属租户不能为空");
        }
        if (parentId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "父菜单 ID 不能为空（0 表示顶级）");
        }
        if (name == null || name.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "菜单名称不能为空");
        }
        if (!MENU_TYPE_DIR.equals(menuType) && !MENU_TYPE_MENU.equals(menuType)) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "菜单类型非法，仅支持 DIR（目录）/ MENU（菜单）");
        }
        if (visible != VISIBLE_SHOWN && visible != VISIBLE_HIDDEN) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "可见性非法，仅支持 1（显示）/ 0（隐藏）");
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.parentId = parentId;
        this.name = name;
        this.path = path;
        this.component = component;
        this.icon = icon;
        this.sort = sort;
        this.menuType = menuType;
        this.permissionCode = permissionCode;
        this.visible = visible;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：新建菜单（无 ID），空值取默认（parentId=0、sort=0、menuType=MENU、visible=1、status=1）
     *
     * @param tenantId 所属租户 ID
     * @param parentId 父菜单 ID，为空时按 0（顶级）处理
     * @param name 菜单名称
     * @param path 前端路由路径
     * @param component 前端组件路径
     * @param icon 图标
     * @param sort 排序号，为空时按 0 处理
     * @param menuType 菜单类型，为空时按 MENU 处理
     * @param permissionCode 关联权限编码
     * @param visible 是否可见，为空时按显示处理
     * @param status 状态，为空时按启用处理
     * @return 新建的菜单聚合根
     */
    public static MenuAggregate create(Long tenantId, Long parentId, String name, String path, String component, String icon,
                                       Integer sort, String menuType, String permissionCode, Integer visible,
                                       Integer status) {
        LocalDateTime now = LocalDateTime.now();
        return new MenuAggregate(null, tenantId, parentId == null ? ROOT_PARENT_ID : parentId, name, path, component, icon,
                sort == null ? 0 : sort, menuType == null || menuType.isBlank() ? MENU_TYPE_MENU : menuType,
                permissionCode, visible == null ? VISIBLE_SHOWN : visible,
                status == null ? STATUS_ENABLED : status, now, now);
    }

    /**
     * 重建方法：由持久化数据还原菜单聚合根
     *
     * @param id 菜单 ID
     * @param tenantId 所属租户 ID
     * @param parentId 父菜单 ID，为空时按 0（顶级）处理
     * @param name 菜单名称
     * @param path 前端路由路径
     * @param component 前端组件路径
     * @param icon 图标
     * @param sort 排序号，为空时按 0 处理
     * @param menuType 菜单类型，为空时按 MENU 处理
     * @param permissionCode 关联权限编码
     * @param visible 是否可见，为空时按显示处理
     * @param status 状态，为空时按启用处理
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 还原的菜单聚合根
     */
    public static MenuAggregate reconstitute(Long id, Long tenantId, Long parentId, String name, String path,
                                             String component, String icon, Integer sort, String menuType,
                                             String permissionCode, Integer visible, Integer status,
                                             LocalDateTime createTime, LocalDateTime updateTime) {
        return new MenuAggregate(id, tenantId, parentId == null ? ROOT_PARENT_ID : parentId, name, path, component, icon,
                sort == null ? 0 : sort, menuType == null || menuType.isBlank() ? MENU_TYPE_MENU : menuType,
                permissionCode, visible == null ? VISIBLE_SHOWN : visible,
                status == null ? STATUS_ENABLED : status, createTime, updateTime);
    }

    /**
     * 行为：移动到新的父菜单（对应更新接口的 {@code parentId} 赋值）
     *
     * @param parentId 新父菜单 ID（0 表示顶级）
     */
    public void moveTo(Long parentId) {
        if (parentId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "父菜单 ID 不能为空（0 表示顶级）");
        }
        this.parentId = parentId;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：重命名菜单（对应更新接口的 {@code name} 赋值）
     *
     * @param name 新菜单名称
     */
    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "菜单名称不能为空");
        }
        this.name = name;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更前端路由信息（对应更新接口的 {@code path}/{@code component}/{@code icon} 赋值）
     *
     * @param path 前端路由路径
     * @param component 前端组件路径
     * @param icon 图标
     */
    public void changeRoute(String path, String component, String icon) {
        this.path = path;
        this.component = component;
        this.icon = icon;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：修改排序号（对应更新接口的 {@code sort} 赋值）
     *
     * @param sort 新排序号
     */
    public void changeSort(int sort) {
        this.sort = sort;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更菜单类型（对应更新接口的 {@code menuType} 赋值）
     *
     * @param menuType 新菜单类型（DIR / MENU）
     */
    public void changeMenuType(String menuType) {
        if (!MENU_TYPE_DIR.equals(menuType) && !MENU_TYPE_MENU.equals(menuType)) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "菜单类型非法，仅支持 DIR（目录）/ MENU（菜单）");
        }
        this.menuType = menuType;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：绑定关联权限编码（对应更新接口的 {@code permissionCode} 赋值）
     *
     * @param permissionCode 关联权限编码
     */
    public void bindPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更可见性（对应更新接口的 {@code visible} 赋值）
     *
     * @param visible 新可见性（1 显示 / 0 隐藏）
     */
    public void changeVisible(int visible) {
        if (visible != VISIBLE_SHOWN && visible != VISIBLE_HIDDEN) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "可见性非法，仅支持 1（显示）/ 0（隐藏）");
        }
        this.visible = visible;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更状态（对应更新接口的 {@code status} 赋值）
     *
     * @param status 新状态（1 启用 / 0 停用）
     */
    public void changeStatus(int status) {
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
        this.status = status;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：一次性应用更新接口的全部字段（PATCH 契约）
     *
     * <p><b>PATCH 契约</b>：所有参数统一 {@code null = 不修改}（保持原值），不做任何隐式默认值
     * 降级（如不再把空 {@code parentId} 降级为顶级、空 {@code sort} 降级为 0）。</p>
     *
     * @param parentId 新父菜单 ID（{@code null} 表示不修改）
     * @param name 新菜单名称（{@code null} 表示不修改）
     * @param path 前端路由路径（{@code null} 表示不修改）
     * @param component 前端组件路径（{@code null} 表示不修改）
     * @param icon 图标（{@code null} 表示不修改）
     * @param sort 新排序号（{@code null} 表示不修改）
     * @param menuType 新菜单类型（{@code null} 或空白表示不修改）
     * @param permissionCode 关联权限编码（{@code null} 表示不修改）
     * @param visible 新可见性（{@code null} 表示不修改）
     * @param status 新状态（{@code null} 表示不修改）
     */
    public void applyUpdate(Long parentId, String name, String path, String component, String icon, Integer sort,
                            String menuType, String permissionCode, Integer visible, Integer status) {
        if (parentId != null) {
            moveTo(parentId);
        }
        if (name != null) {
            rename(name);
        }
        if (path != null || component != null || icon != null) {
            changeRoute(path, component, icon);
        }
        if (sort != null) {
            changeSort(sort);
        }
        if (menuType != null && !menuType.isBlank()) {
            changeMenuType(menuType);
        }
        if (permissionCode != null) {
            bindPermissionCode(permissionCode);
        }
        if (visible != null) {
            changeVisible(visible);
        }
        if (status != null) {
            changeStatus(status);
        }
    }

    /**
     * 判定当前菜单是否为目录
     *
     * @return 目录返回 true，菜单返回 false
     */
    public boolean isDirectory() {
        return MENU_TYPE_DIR.equals(menuType);
    }

    /**
     * 获取菜单 ID
     *
     * @return 菜单 ID（新建且未落库时为 {@code null}）
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取所属租户 ID
     *
     * @return 所属租户 ID
     */
    public Long getTenantId() {
        return tenantId;
    }

    /**
     * 获取父菜单 ID
     *
     * @return 父菜单 ID（0 表示顶级）
     */
    public Long getParentId() {
        return parentId;
    }

    /**
     * 获取菜单名称
     *
     * @return 菜单名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取前端路由路径
     *
     * @return 前端路由路径
     */
    public String getPath() {
        return path;
    }

    /**
     * 获取前端组件路径
     *
     * @return 前端组件路径
     */
    public String getComponent() {
        return component;
    }

    /**
     * 获取图标
     *
     * @return 图标
     */
    public String getIcon() {
        return icon;
    }

    /**
     * 获取排序号
     *
     * @return 排序号
     */
    public int getSort() {
        return sort;
    }

    /**
     * 获取菜单类型
     *
     * @return 菜单类型（DIR / MENU）
     */
    public String getMenuType() {
        return menuType;
    }

    /**
     * 获取关联权限编码
     *
     * @return 关联权限编码
     */
    public String getPermissionCode() {
        return permissionCode;
    }

    /**
     * 获取可见性
     *
     * @return 可见性（1 显示 / 0 隐藏）
     */
    public int getVisible() {
        return visible;
    }

    /**
     * 获取状态
     *
     * @return 状态（1 启用 / 0 停用）
     */
    public int getStatus() {
        return status;
    }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
}
