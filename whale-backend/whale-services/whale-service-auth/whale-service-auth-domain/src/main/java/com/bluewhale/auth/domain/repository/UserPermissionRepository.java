package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.UserPermissionAttributeAggregate;

import java.util.List;

/**
 * 用户 ABAC 属性聚合仓储端口（REQ-ARCH-001 / T-DDD-9）：契约定义于领域层，由 {@code infrastructure} 适配实现。
 *
 * @author BlueWhale
 */
public interface UserPermissionRepository {

    /**
     * 查询指定用户的全部 ABAC 属性
     *
     * @param userId 用户 ID
     * @return 属性聚合列表
     */
    List<UserPermissionAttributeAggregate> listByUserId(Long userId);

    /**
     * 全量覆盖用户 ABAC 属性（先物理清空后批量重建，避免唯一键冲突）
     *
     * @param userId 用户 ID
     * @param attributes 目标属性聚合列表（可为空，为空时仅清空）
     */
    void replaceAll(Long userId, List<UserPermissionAttributeAggregate> attributes);
}
