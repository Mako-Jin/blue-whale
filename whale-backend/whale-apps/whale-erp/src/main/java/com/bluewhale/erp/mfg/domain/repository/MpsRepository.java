package com.bluewhale.erp.mfg.domain.repository;

import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.MpsPlan;

import java.util.List;
import java.util.Optional;

/**
 * 主生产计划仓储。
 *
 * @author BlueWhale
 */
public interface MpsRepository {

    /**
     * 分页查询主生产计划。
     *
     * @param query 查询条件
     * @return 计划列表
     */
    List<MpsPlan> page(MfgQuery query);

    /**
     * 统计计划数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(MfgQuery query);

    /**
     * 按主键查询（含分录）。
     *
     * @param id 主键
     * @return 主生产计划
     */
    Optional<MpsPlan> findById(Long id);

    /**
     * 生成计划单号。
     *
     * @param period 计划期间
     * @return 计划单号
     */
    String nextPlanNo(String period);

    /**
     * 新增计划与分录。
     *
     * @param plan 主生产计划
     */
    void insert(MpsPlan plan);

    /**
     * 更新计划状态与已下达数量。
     *
     * @param plan 主生产计划
     */
    void update(MpsPlan plan);
}
