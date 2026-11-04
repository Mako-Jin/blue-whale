package com.bluewhale.erp.mfg.domain.repository;

import com.bluewhale.erp.mfg.domain.model.Forecast;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;

import java.util.List;
import java.util.Optional;

/**
 * 需求预测仓储。
 *
 * @author BlueWhale
 */
public interface ForecastRepository {

    /**
     * 分页查询预测批次。
     *
     * @param query 查询条件
     * @return 预测批次
     */
    List<Forecast> page(MfgQuery query);

    /**
     * 统计预测批次数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(MfgQuery query);

    /**
     * 按主键查询（含分录）。
     *
     * @param id 主键
     * @return 预测批次
     */
    Optional<Forecast> findById(Long id);

    /**
     * 生成批次号。
     *
     * @param period 预测期间
     * @return 批次号
     */
    String nextBatchNo(String period);

    /**
     * 新增预测批次与分录。
     *
     * @param forecast 预测批次
     */
    void insert(Forecast forecast);

    /**
     * 更新预测批次状态。
     *
     * @param forecast 预测批次
     */
    void update(Forecast forecast);
}
