package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.UserProfileAggregate;

import java.util.Optional;

/**
 * 用户身份档案仓储端口（权限中心扩展 M3）：契约定义于领域层，由 infrastructure 适配实现。
 *
 * <p>子表只允许通过聚合根整体替换：{@code save} 以 {@code userId} 为准做
 * 「无档案则建、有则更新 + 全量重建子表」的级联写。</p>
 *
 * @author BlueWhale
 */
public interface UserProfileRepository {

    /**
     * 保存档案聚合（无 ID 时按 userId 新建，有 ID 时按主键更新并整体重建子表）
     *
     * <p>须在事务内调用；子表替换策略为「先物理删后批量插」。</p>
     *
     * @param profile 档案聚合（子集合已通过聚合行为整体替换）
     * @return 回填主键后的聚合
     */
    UserProfileAggregate save(UserProfileAggregate profile);

    /**
     * 按用户 ID 查询档案（含全部子集合）
     *
     * @param userId 用户 ID
     * @return 档案聚合；不存在时返回空
     */
    Optional<UserProfileAggregate> findByUserId(Long userId);

    /**
     * 判断用户是否已建档案
     *
     * @param userId 用户 ID
     * @return 已建返回 true
     */
    boolean existsByUserId(Long userId);

    /**
     * 删除档案（逻辑删除主表，物理清理子表）
     *
     * @param userId 用户 ID
     */
    void deleteByUserId(Long userId);
}
