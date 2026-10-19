package com.bluewhale.auth.domain.user.repository;

import com.bluewhale.auth.domain.user.aggregate.User;

import java.util.List;
import java.util.Optional;

/**
 * User 聚合仓储端口（ADR-AUTH-004）：契约定义于领域层，由 {@code infrastructure} 适配实现。
 *
 * @author BlueWhale
 */
public interface UserRepository {

    /**
     * 保存用户聚合（无 ID 时新增，有 ID 时按主键更新）
     *
     * @param user 用户聚合根
     */
    void save(User user);

    /**
     * 按用户 ID 查询用户聚合
     *
     * @param id 用户 ID
     * @return 用户聚合；不存在时返回空
     */
    Optional<User> findById(Long id);

    /**
     * 按用户名查询用户聚合（原样匹配，不做格式前置校验，以保持登录链路对外行为不变）
     *
     * @param username 用户名
     * @return 用户聚合；不存在时返回空
     */
    Optional<User> findByUsername(String username);

    /**
     * 判断用户名是否已存在（供用户唯一性不变量跨聚合校验）
     *
     * @param username 用户名
     * @return 已存在返回 true，否则返回 false
     */
    boolean existsByUsername(String username);

    /**
     * 全量覆盖用户-角色关联（先清空后重建）
     *
     * @param userId 用户 ID
     * @param roleIds 目标角色 ID 列表
     */
    void replaceRoles(Long userId, List<Long> roleIds);
}
