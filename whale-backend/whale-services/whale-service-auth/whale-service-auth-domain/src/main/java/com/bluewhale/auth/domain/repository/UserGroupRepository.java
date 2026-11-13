package com.bluewhale.auth.domain.repository;

import com.bluewhale.auth.domain.aggregate.UserGroupAggregate;
import com.bluewhale.auth.domain.model.vo.GroupFilters;

import java.util.List;
import java.util.Optional;

/**
 * 用户组仓储端口（权限中心扩展 M3）：契约定义于领域层，由 infrastructure 适配实现。
 *
 * @author BlueWhale
 */
public interface UserGroupRepository {

    /**
     * 保存用户组聚合（无 ID 时新增，有 ID 时按主键更新）
     *
     * @param group 用户组聚合
     * @return 回填主键后的聚合
     */
    UserGroupAggregate save(UserGroupAggregate group);

    /**
     * 按主键查询用户组（当前租户内查询）
     *
     * @param id 主键
     * @return 用户组聚合；不存在时返回空
     */
    Optional<UserGroupAggregate> findById(Long id);

    /**
     * 判断组编码在租户内是否已存在（排除指定组自身，供重命名/编码校验）
     *
     * <p>并发说明：先查后插的友好预检，唯一性由 DB 约束 {@code uk_group_code} 兜底。</p>
     *
     * @param tenantId 租户 ID
     * @param groupCode 组编码
     * @param excludeGroupId 排除的组 ID（更新场景传自身主键，新建传 {@code null}）
     * @return 已存在返回 true
     */
    boolean existsByCode(Long tenantId, String groupCode, Long excludeGroupId);

    /**
     * 分页查询用户组（keyword 模糊匹配编码/名称，按 sortNo 升序）
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 关键字（可空）
     * @param category 类别过滤（可空）
     * @return 用户组分页结果
     */
    GroupPage page(long current, long size, String keyword, String category);

    /**
     * 删除用户组（逻辑删除并物理清理成员关系）
     *
     * @param id 组 ID
     */
    void deleteById(Long id);

    /**
     * 全量覆盖静态组成员（先清空后重建，成员来源标记 STATIC）
     *
     * <p>须在事务内调用；入参应先经聚合 {@code normalizeMembers} 归一化。</p>
     *
     * @param groupId 组 ID
     * @param memberUserIds 成员用户 ID 列表
     */
    void replaceMembers(Long groupId, List<Long> memberUserIds);

    /**
     * 查询组成员用户 ID 列表
     *
     * @param groupId 组 ID
     * @return 成员用户 ID 列表；无成员时返回空列表
     */
    List<Long> listMemberUserIds(Long groupId);

    /**
     * 查询租户内全部启用状态的用户组（供动态组成员求值）
     *
     * @param tenantId 租户 ID
     * @return 启用用户组列表
     */
    List<UserGroupAggregate> listEnabledByTenant(Long tenantId);

    /**
     * 查询用户所属的全部启用组 ID（静态关系 + 动态规则求值结果，供授权链路反查）
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 组 ID 列表；无归属时返回空列表
     */
    List<Long> listGroupIdsByUser(Long tenantId, Long userId);

    /**
     * 用规则求值结果全量重建动态组成员快照（成员来源标记 DYNAMIC）
     *
     * <p>须在事务内调用；仅作用于动态组，静态组调用方应使用 {@link #replaceMembers}。</p>
     *
     * @param groupId 组 ID
     * @param memberUserIds 求值出的成员用户 ID 列表
     */
    void replaceDynamicMembers(Long groupId, List<Long> memberUserIds);

    /**
     * 解析动态组过滤规则（JSON 反序列化为值对象；存储为空时返回空规则）
     *
     * @param filtersJson 过滤规则 JSON（可空）
     * @return 过滤规则值对象
     */
    GroupFilters parseFilters(String filtersJson);

    /**
     * 序列化过滤规则值对象为 JSON（应用层 VO 透传用）
     *
     * @param filters 过滤规则值对象
     * @return JSON 字符串；空规则返回 {@code null}
     */
    String serializeFilters(GroupFilters filters);

    /**
     * 用户组分页结果：领域层自有分页载体
     *
     * @param records 当前页用户组聚合列表
     * @param total 总记录数
     * @author BlueWhale
     */
    record GroupPage(List<UserGroupAggregate> records, long total) {
    }
}
