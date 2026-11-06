package com.bluewhale.lms.exam.application.view;

import com.bluewhale.lms.exam.domain.model.Exam;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试列表 / 详情视图。
 *
 * @author BlueWhale
 */
@Data
public class ExamView {

    /** 主键 */
    private Long id;

    /** 考试编码 */
    private String examCode;

    /** 考试名称 */
    private String title;

    /** 试卷 ID */
    private Long paperId;

    /** 试卷名称（详情接口填充） */
    private String paperTitle;

    /** 试卷总分（详情接口填充，便于核对及格分） */
    private BigDecimal paperTotalScore;

    /** 关联课程 ID */
    private Long courseId;

    /** 开考时间 */
    private LocalDateTime startTime;

    /** 截止时间 */
    private LocalDateTime endTime;

    /** 单次作答时长（分钟） */
    private Integer durationMinutes;

    /** 最大作答次数 */
    private Integer maxAttempts;

    /** 是否乱序 */
    private Boolean shuffle;

    /** 及格分 */
    private BigDecimal passScore;

    /** 切屏次数上限（0 不限） */
    private Integer switchLimit;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 领域转视图。
     *
     * @param exam 考试
     * @return 视图
     */
    public static ExamView from(Exam exam) {
        ExamView view = new ExamView();
        view.setId(exam.getId());
        view.setExamCode(exam.getExamCode());
        view.setTitle(exam.getTitle());
        view.setPaperId(exam.getPaperId());
        view.setCourseId(exam.getCourseId());
        view.setStartTime(exam.getStartTime());
        view.setEndTime(exam.getEndTime());
        view.setDurationMinutes(exam.getDurationMinutes());
        view.setMaxAttempts(exam.getMaxAttempts());
        view.setShuffle(exam.isShuffle());
        view.setPassScore(exam.getPassScore());
        view.setSwitchLimit(exam.getSwitchLimit());
        view.setStatus(exam.getStatus() == null ? null : exam.getStatus().name());
        view.setRemark(exam.getRemark());
        view.setCreateTime(exam.getCreateTime());
        return view;
    }
}
