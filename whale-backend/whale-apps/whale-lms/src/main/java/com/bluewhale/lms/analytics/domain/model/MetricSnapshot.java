package com.bluewhale.lms.analytics.domain.model;

import com.bluewhale.lms.common.LmsErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习看板指标快照（某统计日某范围的一组计数）。
 *
 * <p>快照只存分子分母，比率一律现算（{@link #rate(long, long)}）。理由：</p>
 * <ol>
 *   <li>存了比率就得连精度一起存，而四舍五入后的比率既不能相加也不能取平均；</li>
 *   <li>分母口径将来若调整（比如把劳务派遣也算进在职人数），只需重算，不用回填历史比率；</li>
 *   <li>「覆盖率 82.3%」这类展示值由前端或导出时统一算，避免两个地方各算一遍、算得不一样。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Data
public class MetricSnapshot {

    /** 比率保留小数位 */
    private static final int RATE_SCALE = 1;

    /** 百分比基数 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 主键 */
    private Long id;

    /** 统计日 */
    private LocalDate statDate;

    /** 统计范围 */
    private MetricScope scopeType = MetricScope.ALL;

    /** 范围对象 ID（ALL 固定 0） */
    private Long scopeId = 0L;

    /** 范围名称快照 */
    private String scopeName = MetricScope.ALL.getLabel();

    /** 在职人数（覆盖率分母） */
    private long memberTotal;

    /** 学习人数 */
    private long learnerTotal;

    /** 完成人数 */
    private long learnerCompleted;

    /** 参考人数 */
    private long examTakerTotal;

    /** 通过人数 */
    private long examPassedTotal;

    /** 学分总量 */
    private BigDecimal creditTotal = BigDecimal.ZERO;

    /** 获学分人数 */
    private long creditEarners;

    /** 计算时间 */
    private LocalDateTime computedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 计算百分比（保留一位小数，分母为 0 返回 0）。
     *
     * <p>分母为 0 时返回 0 而不是抛错：新租户上线第一天没有在职员工也正常，
     * 看板显示 0% 比整个页面报错可接受得多。</p>
     *
     * @param numerator 分子
     * @param denominator 分母
     * @return 百分比（如 82.3 表示 82.3%）
     */
    public static BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(denominator), RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 学习覆盖率（学习人数 / 在职人数）。
     *
     * @return 百分比
     */
    public BigDecimal coverageRate() {
        return rate(learnerTotal, memberTotal);
    }

    /**
     * 学习完成率（完成人数 / 学习人数）。
     *
     * <p>分母用「学习人数」而不是「在职人数」：完成率回答的是「已经开始学的人里
     * 有多少学完了」，若把没开始学的人也塞进分母，它就退化成覆盖率的另一种算法，
     * 两个指标会同时变差、看不出问题出在「没人学」还是「学了没学完」。</p>
     *
     * @return 百分比
     */
    public BigDecimal completionRate() {
        return rate(learnerCompleted, learnerTotal);
    }

    /**
     * 考试通过率（通过人数 / 参考人数）。
     *
     * @return 百分比
     */
    public BigDecimal passRate() {
        return rate(examPassedTotal, examTakerTotal);
    }

    /**
     * 校验并纠正快照。
     *
     * <p>只做「能自动纠正的就纠正、纠不了才报错」：人数不可能为负，负数按 0 处理；
     * 完成人数超过学习人数、通过人数超过参考人数属于集合包含关系被破坏，
     * 这说明取数口径出了问题，必须报错而不是悄悄截断——悄悄截断会让看板
     * 长期显示一个「合理但错误」的数字。</p>
     *
     * @param statDate 统计日
     * @return 当前对象
     */
    public MetricSnapshot validate(LocalDate statDate) {
        if (statDate == null) {
            throw LmsErrorCode.exception(LmsErrorCode.METRIC_INVALID, "统计日不能为空");
        }
        if (scopeType == null) {
            scopeType = MetricScope.ALL;
        }
        if (scopeId == null) {
            scopeId = 0L;
        }
        if (scopeName == null || scopeName.isBlank()) {
            scopeName = scopeType.getLabel();
        }
        this.statDate = statDate;
        this.memberTotal = Math.max(memberTotal, 0);
        this.learnerTotal = Math.max(learnerTotal, 0);
        this.learnerCompleted = Math.max(learnerCompleted, 0);
        this.examTakerTotal = Math.max(examTakerTotal, 0);
        this.examPassedTotal = Math.max(examPassedTotal, 0);
        this.creditEarners = Math.max(creditEarners, 0);
        if (creditTotal == null || creditTotal.signum() < 0) {
            this.creditTotal = BigDecimal.ZERO;
        }
        if (learnerCompleted > learnerTotal) {
            throw LmsErrorCode.exception(LmsErrorCode.METRIC_INVALID,
                    "完成人数不能超过学习人数：" + learnerCompleted + " > " + learnerTotal);
        }
        if (examPassedTotal > examTakerTotal) {
            throw LmsErrorCode.exception(LmsErrorCode.METRIC_INVALID,
                    "通过人数不能超过参考人数：" + examPassedTotal + " > " + examTakerTotal);
        }
        this.computedAt = LocalDateTime.now();
        return this;
    }
}
