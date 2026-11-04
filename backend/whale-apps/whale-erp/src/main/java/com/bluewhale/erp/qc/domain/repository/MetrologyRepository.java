package com.bluewhale.erp.qc.domain.repository;

import com.bluewhale.erp.qc.domain.model.CalibrationPlan;
import com.bluewhale.erp.qc.domain.model.CalibrationRecord;
import com.bluewhale.erp.qc.domain.model.MeasuringTool;
import com.bluewhale.erp.qc.domain.model.QcQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 计量器具、校准计划与校准记录仓储。
 *
 * @author BlueWhale
 */
public interface MetrologyRepository {

    /**
     * 分页查询量具。
     *
     * @param query 查询条件
     * @return 量具列表
     */
    List<MeasuringTool> pageTools(QcQuery query);

    /**
     * 统计量具数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long countTools(QcQuery query);

    /**
     * 按量具编号查询。
     *
     * @param toolNo 量具编号
     * @return 量具
     */
    Optional<MeasuringTool> findToolByNo(String toolNo);

    /**
     * 新增量具。
     *
     * @param tool 量具
     */
    void insertTool(MeasuringTool tool);

    /**
     * 更新量具。
     *
     * @param tool 量具
     */
    void updateTool(MeasuringTool tool);

    /**
     * 分页查询校准计划。
     *
     * @param query 查询条件
     * @return 校准计划列表
     */
    List<CalibrationPlan> pagePlans(QcQuery query);

    /**
     * 统计校准计划数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long countPlans(QcQuery query);

    /**
     * 按主键查询校准计划。
     *
     * @param id 主键
     * @return 校准计划
     */
    Optional<CalibrationPlan> findPlanById(Long id);

    /**
     * 生成校准计划号。
     *
     * @param date 建单日期
     * @return 校准计划号
     */
    String nextPlanNo(LocalDate date);

    /**
     * 新增校准计划。
     *
     * @param plan 校准计划
     */
    void insertPlan(CalibrationPlan plan);

    /**
     * 更新校准计划。
     *
     * @param plan 校准计划
     */
    void updatePlan(CalibrationPlan plan);

    /**
     * 分页查询校准记录。
     *
     * @param query 查询条件
     * @return 校准记录列表
     */
    List<CalibrationRecord> pageRecords(QcQuery query);

    /**
     * 统计校准记录数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long countRecords(QcQuery query);

    /**
     * 生成校准记录号。
     *
     * @param date 校准日期
     * @return 校准记录号
     */
    String nextRecordNo(LocalDate date);

    /**
     * 新增校准记录。
     *
     * @param record 校准记录
     */
    void insertRecord(CalibrationRecord record);
}
