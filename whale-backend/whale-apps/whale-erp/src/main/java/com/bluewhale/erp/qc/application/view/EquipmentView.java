package com.bluewhale.erp.qc.application.view;

import com.bluewhale.erp.qc.domain.model.Equipment;
import lombok.Data;

import java.time.LocalDate;

/**
 * 设备档案视图。
 *
 * @author BlueWhale
 */
@Data
public class EquipmentView {

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

    /** 状态 */
    private String status;

    /** 购置日期 */
    private LocalDate purchaseDate;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param equipment 设备
     * @return 视图
     */
    public static EquipmentView of(Equipment equipment) {
        EquipmentView view = new EquipmentView();
        view.setId(equipment.getId());
        view.setEquipmentNo(equipment.getEquipmentNo());
        view.setName(equipment.getName());
        view.setEquipmentType(equipment.getEquipmentType());
        view.setLocation(equipment.getLocation());
        view.setStatus(equipment.getStatus());
        view.setPurchaseDate(equipment.getPurchaseDate());
        view.setRemark(equipment.getRemark());
        return view;
    }
}
