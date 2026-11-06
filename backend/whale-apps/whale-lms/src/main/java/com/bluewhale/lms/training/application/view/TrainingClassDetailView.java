package com.bluewhale.lms.training.application.view;

import com.bluewhale.lms.training.domain.model.TrainingClass;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 培训班详情视图（列表字段 + 场次 + 统计）。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TrainingClassDetailView extends TrainingClassView {

    /** 场次列表 */
    private List<SessionView> sessions;

    /** 已分配人数 */
    private Long assignedCount;

    /** 签到人次 */
    private Long checkinCount;

    /** 课时合计（各场次之和） */
    private BigDecimal totalHours;

    /**
     * 从领域模型转换。
     *
     * @param trainingClass 培训班
     * @return 详情视图
     */
    public static TrainingClassDetailView fromClass(TrainingClass trainingClass) {
        TrainingClassDetailView view = new TrainingClassDetailView();
        view.setId(trainingClass.getId());
        view.setClassCode(trainingClass.getClassCode());
        view.setTitle(trainingClass.getTitle());
        view.setCourseId(trainingClass.getCourseId());
        view.setMode(trainingClass.getMode().name());
        view.setLecturer(trainingClass.getLecturer());
        view.setStartDate(trainingClass.getStartDate());
        view.setEndDate(trainingClass.getEndDate());
        view.setLocation(trainingClass.getLocation());
        view.setCapacity(trainingClass.getCapacity());
        view.setStatus(trainingClass.getStatus().name());
        view.setRemark(trainingClass.getRemark());
        view.setCreateTime(trainingClass.getCreateTime());
        return view;
    }
}
