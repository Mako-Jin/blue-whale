package com.bluewhale.erp.qc.application.view;

import com.bluewhale.erp.qc.domain.model.CalibrationRecord;
import lombok.Data;

import java.time.LocalDate;

/**
 * 校准记录视图。
 *
 * @author BlueWhale
 */
@Data
public class CalibrationRecordView {

    /** 主键 */
    private Long id;

    /** 校准记录号 */
    private String recordNo;

    /** 量具编号 */
    private String toolNo;

    /** 校准计划主键 */
    private Long planId;

    /** 校准日期 */
    private LocalDate calibrationDate;

    /** 校准结论 */
    private String result;

    /** 校准证书号 */
    private String certificateNo;

    /** 备注 */
    private String remark;

    /**
     * 由领域对象构建视图。
     *
     * @param record 校准记录
     * @return 视图
     */
    public static CalibrationRecordView of(CalibrationRecord record) {
        CalibrationRecordView view = new CalibrationRecordView();
        view.setId(record.getId());
        view.setRecordNo(record.getRecordNo());
        view.setToolNo(record.getToolNo());
        view.setPlanId(record.getPlanId());
        view.setCalibrationDate(record.getCalibrationDate());
        view.setResult(record.getResult());
        view.setCertificateNo(record.getCertificateNo());
        view.setRemark(record.getRemark());
        return view;
    }
}
