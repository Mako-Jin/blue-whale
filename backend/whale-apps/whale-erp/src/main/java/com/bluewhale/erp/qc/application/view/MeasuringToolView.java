package com.bluewhale.erp.qc.application.view;

import com.bluewhale.erp.qc.domain.model.MeasuringTool;
import lombok.Data;

import java.time.LocalDate;

/**
 * 量具台账视图。
 *
 * @author BlueWhale
 */
@Data
public class MeasuringToolView {

    /** 主键 */
    private Long id;

    /** 量具编号 */
    private String toolNo;

    /** 量具名称 */
    private String name;

    /** 量具类型 */
    private String toolType;

    /** 精度值描述 */
    private String precisionValue;

    /** 校准周期（月） */
    private Integer calibrationCycleMonths;

    /** 上次校准日期 */
    private LocalDate lastCalibrationDate;

    /** 下次校准日期 */
    private LocalDate nextCalibrationDate;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param tool 量具
     * @return 视图
     */
    public static MeasuringToolView of(MeasuringTool tool) {
        MeasuringToolView view = new MeasuringToolView();
        view.setId(tool.getId());
        view.setToolNo(tool.getToolNo());
        view.setName(tool.getName());
        view.setToolType(tool.getToolType());
        view.setPrecisionValue(tool.getPrecisionValue());
        view.setCalibrationCycleMonths(tool.getCalibrationCycleMonths());
        view.setLastCalibrationDate(tool.getLastCalibrationDate());
        view.setNextCalibrationDate(tool.getNextCalibrationDate());
        view.setStatus(tool.getStatus());
        view.setRemark(tool.getRemark());
        return view;
    }
}
