package com.bluewhale.erp.mfg.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * MES 工单下发结果。
 *
 * @author BlueWhale
 */
@Data
@AllArgsConstructor
public class MesPushResult {

    /** MES 工单 ID */
    private Long workOrderId;

    /** MES 工单编码 */
    private String workOrderCode;
}
