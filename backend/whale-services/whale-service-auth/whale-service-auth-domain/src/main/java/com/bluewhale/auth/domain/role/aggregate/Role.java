package com.bluewhale.auth.domain.role.aggregate;

import com.bluewhale.auth.domain.model.vo.RoleCode;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;

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
public class Role implements Entity<Long> {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;

    private final Long id;
    private final Long tenantId;
    private final RoleCode roleCode;
    private String roleName;
    private String description;
    private int sortNo;
    private int status;
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
    private Role(Long id, Long tenantId, RoleCode roleCode, String roleName, String description, int sortNo,
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
    public static Role create(Long tenantId, RoleCode roleCode, String roleName, String description, Integer sortNo,
                              Integer status) {
        LocalDateTime now = LocalDateTime.now();
        return new Role(null, tenantId, roleCode, roleName, description, sortNo == null ? 0 : sortNo,
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
    public static Role reconstitute(Long id, Long tenantId, RoleCode roleCode, String roleName, String description,
                                    Integer sortNo, Integer status, LocalDateTime createTime,
                                    LocalDateTime updateTime) {
        return new Role(id, tenantId, roleCode, roleName, description, sortNo == null ? 0 : sortNo,
                status == null ? STATUS_ENABLED : status, createTime, updateTime);
    }

    /**
     * 行为：重命名角色（对应更新接口的 {@code roleName} 赋值）
     *
     * @param roleName 新角色名称
     */
    public void rename(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "角色名称不能为空");
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
        this.status = status;
        this.updateTime = LocalDateTime.now();
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
}
