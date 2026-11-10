package com.bluewhale.erp.qc.domain.repository;

import com.bluewhale.erp.qc.domain.model.QcQuery;
import com.bluewhale.erp.qc.domain.model.QualityCost;

import java.time.LocalDate;
import java.util.List;

/**
 * 质量成本仓储。
 *
 * @author BlueWhale
 */
public interface QualityCostRepository {

    /**
     * 分页查询质量成本。
     *
     * @param query 查询条件
     * @return 质量成本列表
     */
    List<QualityCost> page(QcQuery query);

    /**
     * 统计质量成本数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(QcQuery query);

    /**
     * 生成质量成本单号。
     *
     * @param costDate 成本日期
     * @return 质量成本单号
     */
    String nextCostNo(LocalDate costDate);

    /**
     * 新增质量成本。
     *
     * @param cost 质量成本
     */
    void insert(QualityCost cost);
}
