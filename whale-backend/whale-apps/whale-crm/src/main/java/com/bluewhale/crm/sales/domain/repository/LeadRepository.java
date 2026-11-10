package com.bluewhale.crm.sales.domain.repository;

import com.bluewhale.crm.sales.domain.model.Lead;
import com.bluewhale.crm.sales.domain.model.LeadQuery;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 线索仓储端口。
 *
 * @author BlueWhale
 */
public interface LeadRepository {

    /**
     * 分页查询线索。
     *
     * @param query 查询条件
     * @return 当前页线索
     */
    List<Lead> page(LeadQuery query);

    /**
     * 统计符合条件的线索数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(LeadQuery query);

    /**
     * 按主键查询。
     *
     * @param id 线索 ID
     * @return 线索；不存在返回 {@code null}
     */
    Lead findById(Long id);

    /**
     * 按编号查询（租户内唯一）。
     *
     * @param leadNo 线索编号
     * @return 线索；不存在返回 {@code null}
     */
    Lead findByNo(String leadNo);

    /**
     * 查找疑似重复线索（电话或邮箱相同）。
     *
     * @param normalizedPhone 归一化电话
     * @param normalizedEmail 归一化邮箱
     * @param excludeId 排除的线索 ID（编辑时传自身）
     * @return 疑似重复线索
     */
    List<Lead> findDuplicates(String normalizedPhone, String normalizedEmail, Long excludeId);

    /**
     * 查询指定前缀下最大编号。
     *
     * @param prefix 编号前缀
     * @return 最大编号；无记录返回 {@code null}
     */
    String findMaxNo(String prefix);

    /**
     * 新增线索。
     *
     * @param lead 线索
     */
    void insert(Lead lead);

    /**
     * 更新线索。
     *
     * @param lead 线索
     */
    void update(Lead lead);

    /**
     * 原子标记线索已转化（仅当状态非 CONVERTED 时成功）。
     *
     * @param id 线索 ID
     * @param customerId 客户 ID
     * @param opportunityId 商机 ID
     * @param at 转化时间
     * @return 更新成功返回 true
     */
    boolean markConverted(Long id, Long customerId, Long opportunityId, LocalDateTime at);

    /**
     * 更新负责人。
     *
     * @param id 线索 ID
     * @param ownerId 负责人
     * @param deptId 归属部门
     */
    void updateOwner(Long id, Long ownerId, Long deptId);
}
