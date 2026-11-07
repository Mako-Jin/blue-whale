package com.bluewhale.crm.marketing.domain.repository;

import com.bluewhale.crm.marketing.domain.model.NurtureQuery;
import com.bluewhale.crm.marketing.domain.model.NurtureTask;

import java.util.List;

/**
 * 客户培育任务仓储。
 *
 * @author BlueWhale
 */
public interface NurtureTaskRepository {

    /**
     * 分页查询培育任务。
     *
     * @param query 查询条件
     * @return 任务列表
     */
    List<NurtureTask> page(NurtureQuery query);

    /**
     * 统计培育任务数量。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(NurtureQuery query);

    /**
     * 按主键查询培育任务。
     *
     * @param id 主键
     * @return 任务；不存在返回 null
     */
    NurtureTask findById(Long id);

    /**
     * 新增培育任务。
     *
     * @param task 任务
     */
    void insert(NurtureTask task);

    /**
     * 更新培育任务。
     *
     * @param task 任务
     */
    void update(NurtureTask task);

    /**
     * 统计指定前缀的任务号数量。
     *
     * @param prefix 前缀
     * @return 数量
     */
    long countByTaskNoPrefix(String prefix);
}
