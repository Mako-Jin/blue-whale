package com.bluewhale.erp.qc.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.time.LocalDate;

/**
 * 设备档案聚合。
 *
 * <p>不变量：设备编码租户内唯一；名称必填；状态只能为运行/闲置/故障/退役。</p>
 *
 * @author BlueWhale
 */
@Data
public class Equipment {

    /** 主键 */
    private Long id;

    /** 设备编码 */
    private String equipmentNo;

    /** 设备名称 */
    private String name;

    /** 设备类型 */
    private String equipmentType;

    /** 安装位置 */
    private String location;

    /** 状态：RUNNING / IDLE / DOWN / RETIRED */
    private String status;

    /** 购置日期 */
    private LocalDate purchaseDate;

    /** 备注 */
    private String remark;

    /**
     * 校验建档数据。
     */
    public void validateForCreate() {
        if (equipmentNo == null || equipmentNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "设备编码不能为空");
        }
        if (name == null || name.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID, "设备名称不能为空");
        }
        status = validStatus(status);
    }

    /**
     * 校验并规范化状态。
     *
     * @param value 状态
     * @return 合法状态
     */
    private String validStatus(String value) {
        String target = value == null || value.isBlank() ? QcStatus.EQUIPMENT_RUNNING : value;
        if (!QcStatus.EQUIPMENT_RUNNING.equals(target)
                && !QcStatus.EQUIPMENT_IDLE.equals(target)
                && !QcStatus.EQUIPMENT_DOWN.equals(target)
                && !QcStatus.EQUIPMENT_RETIRED.equals(target)) {
            throw ErpErrorCode.exception(ErpErrorCode.QC_INVALID,
                    "设备状态只能为 RUNNING、IDLE、DOWN 或 RETIRED");
        }
        return target;
    }
}
