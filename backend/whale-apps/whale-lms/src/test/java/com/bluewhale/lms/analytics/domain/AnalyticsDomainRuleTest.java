package com.bluewhale.lms.analytics.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.analytics.domain.model.MetricScope;
import com.bluewhale.lms.analytics.domain.model.MetricSnapshot;
import com.bluewhale.lms.common.LmsErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 看板领域规则：比率口径、负值纠正、集合包含关系校验、范围解析。
 *
 * @author BlueWhale
 */
class AnalyticsDomainRuleTest {

    /** 统计日 */
    private static final LocalDate DAY = LocalDate.of(2026, 11, 6);

    @Test
    @DisplayName("分母为零时比率为 0 而不是抛错")
    void rateShouldReturnZeroWhenDenominatorIsZero() {
        assertEquals(new BigDecimal("0.0"), MetricSnapshot.rate(5, 0));
        assertEquals(new BigDecimal("0.0"), MetricSnapshot.rate(0, 0));
    }

    @Test
    @DisplayName("比率保留一位小数并四舍五入")
    void rateShouldRoundToSingleDecimal() {
        assertEquals(new BigDecimal("82.4"), MetricSnapshot.rate(61, 74));
        assertEquals(new BigDecimal("100.0"), MetricSnapshot.rate(7, 7));
    }

    @Test
    @DisplayName("完成率分母是学习人数而不是在职人数")
    void completionRateShouldUseLearnerAsDenominator() {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setMemberTotal(20);
        snapshot.setLearnerTotal(10);
        snapshot.setLearnerCompleted(5);
        // 用在职人数做分母会得到 25.0%，那是覆盖率的算法，不是完成率
        assertEquals(new BigDecimal("50.0"), snapshot.completionRate());
    }

    @Test
    @DisplayName("校验时负数按 0 处理")
    void validateShouldClampNegatives() {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setMemberTotal(-3);
        snapshot.setLearnerTotal(-1);
        snapshot.setExamTakerTotal(-5);
        snapshot.setCreditTotal(new BigDecimal("-2"));
        snapshot.validate(DAY);
        assertEquals(0, snapshot.getMemberTotal());
        assertEquals(0, snapshot.getLearnerTotal());
        assertEquals(0, snapshot.getExamTakerTotal());
        assertEquals(BigDecimal.ZERO, snapshot.getCreditTotal());
        assertEquals(DAY, snapshot.getStatDate());
        assertEquals(MetricScope.ALL, snapshot.getScopeType());
    }

    @Test
    @DisplayName("完成人数超过学习人数属于口径错误，必须报错而不是截断")
    void validateShouldRejectCompletedGreaterThanLearner() {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setLearnerTotal(3);
        snapshot.setLearnerCompleted(4);
        BizException exception = assertThrows(BizException.class, () -> snapshot.validate(DAY));
        assertEquals(LmsErrorCode.METRIC_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("通过人数超过参考人数属于口径错误")
    void validateShouldRejectPassedGreaterThanTaker() {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setExamTakerTotal(2);
        snapshot.setExamPassedTotal(3);
        BizException exception = assertThrows(BizException.class, () -> snapshot.validate(DAY));
        assertEquals(LmsErrorCode.METRIC_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("统计日缺失时拒绝生成快照")
    void validateShouldRejectNullStatDate() {
        BizException exception = assertThrows(BizException.class, () -> new MetricSnapshot().validate(null));
        assertEquals(LmsErrorCode.METRIC_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("范围解析：空值回落全院，未知值报错")
    void scopeShouldParseAndRejectUnknown() {
        assertEquals(MetricScope.ALL, MetricScope.require(null));
        assertEquals(MetricScope.ALL, MetricScope.require(" "));
        assertEquals(MetricScope.COURSE, MetricScope.require("course"));
        BizException exception = assertThrows(BizException.class, () -> MetricScope.require("SECTION"));
        assertEquals(LmsErrorCode.METRIC_INVALID, exception.getCode());
    }
}
