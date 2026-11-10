package com.bluewhale.lms.training.application.view;

import com.bluewhale.lms.training.domain.model.TrainingClass;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 培训班列表视图。
 *
 * @author BlueWhale
 */
@Data
public class TrainingClassView {

    /** 主键 */
    private Long id;

    /** 培训班编码 */
    private String classCode;

    /** 培训班名称 */
    private String title;

    /** 关联课程 ID */
    private Long courseId;

    /** 形式 */
    private String mode;

    /** 主讲讲师 */
    private String lecturer;

    /** 开班日期 */
    private LocalDate startDate;

    /** 结班日期 */
    private LocalDate endDate;

    /** 培训地点 */
    private String location;

    /** 计划人数 */
    private Integer capacity;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 已分配人数（应用层填充） */
    private Long assignedCount;

    /** 场次数（应用层填充） */
    private Long sessionCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从领域模型转换（统计字段由应用层填充）。
     *
     * @param trainingClass 培训班
     * @return 视图
     */
    public static TrainingClassView from(TrainingClass trainingClass) {
        TrainingClassView view = new TrainingClassView();
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
