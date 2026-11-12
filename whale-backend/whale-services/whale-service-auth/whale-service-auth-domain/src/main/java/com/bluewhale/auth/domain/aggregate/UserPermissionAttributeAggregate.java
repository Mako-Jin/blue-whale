package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

/**
 * 用户 ABAC 属性小聚合（对应 {@code sys_user_permission}，REQ-ARCH-001 / T-DDD-9）。
 *
 * <p>聚合根标识为 {@link UserPermissionKeyAggregate}（{@code (userId, permissionId, attrKey)} 三元组）：
 * 为「用户 + 权限点」附加属性键值对，供鉴权时做数据范围过滤。<b>不变量</b>：① {@code userId}/{@code permissionId} 非空；
 * ② {@code attrKey} 非空。</p>
 *
 * <p><b>双标识说明</b>：{@link #getId()} 返回业务标识（三元组），{@link #getRowId()} 返回持久化自增主键
 * （仅供仓储映射，新建未落库时为 {@code null}），两者不可混用。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。</p>
 *
 * @author BlueWhale
 */
public class UserPermissionAttributeAggregate implements Entity<UserPermissionKeyAggregate> {

    private final Long id;
    private final Long userId;
    private final Long permissionId;
    private final String attrKey;
    private final String attrValue;

    /**
     * 私有构造器：集中校验聚合不变量
     *
     * @param id 属性 ID（新建时为 {@code null}）
     * @param userId 用户 ID
     * @param permissionId 权限点 ID
     * @param attrKey 属性键
     * @param attrValue 属性值
     */
    private UserPermissionAttributeAggregate(Long id, Long userId, Long permissionId, String attrKey, String attrValue) {
        if (userId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "用户 ID 不能为空");
        }
        if (permissionId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "权限点 ID 不能为空");
        }
        if (attrKey == null || attrKey.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "属性键不能为空");
        }
        this.id = id;
        this.userId = userId;
        this.permissionId = permissionId;
        this.attrKey = attrKey;
        this.attrValue = attrValue;
    }

    /**
     * 工厂方法：新建属性（无 ID）
     *
     * @param userId 用户 ID
     * @param permissionId 权限点 ID
     * @param attrKey 属性键
     * @param attrValue 属性值
     * @return 新建的属性聚合根
     */
    public static UserPermissionAttributeAggregate create(Long userId, Long permissionId, String attrKey, String attrValue) {
        return new UserPermissionAttributeAggregate(null, userId, permissionId, attrKey, attrValue);
    }

    /**
     * 重建方法：由持久化数据还原属性聚合根
     *
     * @param id 属性 ID
     * @param userId 用户 ID
     * @param permissionId 权限点 ID
     * @param attrKey 属性键
     * @param attrValue 属性值
     * @return 还原的属性聚合根
     */
    public static UserPermissionAttributeAggregate reconstitute(Long id, Long userId, Long permissionId,
                                                                String attrKey, String attrValue) {
        return new UserPermissionAttributeAggregate(id, userId, permissionId, attrKey, attrValue);
    }

    /**
     * 获取聚合业务标识（三元组）
     *
     * @return 业务标识 {@code (userId, permissionId, attrKey)}
     */
    @Override
    public UserPermissionKeyAggregate getId() {
        return new UserPermissionKeyAggregate(userId, permissionId, attrKey);
    }

    /**
     * 获取持久化自增主键（仅供仓储映射使用，非业务标识）
     *
     * @return 关联记录 ID（新建且未落库时为 {@code null}）
     */
    public Long getRowId() {
        return id;
    }

    /**
     * 获取用户 ID
     *
     * @return 用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 获取权限点 ID
     *
     * @return 权限点 ID
     */
    public Long getPermissionId() {
        return permissionId;
    }

    /**
     * 获取属性键
     *
     * @return 属性键
     */
    public String getAttrKey() {
        return attrKey;
    }

    /**
     * 获取属性值
     *
     * @return 属性值
     */
    public String getAttrValue() {
        return attrValue;
    }
}
