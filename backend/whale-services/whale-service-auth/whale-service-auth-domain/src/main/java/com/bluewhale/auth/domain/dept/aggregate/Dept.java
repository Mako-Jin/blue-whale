package com.bluewhale.auth.domain.dept.aggregate;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

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
public class Dept {

    /** 顶级部门的父 ID */
    public static final long ROOT_PARENT_ID = 0L;

    private final Long id;
    private final Long tenantId;
    private Long parentId;
    private String deptName;
    private int sort;
    private String leader;
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
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private Dept(Long id, Long tenantId, Long parentId, String deptName, int sort, String leader,
                 LocalDateTime createTime, LocalDateTime updateTime) {
        if (tenantId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "部门所属租户不能为空");
        }
        if (parentId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "父部门 ID 不能为空（0 表示顶级）");
        }
        if (deptName == null || deptName.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "部门名称不能为空");
        }
        this.id = id;
        this.tenantId = tenantId;
        this.parentId = parentId;
        this.deptName = deptName;
        this.sort = sort;
        this.leader = leader;
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
        LocalDateTime now = LocalDateTime.now();
        return new Dept(null, tenantId, parentId == null ? ROOT_PARENT_ID : parentId, deptName,
                sort == null ? 0 : sort, leader, now, now);
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
        return new Dept(id, tenantId, parentId == null ? ROOT_PARENT_ID : parentId, deptName,
                sort == null ? 0 : sort, leader, createTime, updateTime);
    }

    /**
     * 行为：移动到新的父部门（对应更新接口的 {@code parentId} 赋值）
     *
     * @param parentId 新父部门 ID（0 表示顶级）
     */
    public void moveTo(Long parentId) {
        if (parentId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "父部门 ID 不能为空（0 表示顶级）");
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
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "部门名称不能为空");
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
     * 行为：一次性应用更新接口的全部字段（空值默认与 {@link #create} 保持一致）
     *
     * @param parentId 父部门 ID，为空时按 0（顶级）处理
     * @param deptName 部门名称
     * @param sort 排序号，为空时按 0 处理
     * @param leader 负责人
     */
    public void applyChanges(Long parentId, String deptName, Integer sort, String leader) {
        moveTo(parentId == null ? ROOT_PARENT_ID : parentId);
        rename(deptName);
        changeSort(sort == null ? 0 : sort);
        changeLeader(leader);
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
