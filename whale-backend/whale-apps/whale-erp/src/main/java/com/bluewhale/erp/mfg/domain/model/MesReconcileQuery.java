package com.bluewhale.erp.mfg.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MES 对账分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MesReconcileQuery extends MfgQuery {

    /** MES 下发状态：NOT_SYNCED/PUSHED/FAILED */
    private String mesSyncStatus;

    /** MES 工单状态 */
    private String mesStatus;
}
