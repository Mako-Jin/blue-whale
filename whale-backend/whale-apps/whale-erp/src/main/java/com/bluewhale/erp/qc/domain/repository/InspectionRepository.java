package com.bluewhale.erp.qc.domain.repository;

import com.bluewhale.erp.qc.domain.model.InspectionOrder;
import com.bluewhale.erp.qc.domain.model.QcQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 质量检验单仓储。
 *
 * @author BlueWhale
 */
public interface InspectionRepository {

    /**
     * 分页查询检验单。
     *
     * @param query 查询条件
     * @return 检验单列表
     */
    List<InspectionOrder> page(QcQuery query);

    /**
     * 统计检验单数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(QcQuery query);

    /**
     * 按主键查询检验单（含检验项）。
     *
     * @param id 主键
     * @return 检验单
     */
    Optional<InspectionOrder> findById(Long id);

    /**
     * 按检验单号查询检验单（含检验项）。
     *
     * @param orderNo 检验单号
     * @return 检验单
     */
    Optional<InspectionOrder> findByOrderNo(String orderNo);

    /**
     * 生成检验单号。
     *
     * @param inspectDate 检验日期
     * @return 检验单号
     */
    String nextOrderNo(LocalDate inspectDate);

    /**
     * 新增检验单与检验项。
     *
     * @param order 检验单
     */
    void insert(InspectionOrder order);

    /**
     * 更新检验单头与检验项结论。
     *
     * @param order 检验单
     */
    void update(InspectionOrder order);
}
