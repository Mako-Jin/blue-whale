package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.auth.domain.model.vo.GroupFilters;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 用户组聚合根（对应 {@code sys_user_group} + {@code sys_user_group_member}，权限中心扩展 M3）。
 *
 * <p><b>不变量</b>：① {@code groupCode} 租户内唯一（仓储前置检查 + DB 唯一约束兜底）；
 * ② 组树不得成环（聚合内拒绝自指，跨聚合链路由应用层沿 {@code parentId} 校验）；
 * ③ {@code category=STATIC} 成员显式维护，{@code DYNAMIC} 成员由 {@code filters} 计算且
 * 不可手工增删。</p>
 *
 * <p>聚合根不持有成员集合（同 {@code RoleAggregate#assignPermissions} 口径）：
 * 成员落库由 {@code UserGroupRepository#replaceMembers} 端口承担。</p>
 *
 * @author BlueWhale
 */
public class UserGroupAggregate implements Entity<Long> {

    /** 组类别：静态组（成员显式维护） */
    public static final String CATEGORY_STATIC = "STATIC";
    /** 组类别：动态组（成员按 filters 规则计算） */
    public static final String CATEGORY_DYNAMIC = "DYNAMIC";
    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;
    /** 单组成员数量上限 */
    public static final int MAX_MEMBERS_PER_GROUP = 5000;

    private final Long id;
    private final Long tenantId;
    private final String groupCode;
    private String groupName;
    private Long parentId;
    private final String category;
    private GroupFilters filters;
    private String description;
    private int sortNo;
    private int status;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量
     *
     * @param id 主键（新建时为 {@code null}）
     * @param tenantId 租户 ID
     * @param groupCode 组编码
     * @param groupName 组名称
     * @param parentId 父组 ID（0 表示顶级）
     * @param category 组类别（STATIC / DYNAMIC）
     * @param filters 动态组过滤规则（静态组为 {@code null}）
     * @param description 描述
     * @param sortNo 排序号
     * @param status 状态（1 启用 / 0 停用）
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private UserGroupAggregate(Long id, Long tenantId, String groupCode, String groupName, Long parentId,
                               String category, GroupFilters filters, String description, int sortNo,
                               int status, LocalDateTime createTime, LocalDateTime updateTime) {
        validateBasics(tenantId, groupCode, groupName, status);
        validateCategoryAndFilters(category, filters);
        this.id = id;
        this.tenantId = tenantId;
        this.groupCode = groupCode.trim();
        this.groupName = groupName.trim();
        this.parentId = parentId == null ? 0L : parentId;
        this.category = category;
        this.filters = CATEGORY_DYNAMIC.equals(category) ? filters : null;
        this.description = description;
        this.sortNo = sortNo;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 校验基础字段不变量（归属、编码、名称、状态）
     *
     * @param tenantId 租户 ID
     * @param groupCode 组编码
     * @param groupName 组名称
     * @param status 状态
     */
    private static void validateBasics(Long tenantId, String groupCode, String groupName, int status) {
        if (tenantId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "用户组必须归属租户");
        }
        if (groupCode == null || groupCode.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "组编码不能为空");
        }
        if (groupName == null || groupName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "组名称不能为空");
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
    }

    /**
     * 校验组类别与过滤规则的匹配关系
     *
     * @param category 组类别（STATIC / DYNAMIC）
     * @param filters 过滤规则（可空）
     */
    private static void validateCategoryAndFilters(String category, GroupFilters filters) {
        if (!CATEGORY_STATIC.equals(category) && !CATEGORY_DYNAMIC.equals(category)) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "组类别非法，仅支持 STATIC / DYNAMIC");
        }
        if (CATEGORY_DYNAMIC.equals(category) && filters == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "动态组必须提供过滤规则");
        }
        if (CATEGORY_STATIC.equals(category) && filters != null && !filters.conditions().isEmpty()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "静态组不承载过滤规则");
        }
    }

    /**
     * 工厂方法：新建静态组
     *
     * @param tenantId 租户 ID
     * @param groupCode 组编码
     * @param groupName 组名称
     * @param parentId 父组 ID（0 表示顶级）
     * @param description 描述（可空）
     * @param sortNo 排序号（可空）
     * @return 静态组聚合
     */
    public static UserGroupAggregate createStatic(Long tenantId, String groupCode, String groupName,
                                                  Long parentId, String description, Integer sortNo) {
        LocalDateTime now = LocalDateTime.now();
        return new UserGroupAggregate(null, tenantId, groupCode, groupName, parentId, CATEGORY_STATIC,
                null, description, sortNo == null ? 0 : sortNo, STATUS_ENABLED, now, now);
    }

    /**
     * 工厂方法：新建动态组
     *
     * @param tenantId 租户 ID
     * @param groupCode 组编码
     * @param groupName 组名称
     * @param parentId 父组 ID（0 表示顶级）
     * @param filters 过滤规则
     * @param description 描述（可空）
     * @param sortNo 排序号（可空）
     * @return 动态组聚合
     */
    public static UserGroupAggregate createDynamic(Long tenantId, String groupCode, String groupName,
                                                   Long parentId, GroupFilters filters, String description,
                                                   Integer sortNo) {
        LocalDateTime now = LocalDateTime.now();
        return new UserGroupAggregate(null, tenantId, groupCode, groupName, parentId, CATEGORY_DYNAMIC,
                filters, description, sortNo == null ? 0 : sortNo, STATUS_ENABLED, now, now);
    }

    /**
     * 由持久化数据重建聚合
     *
     * @param id 主键
     * @param tenantId 租户 ID
     * @param groupCode 组编码
     * @param groupName 组名称
     * @param parentId 父组 ID
     * @param category 组类别
     * @param filters 过滤规则（静态组为 {@code null}）
     * @param description 描述
     * @param sortNo 排序号
     * @param status 状态
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 用户组聚合
     */
    public static UserGroupAggregate reconstitute(Long id, Long tenantId, String groupCode, String groupName,
                                                  Long parentId, String category, GroupFilters filters,
                                                  String description, Integer sortNo, Integer status,
                                                  LocalDateTime createTime, LocalDateTime updateTime) {
        return new UserGroupAggregate(id, tenantId, groupCode, groupName, parentId, category, filters,
                description, sortNo == null ? 0 : sortNo,
                status == null ? STATUS_ENABLED : status, createTime, updateTime);
    }

    /**
     * 行为：重命名组
     *
     * @param groupName 新组名称
     */
    public void rename(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "组名称不能为空");
        }
        this.groupName = groupName.trim();
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：挂载到新父组（聚合内拒绝自指；跨组链路成环检测由应用层沿 {@code parentId} 完成）
     *
     * @param newParentId 新父组 ID（0 表示挂为顶级）
     * @throws DomainException 自指时抛出
     */
    public void changeParent(Long newParentId) {
        long target = newParentId == null ? 0L : newParentId;
        if (id != null && target == id) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "父组不能是自身（组树不得成环）");
        }
        this.parentId = target;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：更新动态组过滤规则（静态组拒绝）
     *
     * @param newFilters 新过滤规则
     * @throws DomainException 静态组调用时抛出
     */
    public void changeFilters(GroupFilters newFilters) {
        if (!isDynamic()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "静态组不承载过滤规则");
        }
        this.filters = newFilters;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：修改描述
     *
     * @param description 新描述
     */
    public void changeDescription(String description) {
        this.description = description;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：修改排序号
     *
     * @param sortNo 新排序号
     */
    public void changeSortNo(int sortNo) {
        this.sortNo = sortNo;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更状态
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
     * 归一化本次要写入静态组的成员集合（去重、去空、数量上限校验）
     *
     * <p>聚合根不持有成员集合；本方法负责在应用层落库前完成领域侧校验，
     * 返回值应作为 {@code replaceMembers} 落库入参。动态组拒绝手工维护成员。</p>
     *
     * @param memberUserIds 待写入的成员用户 ID 集合（允许空集合表示清空成员）
     * @return 去重去空后的成员用户 ID 列表
     * @throws DomainException 集合为 {@code null}、超量或动态组调用时抛出
     */
    public List<Long> normalizeMembers(Collection<Long> memberUserIds) {
        if (isDynamic()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "动态组成员由规则计算，不可手工维护");
        }
        if (memberUserIds == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "成员列表不能为空");
        }
        List<Long> distinct = memberUserIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.size() > MAX_MEMBERS_PER_GROUP) {
            throw new DomainException(ErrorCode.PARAM_ERROR,
                    "单组最多 " + MAX_MEMBERS_PER_GROUP + " 个成员");
        }
        return distinct;
    }

    /**
     * 判断是否动态组
     *
     * @return 动态组返回 {@code true}
     */
    public boolean isDynamic() {
        return CATEGORY_DYNAMIC.equals(category);
    }

    /**
     * 判断是否启用
     *
     * @return 启用返回 {@code true}
     */
    public boolean isEnabled() {
        return status == STATUS_ENABLED;
    }

    /**
     * 获取主键
     *
     * @return 主键（新建时为 {@code null}）
     */
    @Override
    public Long getId() {
        return id;
    }

    /**
     * 获取租户 ID
     *
     * @return 租户 ID
     */
    public Long getTenantId() {
        return tenantId;
    }

    /**
     * 获取组编码
     *
     * @return 组编码
     */
    public String getGroupCode() {
        return groupCode;
    }

    /**
     * 获取组名称
     *
     * @return 组名称
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * 获取父组 ID
     *
     * @return 父组 ID（0 表示顶级）
     */
    public Long getParentId() {
        return parentId;
    }

    /**
     * 获取组类别
     *
     * @return STATIC / DYNAMIC
     */
    public String getCategory() {
        return category;
    }

    /**
     * 获取过滤规则
     *
     * @return 过滤规则（静态组为 {@code null}）
     */
    public GroupFilters getFilters() {
        return filters;
    }

    /**
     * 获取描述
     *
     * @return 描述（可空）
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
