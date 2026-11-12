package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;

/**
 * Dept 聚合根（对应 {@code sys_dept}，REQ-ARCH-001 / T-DDD-7）。
 *
 * <p><b>不变量</b>：① {@code parentId} 非空（{@code 0} 表示顶级部门）；② {@code deptName} 非空。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。写路径接入留待 AUTH-17。</p>
 *
 * @author BlueWhale
 */
public class Dept implements Entity<Long> {

    /** 顶级部门的父 ID */
    public static final long ROOT_PARENT_ID = 0L;

    private final Long id;
    private final Long tenantId;
    private Long parentId;
    private String deptName;
    private int sort;
    /** 负责人姓名（展示快照：与 {@code leaderUserId} 指向的用户改名后可能不同步，仅作展示用，权威关联以 {@code leaderUserId} 为准） */
    private String leader;
    /** 负责人用户 ID（逻辑关联 {@code sys_user.id}，允许为空） */
    private Long leaderUserId;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量
     *
     * @param id 部门 ID（新建时为 {@code null}）
     * @param tenantId 所属租户 ID
     * @param parentId 父部门 ID（0 表示顶级）
     * @param deptName 部门名称
     * @param sort 排序号
     * @param leader 负责人
     * @param leaderUserId 负责人用户 ID（允许为空）
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private Dept(Long id, Long tenantId, Long parentId, String deptName, int sort, String leader,
                 Long leaderUserId, LocalDateTime createTime, LocalDateTime updateTime) {
        if (tenantId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "部门所属租户不能为空");
        }
        if (parentId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "父部门 ID 不能为空（0 表示顶级）");
        }
        if (deptName == null || deptName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "部门名称不能为空");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.parentId = parentId;
        this.deptName = deptName;
        this.sort = sort;
        this.leader = leader;
        this.leaderUserId = leaderUserId;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：新建部门（无 ID），{@code parentId}/{@code sort} 为空时取默认（0 / 0）
     *
     * @param tenantId 所属租户 ID
     * @param parentId 父部门 ID，为空时按 0（顶级）处理
     * @param deptName 部门名称
     * @param sort 排序号，为空时按 0 处理
     * @param leader 负责人
     * @return 新建的部门聚合根
     */
    public static Dept create(Long tenantId, Long parentId, String deptName, Integer sort, String leader) {
        return create(tenantId, parentId, deptName, sort, leader, null);
    }

    /**
     * 工厂方法（含负责人用户 ID）：新建部门（无 ID），{@code parentId}/{@code sort} 为空时取默认（0 / 0）
     *
     * @param tenantId 所属租户 ID
     * @param parentId 父部门 ID，为空时按 0（顶级）处理
     * @param deptName 部门名称
     * @param sort 排序号，为空时按 0 处理
     * @param leader 负责人
     * @param leaderUserId 负责人用户 ID（允许为空）
     * @return 新建的部门聚合根
     */
    public static Dept create(Long tenantId, Long parentId, String deptName, Integer sort, String leader,
                              Long leaderUserId) {
        LocalDateTime now = LocalDateTime.now();
        return new Dept(null, tenantId, parentId == null ? ROOT_PARENT_ID : parentId, deptName,
                sort == null ? 0 : sort, leader, leaderUserId, now, now);
    }

    /**
     * 重建方法：由持久化数据还原部门聚合根
     *
     * @param id 部门 ID
     * @param tenantId 所属租户 ID
     * @param parentId 父部门 ID，为空时按 0（顶级）处理
     * @param deptName 部门名称
     * @param sort 排序号，为空时按 0 处理
     * @param leader 负责人
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 还原的部门聚合根
     */
    public static Dept reconstitute(Long id, Long tenantId, Long parentId, String deptName, Integer sort,
                                    String leader, LocalDateTime createTime, LocalDateTime updateTime) {
        return reconstitute(id, tenantId, parentId, deptName, sort, leader, null, createTime, updateTime);
    }

    /**
     * 重建方法（含负责人用户 ID）：由持久化数据还原部门聚合根
     *
     * @param id 部门 ID
     * @param tenantId 所属租户 ID
     * @param parentId 父部门 ID，为空时按 0（顶级）处理
     * @param deptName 部门名称
     * @param sort 排序号，为空时按 0 处理
     * @param leader 负责人
     * @param leaderUserId 负责人用户 ID（允许为空）
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 还原的部门聚合根
     */
    public static Dept reconstitute(Long id, Long tenantId, Long parentId, String deptName, Integer sort,
                                    String leader, Long leaderUserId, LocalDateTime createTime,
                                    LocalDateTime updateTime) {
        return new Dept(id, tenantId, parentId == null ? ROOT_PARENT_ID : parentId, deptName,
                sort == null ? 0 : sort, leader, leaderUserId, createTime, updateTime);
    }

    /**
     * 行为：移动到新的父部门（对应更新接口的 {@code parentId} 赋值）
     *
     * @param parentId 新父部门 ID（0 表示顶级）
     */
    public void moveTo(Long parentId) {
        if (parentId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "父部门 ID 不能为空（0 表示顶级）");
        }
        this.parentId = parentId;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：重命名部门（对应更新接口的 {@code deptName} 赋值）
     *
     * @param deptName 新部门名称
     */
    public void rename(String deptName) {
        if (deptName == null || deptName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "部门名称不能为空");
        }
        this.deptName = deptName;
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
     * 行为：修改负责人（对应更新接口的 {@code leader} 赋值）
     *
     * @param leader 新负责人
     */
    public void changeLeader(String leader) {
        this.leader = leader;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：修改负责人用户 ID（对应管理端维护 {@code leaderUserId} 赋值）
     *
     * @param leaderUserId 新负责人用户 ID（允许为空）
     */
    public void changeLeaderUser(Long leaderUserId) {
        this.leaderUserId = leaderUserId;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：一次性应用更新接口的全部字段（PATCH 契约）
     *
     * <p><b>注意</b>：本重载不修改 {@code leaderUserId}，避免未携带该字段的旧调用方
     * 无意清空已维护的负责人用户 ID。</p>
     *
     * @param parentId 新父部门 ID（{@code null} 表示不修改）
     * @param deptName 新部门名称（{@code null} 表示不修改）
     * @param sort 新排序号（{@code null} 表示不修改）
     * @param leader 新负责人（{@code null} 表示不修改）
     */
    public void applyUpdate(Long parentId, String deptName, Integer sort, String leader) {
        applyUpdate(parentId, deptName, sort, leader, null);
    }

    /**
     * 行为：一次性应用更新接口的全部字段（含负责人用户 ID，PATCH 契约）
     *
     * <p><b>PATCH 契约</b>：所有参数统一 {@code null = 不修改}（保持原值），不做任何隐式默认值
     * 降级；需要清空 {@code leaderUserId} 时显式调用 {@link #changeLeaderUser(Long)} 传 {@code null}。</p>
     *
     * @param parentId 新父部门 ID（{@code null} 表示不修改）
     * @param deptName 新部门名称（{@code null} 表示不修改）
     * @param sort 新排序号（{@code null} 表示不修改）
     * @param leader 新负责人（{@code null} 表示不修改）
     * @param leaderUserId 新负责人用户 ID（{@code null} 表示不修改）
     */
    public void applyUpdate(Long parentId, String deptName, Integer sort, String leader, Long leaderUserId) {
        if (parentId != null) {
            moveTo(parentId);
        }
        if (deptName != null) {
            rename(deptName);
        }
        if (sort != null) {
            changeSort(sort);
        }
        if (leader != null) {
            changeLeader(leader);
        }
        if (leaderUserId != null) {
            changeLeaderUser(leaderUserId);
        }
    }

    /**
     * 获取部门 ID
     *
     * @return 部门 ID（新建且未落库时为 {@code null}）
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
     * 获取父部门 ID
     *
     * @return 父部门 ID（0 表示顶级）
     */
    public Long getParentId() {
        return parentId;
    }

    /**
     * 获取部门名称
     *
     * @return 部门名称
     */
    public String getDeptName() {
        return deptName;
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
     * 获取负责人
     *
     * @return 负责人
     */
    public String getLeader() {
        return leader;
    }

    /**
     * 获取负责人用户 ID
     *
     * @return 负责人用户 ID；未维护时为 {@code null}
     */
    public Long getLeaderUserId() {
        return leaderUserId;
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
