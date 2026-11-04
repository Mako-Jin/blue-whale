package com.bluewhale.erp.qc.domain.repository;

import com.bluewhale.erp.qc.domain.model.Equipment;
import com.bluewhale.erp.qc.domain.model.QcQuery;

import java.util.List;
import java.util.Optional;

/**
 * 设备档案仓储。
 *
 * @author BlueWhale
 */
public interface EquipmentRepository {

    /**
     * 分页查询设备。
     *
     * @param query 查询条件
     * @return 设备列表
     */
    List<Equipment> page(QcQuery query);

    /**
     * 统计设备数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(QcQuery query);

    /**
     * 按设备编码查询。
     *
     * @param equipmentNo 设备编码
     * @return 设备
     */
    Optional<Equipment> findByEquipmentNo(String equipmentNo);

    /**
     * 新增设备。
     *
     * @param equipment 设备
     */
    void insert(Equipment equipment);

    /**
     * 更新设备。
     *
     * @param equipment 设备
     */
    void update(Equipment equipment);
}
