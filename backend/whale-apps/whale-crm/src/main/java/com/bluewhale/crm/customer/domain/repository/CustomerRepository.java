package com.bluewhale.crm.customer.domain.repository;

import com.bluewhale.crm.customer.domain.model.Customer;
import com.bluewhale.crm.customer.domain.model.CustomerQuery;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 客户聚合仓储。
 *
 * @author BlueWhale
 */
public interface CustomerRepository {

    /**
     * 分页查询客户。
     *
     * @param query 查询条件
     * @return 客户列表
     */
    List<Customer> page(CustomerQuery query);

    /**
     * 统计客户数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(CustomerQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 客户
     */
    Optional<Customer> findById(Long id);

    /**
     * 按编码查询。
     *
     * @param code 客户编码
     * @return 客户
     */
    Optional<Customer> findByCode(String code);

    /**
     * 按归一化名称 / 税号 / 域名查询硬冲突候选。
     *
     * @param normalizedName 归一化名称
     * @param normalizedTaxNo 归一化税号
     * @param normalizedDomain 归一化域名
     * @param excludeId 排除的客户 ID（编辑时排除自身）
     * @return 候选客户
     */
    List<Customer> findHardDuplicates(String normalizedName, String normalizedTaxNo,
                                      String normalizedDomain, Long excludeId);

    /**
     * 查询指定前缀下最大的客户编码。
     *
     * @param prefix 编码前缀
     * @return 最大编码
     */
    Optional<String> findMaxCode(String prefix);

    /**
     * 新增客户。
     *
     * @param customer 客户
     */
    void insert(Customer customer);

    /**
     * 更新客户。
     *
     * @param customer 客户
     */
    void update(Customer customer);

    /**
     * 逻辑删除客户。
     *
     * @param id 客户 ID
     */
    void delete(Long id);

    /**
     * 原子领取：仅当客户在公海或负责人为空时成功。
     *
     * @param id 客户 ID
     * @param ownerId 领取人
     * @param at 领取时间
     * @return 更新成功返回 true
     */
    boolean claim(Long id, Long ownerId, LocalDateTime at);

    /**
     * 原子释放到公海。
     *
     * @param id 客户 ID
     * @param at 释放时间
     * @return 更新成功返回 true
     */
    boolean release(Long id, LocalDateTime at);

    /**
     * 分配负责人。
     *
     * @param id 客户 ID
     * @param ownerId 新负责人
     * @param deptId 新部门
     * @param at 分配时间
     * @return 更新成功返回 true
     */
    boolean updateOwner(Long id, Long ownerId, Long deptId, LocalDateTime at);

    /**
     * 全量刷新最近跟进时间。
     *
     * @param id 客户 ID
     * @param at 跟进时间
     */
    void touchFollowUp(Long id, LocalDateTime at);

    /**
     * 查询公海回收候选（私有、未合并、最近跟进早于阈值）。
     *
     * @param followUpBefore 最近跟进时间阈值
     * @param levels 适用等级（逗号分隔，空表示不限）
     * @param deptId 适用部门（空表示不限）
     * @param limit 最大条数
     * @return 回收候选
     */
    List<Customer> findRecycleCandidates(LocalDateTime followUpBefore, String levels, Long deptId, int limit);
}
