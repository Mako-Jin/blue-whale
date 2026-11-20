package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.auth.domain.model.AuthRoleCodes;
import com.bluewhale.auth.domain.model.vo.InheritStrategy;
import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Role 聚合根（对应 {@code sys_role}，REQ-ARCH-001 / T-DDD-5）。
 *
 * <p><b>不变量</b>：① {@code roleCode} 非空且格式合法（由 {@link RoleCode} 值对象承载，
 * 其全局唯一性由仓储跨聚合校验）；② {@code roleName} 非空；③ {@code status ∈ {1 启用, 0 停用}}。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。写路径接入留待 AUTH-17。</p>
 *
 * @author BlueWhale
 */
public class RoleAggregate implements Entity<Long> {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;
    /** 单角色可分配权限点数量上限（防止误传超大集合导致关联表爆炸） */
    public static final int MAX_PERMISSIONS_PER_ROLE = 500;

    private final Long id;
    private final Long tenantId;
    private final RoleCode roleCode;
    private String roleName;
    private String description;
    private int sortNo;
    private int status;
    /** 父角色 ID（0 表示顶级，继承链不得成环——跨聚合链路由应用层校验，聚合内拒绝自指） */
    private Long parentId;
    /** 角色层级（顶级为 1，子角色只能由父角色 +1） */
    private int roleLevel;
    /** 继承策略 */
    private InheritStrategy inheritStrategy;
    /** 角色分类（GENERAL / BUSINESS / SYSTEM） */
    private String category;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量
     *
     * @param id 角色 ID（新建时为 {@code null}）
     * @param tenantId 所属租户 ID
     * @param roleCode 角色编码值对象
     * @param roleName 角色名称
     * @param description 角色描述
     * @param sortNo 排序号
     * @param status 状态（1 启用 / 0 停用）
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private RoleAggregate(Long id, Long tenantId, RoleCode roleCode, String roleName, String description, int sortNo,
                          int status, LocalDateTime createTime, LocalDateTime updateTime) {
        if (tenantId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "角色所属租户不能为空");
        }
        if (roleCode == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "角色编码不能为空");
        }
        if (roleName == null || roleName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "角色名称不能为空");
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.description = description;
        this.sortNo = sortNo;
        this.status = status;
        this.parentId = 0L;
        this.roleLevel = 1;
        this.inheritStrategy = InheritStrategy.NONE;
        this.category = "GENERAL";
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：新建角色（无 ID），{@code sortNo}/{@code status} 为空时取默认（0 / 启用）
     *
     * @param tenantId 所属租户 ID
     * @param roleCode 角色编码值对象
     * @param roleName 角色名称
     * @param description 角色描述
     * @param sortNo 排序号，为空时按 0 处理
     * @param status 状态（1 启用 / 0 停用），为空时按启用处理
     * @return 新建的角色聚合根
     */
    public static RoleAggregate create(Long tenantId, RoleCode roleCode, String roleName, String description, Integer sortNo,
                                       Integer status) {
        LocalDateTime now = LocalDateTime.now();
        return new RoleAggregate(null, tenantId, roleCode, roleName, description, sortNo == null ? 0 : sortNo,
                status == null ? STATUS_ENABLED : status, now, now);
    }

    /**
     * 重建方法：由持久化数据还原角色聚合根
     *
     * @param id 角色 ID
     * @param tenantId 所属租户 ID
     * @param roleCode 角色编码值对象
     * @param roleName 角色名称
     * @param description 角色描述
     * @param sortNo 排序号，为空时按 0 处理
     * @param status 状态（1 启用 / 0 停用），为空时按启用处理
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 还原的角色聚合根
     */
    public static RoleAggregate reconstitute(Long id, Long tenantId, RoleCode roleCode, String roleName, String description,
                                             Integer sortNo, Integer status, LocalDateTime createTime,
                                             LocalDateTime updateTime) {
        return new RoleAggregate(id, tenantId, roleCode, roleName, description, sortNo == null ? 0 : sortNo,
                status == null ? STATUS_ENABLED : status, createTime, updateTime);
    }

    /**
     * 行为：重命名角色（对应更新接口的 {@code roleName} 赋值）
     *
     * <p>内置角色（含三员）的名称是职责标识，改名会让「谁在什么岗位上」失去可追溯性，
     * 因此一律拒绝；描述与排序仍允许调整。</p>
     *
     * @param roleName 新角色名称
     * @throws DomainException 内置角色改名时抛出
     */
    public void rename(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "角色名称不能为空");
        }
        // 同值提交（前端编辑描述时原样带出名称）放行，真正改名才拒绝
        if (isBuiltIn() && !roleName.equals(this.roleName)) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "内置角色不允许改名：" + roleCode.value());
        }
        this.roleName = roleName;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：修改角色描述（对应更新接口的 {@code description} 赋值）
     *
     * @param description 新角色描述
     */
    public void changeDescription(String description) {
        this.description = description;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：修改排序号（对应更新接口的 {@code sortNo} 赋值）
     *
     * @param sortNo 新排序号
     */
    public void changeSortNo(int sortNo) {
        this.sortNo = sortNo;
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
        // 三员角色停用等于让职责分离岗位失效（审计员被停用后无人可查审计），一律拒绝
        if (isThreeAdmin() && status == STATUS_DISABLED && this.status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "三员角色不允许停用：" + roleCode.value());
        }
        this.status = status;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 判断当前角色是否内置角色（{@code super_admin} / {@code tenant_admin} / 三员）
     *
     * <p>内置角色不可删除、不可改名，权限集与启用状态受额外保护。</p>
     *
     * @return 内置角色返回 {@code true}
     */
    public boolean isBuiltIn() {
        return AuthRoleCodes.isBuiltIn(roleCode.value());
    }

    /**
     * 判断当前角色是否三员之一（系统管理员 / 安全管理员 / 安全审计员）
     *
     * @return 属于三员返回 {@code true}
     */
    public boolean isThreeAdmin() {
        return AuthRoleCodes.isThreeAdmin(roleCode.value());
    }

    /**
     * 行为：启用当前角色
     */
    public void enable() {
        changeStatus(STATUS_ENABLED);
    }

    /**
     * 行为：停用当前角色
     */
    public void disable() {
        changeStatus(STATUS_DISABLED);
    }

    /**
     * 判定当前角色是否启用
     *
     * @return 启用返回 true，停用返回 false
     */
    public boolean isEnabled() {
        return status == STATUS_ENABLED;
    }

    /**
     * 行为：归一化本次要分配给角色的权限点集合（去重、去空、数量上限校验）
     *
     * <p>聚合根不持有权限关联集合，关联落库由 {@code RoleRepository#replacePermissions} 端口承担；
     * 本方法负责在应用层落库前完成领域侧校验与归一化，返回值应作为落库入参。</p>
     *
     * @param permissionIds 待分配的权限点 ID 集合（允许为空集合，表示清空全部权限）
     * @return 去重去空后的权限点 ID 列表（应作为 {@code replacePermissions} 落库入参）
     * @throws DomainException 集合为 {@code null} 或超过 {@link #MAX_PERMISSIONS_PER_ROLE} 时抛出参数错误
     */
    public List<Long> assignPermissions(Collection<Long> permissionIds) {
        if (permissionIds == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "权限点 ID 列表不能为空");
        }
        List<Long> distinct = permissionIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.size() > MAX_PERMISSIONS_PER_ROLE) {
            throw new DomainException(ErrorCode.PARAM_ERROR,
                    "单角色最多分配 " + MAX_PERMISSIONS_PER_ROLE + " 个权限点");
        }
        return distinct;
    }

    /**
     * 获取角色 ID
     *
     * @return 角色 ID（新建且未落库时为 {@code null}）
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
     * 获取角色编码值对象
     *
     * @return 角色编码值对象
     */
    public RoleCode getRoleCode() {
        return roleCode;
    }

    /**
     * 获取角色名称
     *
     * @return 角色名称
     */
    public String getRoleName() {
        return roleName;
    }

    /**
     * 获取角色描述
     *
     * @return 角色描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取排序号
     *
     * @return 排序号
     */
    public int getSortNo() {
        return sortNo;
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

    /**
     * 由持久化数据还原角色聚合根（含继承扩展字段，权限中心扩展 M3）
     *
     * @param id 角色 ID
     * @param tenantId 所属租户 ID
     * @param roleCode 角色编码值对象
     * @param roleName 角色名称
     * @param description 角色描述
     * @param sortNo 排序号
     * @param status 状态（1 启用 / 0 停用）
     * @param parentId 父角色 ID（0 表示顶级）
     * @param roleLevel 角色层级
     * @param inheritStrategy 继承策略字符串（FULL / INCREMENT / OVERRIDE / NONE，空值回落 NONE）
     * @param category 角色分类（空值回落 GENERAL）
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 还原的角色聚合根
     */
    public static RoleAggregate reconstituteWithHierarchy(Long id, Long tenantId, RoleCode roleCode,
                                                          String roleName, String description, Integer sortNo,
                                                          Integer status, Long parentId, Integer roleLevel,
                                                          String inheritStrategy, String category,
                                                          LocalDateTime createTime, LocalDateTime updateTime) {
        RoleAggregate role = reconstitute(id, tenantId, roleCode, roleName, description, sortNo, status,
                createTime, updateTime);
        role.parentId = parentId == null ? 0L : parentId;
        role.roleLevel = roleLevel == null || roleLevel < 1 ? 1 : roleLevel;
        role.inheritStrategy = InheritStrategy.of(inheritStrategy);
        role.category = category == null || category.isBlank() ? "GENERAL" : category.trim();
        return role;
    }

    /**
     * 行为：挂载到父角色（继承扩展 M3）。
     *
     * <p>聚合内不变量：拒绝自指、层级只能由父角色 +1；父角色存在性与「继承链不成环」
     * 跨聚合校验由应用层沿 {@code parentId} 链完成（{@code RoleRepository#findById}）。</p>
     *
     * @param parent 父角色聚合
     * @throws DomainException 自指或父角色为空时抛出
     */
    public void attachParent(RoleAggregate parent) {
        if (parent == null || parent.getId() == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "父角色不能为空");
        }
        if (id != null && id.equals(parent.getId())) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "父角色不能是自身（继承链不得成环）");
        }
        this.parentId = parent.getId();
        this.roleLevel = parent.roleLevel + 1;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：解除父角色（回到顶级，层级重置为 1）
     */
    public void detachParent() {
        this.parentId = 0L;
        this.roleLevel = 1;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更继承策略
     *
     * @param strategy 继承策略字符串（FULL / INCREMENT / OVERRIDE / NONE）
     */
    public void changeInheritStrategy(String strategy) {
        this.inheritStrategy = InheritStrategy.of(strategy);
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更角色分类
     *
     * @param category 分类（GENERAL / BUSINESS / SYSTEM）
     */
    public void changeCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "角色分类不能为空");
        }
        this.category = category.trim();
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 判断是否顶级角色
     *
     * @return 顶级（无父角色）返回 {@code true}
     */
    public boolean isTopLevel() {
        return parentId == null || parentId == 0L;
    }

    /**
     * 获取父角色 ID
     *
     * @return 父角色 ID（0 表示顶级）
     */
    public Long getParentId() {
        return parentId;
    }

    /**
     * 获取角色层级
     *
     * @return 层级（顶级为 1）
     */
    public int getRoleLevel() {
        return roleLevel;
    }

    /**
     * 获取继承策略
     *
     * @return 继承策略枚举
     */
    public InheritStrategy getInheritStrategy() {
        return inheritStrategy;
    }

    /**
     * 获取角色分类
     *
     * @return 分类编码
     */
    public String getCategory() {
        return category;
    }
}
