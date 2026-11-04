package com.bluewhale.erp.qc.domain.repository;

import com.bluewhale.erp.qc.domain.model.OeeRecord;
import com.bluewhale.erp.qc.domain.model.QcQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 设备 OEE 记录仓储。
 *
 * @author BlueWhale
 */
public interface OeeRepository {

    /**
     * 分页查询 OEE 记录。
     *
     * @param query 查询条件
     * @return OEE 记录列表
     */
    List<OeeRecord> page(QcQuery query);

    /**
     * 统计 OEE 记录数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(QcQuery query);

    /**
     * 按设备与统计日期查询，用于 MES 回传幂等覆盖。
     *
     * @param equipmentNo 设备编码
     * @param recordDate 统计日期
     * @return OEE 记录
     */
    Optional<OeeRecord> findByEquipmentAndDate(String equipmentNo, LocalDate recordDate);

    /**
     * 新增 OEE 记录。
     *
     * @param record OEE 记录
     */
    void insert(OeeRecord record);

    /**
     * 更新 OEE 记录。
     *
     * @param record OEE 记录
     */
    void update(OeeRecord record);
}
