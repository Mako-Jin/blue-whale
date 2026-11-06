package com.bluewhale.lms.integration.application.view;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 一轮催办的结果汇总。
 *
 * @author BlueWhale
 */
@Data
public class UrgeRunView {

    /** 催办归属日期（幂等键按该日期去重） */
    private LocalDate day;

    /** 扫描的培训班数 */
    private int scannedClasses;

    /** 存在未完成学员的培训班数 */
    private int urgentClasses;

    /** 实际投递人次 */
    private int notified;

    /** 因离职跳过人次 */
    private int skippedOffboard;

    /** 因快照未收录跳过人次 */
    private int skippedUnrecorded;

    /** 失败班级数 */
    private int failedClasses;

    /** 明细 */
    private List<UrgeItemView> items = new ArrayList<>();
}
