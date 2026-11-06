package com.bluewhale.lms.analytics.application.view;

import com.bluewhale.lms.analytics.domain.model.MetricSnapshot;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 指标快照视图（行）。
 *
 * <p>视图里带上比率：比率由 {@link MetricSnapshot} 现算，不落库。
 * 这样「改口径」只需重跑快照，不用回填历史比率。</p>
 *
 * @author BlueWhale
 */
@Data
public class MetricSnapshotView {

    /** 统计日 */
    private LocalDate statDate;

    /** 统计范围 */
    private String scopeType;

    /** 范围名称 */
    private String scopeName;

    /** 在职人数 */
    private long memberTotal;

    /** 学习人数 */
    private long learnerTotal;

    /** 学习覆盖率（%） */
    private BigDecimal coverageRate;

    /** 完成人数 */
    private long learnerCompleted;

    /** 学习完成率（完成人数 / 学习人数，%） */
    private BigDecimal completionRate;

    /** 参考人数 */
    private long examTakerTotal;

    /** 通过人数 */
    private long examPassedTotal;

    /** 考试通过率（%） */
    private BigDecimal passRate;

    /** 学分总量 */
    private BigDecimal creditTotal;

    /** 获学分人数 */
    private long creditEarners;

    /** 计算时间 */
    private LocalDateTime computedAt;

    /**
     * 由领域对象构建视图。
     *
     * @param snapshot 快照
     * @return 视图
     */
    public static MetricSnapshotView from(MetricSnapshot snapshot) {
        MetricSnapshotView view = new MetricSnapshotView();
        view.setStatDate(snapshot.getStatDate());
        view.setScopeType(snapshot.getScopeType() == null ? null : snapshot.getScopeType().name());
        view.setScopeName(snapshot.getScopeName());
        view.setMemberTotal(snapshot.getMemberTotal());
        view.setLearnerTotal(snapshot.getLearnerTotal());
        view.setCoverageRate(snapshot.coverageRate());
        view.setLearnerCompleted(snapshot.getLearnerCompleted());
        view.setCompletionRate(snapshot.completionRate());
        view.setExamTakerTotal(snapshot.getExamTakerTotal());
        view.setExamPassedTotal(snapshot.getExamPassedTotal());
        view.setPassRate(snapshot.passRate());
        view.setCreditTotal(snapshot.getCreditTotal());
        view.setCreditEarners(snapshot.getCreditEarners());
        view.setComputedAt(snapshot.getComputedAt());
        return view;
    }
}
