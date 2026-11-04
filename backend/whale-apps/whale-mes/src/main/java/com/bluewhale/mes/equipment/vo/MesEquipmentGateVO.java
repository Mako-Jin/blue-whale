package com.bluewhale.mes.equipment.vo;

import lombok.Data;

/**
 * 设备开工门禁视图
 *
 * <p>{@code passed=false} 表示设备当前处于故障/离线状态，禁止开工（D11 默认阻断口径）。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesEquipmentGateVO {

    /** 设备编码 */
    private String equipmentCode;

    /** 设备当前状态 */
    private String status;

    /** 是否允许开工 */
    private Boolean passed;

    /** 门禁说明 */
    private String message;
}
