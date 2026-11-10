package com.bluewhale.erp.hr.domain.repository;

import com.bluewhale.erp.hr.domain.model.Appraisal;
import com.bluewhale.erp.hr.domain.model.HrQuery;
import com.bluewhale.erp.hr.domain.model.Kpi;
import com.bluewhale.erp.hr.domain.model.PerformanceCycle;

import java.util.List;
import java.util.Optional;

/**
 * 绩效周期仓储。
 *
 * @author BlueWhale
 */
public interface PerformanceRepository {

    /**
     * 分页查询绩效周期。
     *
     * @param query 查询条件
     * @return 周期列表
     */
    List<PerformanceCycle> page(HrQuery query);

    /**
     * 统计周期数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(HrQuery query);

    /**
     * 按主键查询（含指标与考核结果）。
     *
     * @param id 主键
     * @return 绩效周期
     */
    Optional<PerformanceCycle> findById(Long id);

    /**
     * 生成周期编号。
     *
     * @param period 期间
     * @return 周期编号
     */
    String nextCycleNo(String period);

    /**
     * 新增周期。
     *
     * @param cycle 周期
     */
    void insert(PerformanceCycle cycle);

    /**
     * 更新周期状态。
     *
     * @param cycle 周期
     */
    void update(PerformanceCycle cycle);

    /**
     * 新增指标。
     *
     * @param kpi 指标
     */
    void insertKpi(Kpi kpi);

    /**
     * 查询周期内指标。
     *
     * @param cycleId 周期主键
     * @return 指标列表
     */
    List<Kpi> listKpis(Long cycleId);

    /**
     * 查询周期内指标。
     *
     * @param cycleId 周期主键
     * @param kpiId 指标主键
     * @return 指标
     */
    Optional<Kpi> findKpi(Long cycleId, Long kpiId);

    /**
     * 新增或更新考核结果。
     *
     * @param appraisal 考核结果
     */
    void upsertAppraisal(Appraisal appraisal);

    /**
     * 查询周期内考核结果。
     *
     * @param cycleId 周期主键
     * @return 考核结果列表
     */
    List<Appraisal> listAppraisals(Long cycleId);
}
