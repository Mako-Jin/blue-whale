package com.bluewhale.auth.domain.user.repository;

import com.bluewhale.auth.domain.user.aggregate.User;

import java.util.Collection;
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

    /**
     * 查询用户已分配的角色 ID 列表
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    List<Long> listRoleIds(Long userId);

    /**
     * 分页查询用户（{@code keyword} 模糊匹配用户名/昵称/手机号，按创建时间降序）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 用户名/昵称/手机号模糊匹配关键字（可空）
     * @return 用户分页结果
     */
    UserPage page(long current, long size, String keyword);

    /**
     * 批量查询「启用」状态的用户 ID（供服务间成员合法性校验使用）
     *
     * @param ids 待校验用户 ID 集合（调用方保证去重）
     * @return 存在且启用的用户 ID 列表；入参为空时返回空列表
     */
    List<Long> findEnabledIds(Collection<Long> ids);

    /**
     * 删除用户（逻辑删除用户并按物理删除清理用户-角色关联）
     *
     * @param id 用户 ID
     */
    void deleteById(Long id);

    /**
     * 用户分页结果：领域层自有分页载体，避免向领域层泄漏 MyBatis 分页类型。
     *
     * @param records 当前页用户聚合列表
     * @param total 总记录数
     * @author BlueWhale
     */
    record UserPage(List<User> records, long total) {
    }
}
