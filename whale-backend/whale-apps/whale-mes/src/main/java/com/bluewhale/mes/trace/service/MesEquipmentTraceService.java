package com.bluewhale.mes.trace.service;

import com.bluewhale.mes.trace.vo.MesEquipmentTraceVO;

/**
 * MES 设备维度追溯服务。
 *
 * @author BlueWhale
 */
public interface MesEquipmentTraceService {

    /**
     * 按设备编码追溯该工位的报工与批次
     *
     * @param equipmentCode 设备编码
     * @param limit         报工记录条数上限
     * @return 设备维度追溯视图
     */
    MesEquipmentTraceVO trace(String equipmentCode, Integer limit);
}
