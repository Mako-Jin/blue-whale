package com.bluewhale.lms.exam.application.view;

import com.bluewhale.lms.exam.domain.model.Paper;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试卷列表视图。
 *
 * @author BlueWhale
 */
@Data
public class PaperView {

    /** 主键 */
    private Long id;

    /** 试卷编码 */
    private String paperCode;

    /** 试卷名称 */
    private String title;

    /** 关联课程 ID */
    private Long courseId;

    /** 考试时长（分钟） */
    private Integer durationMinutes;

    /** 总分 */
    private BigDecimal totalScore;

    /** 及格分 */
    private BigDecimal passScore;

    /** 题目数 */
    private Integer questionCount;

    /** 抽题种子 */
    private String seed;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 领域转视图。
     *
     * @param paper 试卷
     * @return 视图
     */
    public static PaperView from(Paper paper) {
        PaperView view = new PaperView();
        view.setId(paper.getId());
        view.setPaperCode(paper.getPaperCode());
        view.setTitle(paper.getTitle());
        view.setCourseId(paper.getCourseId());
        view.setDurationMinutes(paper.getDurationMinutes());
        view.setTotalScore(paper.getTotalScore());
        view.setPassScore(paper.getPassScore());
        view.setQuestionCount(paper.getQuestionCount());
        view.setSeed(paper.getSeed());
        view.setStatus(paper.getStatus() == null ? null : paper.getStatus().name());
        view.setRemark(paper.getRemark());
        view.setCreateTime(paper.getCreateTime());
        return view;
    }
}
