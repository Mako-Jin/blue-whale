package com.bluewhale.lms.credit.domain;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.credit.domain.model.CreditLedger;
import com.bluewhale.lms.credit.domain.model.CreditRule;
import com.bluewhale.lms.credit.domain.model.CreditRuleStatus;
import com.bluewhale.lms.credit.domain.model.CreditSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 学分领域规则：规则参数边界、通配判定、折算口径。
 *
 * @author BlueWhale
 */
class CreditDomainRuleTest {

    /** 业务发生时间 */
    private static final LocalDateTime OCCURRED = LocalDateTime.of(2026, 3, 15, 9, 0);

    @Test
    @DisplayName("规则编码归一为大写")
    void shouldNormalizeRuleCode() {
        assertEquals("EXAM-DEFAULT", CreditRule.normalizeCode(" exam-default "));
        assertEquals("", CreditRule.normalizeCode(null));
    }

    @Test
    @DisplayName("matchId 为 0 表示通配")
    void zeroMatchIdShouldMeanAll() {
        CreditRule rule = rule(0L);
        assertTrue(rule.isMatchAll());
        rule.setMatchId(12L);
        assertFalse(rule.isMatchAll());
    }

    @Test
    @DisplayName("学分必须大于 0")
    void shouldRejectZeroCredit() {
        CreditRule rule = rule(0L);
        rule.setCredit(BigDecimal.ZERO);
        BizException exception = assertThrows(BizException.class, rule::validateBasics);
        assertEquals(LmsErrorCode.CREDIT_RULE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("学分不得超过上限")
    void shouldRejectOversizedCredit() {
        CreditRule rule = rule(0L);
        rule.setCredit(new BigDecimal("1000"));
        BizException exception = assertThrows(BizException.class, rule::validateBasics);
        assertEquals(LmsErrorCode.CREDIT_RULE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("来源类型不能为空")
    void shouldRejectNullSource() {
        CreditRule rule = rule(0L);
        rule.setSourceType(null);
        BizException exception = assertThrows(BizException.class, rule::validateBasics);
        assertEquals(LmsErrorCode.CREDIT_RULE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("匹配对象 ID 不能为负")
    void shouldRejectNegativeMatchId() {
        CreditRule rule = rule(0L);
        rule.setMatchId(-1L);
        BizException exception = assertThrows(BizException.class, rule::validateBasics);
        assertEquals(LmsErrorCode.CREDIT_RULE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("停用规则不可记账")
    void disabledRuleShouldNotAccrue() {
        CreditRule rule = rule(0L);
        rule.setStatus(CreditRuleStatus.DISABLED);
        BizException exception = assertThrows(BizException.class, rule::requireAccruable);
        assertEquals(LmsErrorCode.CREDIT_RULE_DISABLED, exception.getCode());
    }

    @Test
    @DisplayName("台账学分取规则值、年度取业务发生年")
    void ledgerShouldFollowRule() {
        CreditRule rule = rule(7L);
        rule.setId(3L);
        rule.setRuleCode("EXAM-07");
        rule.setCredit(new BigDecimal("5.0"));
        CreditLedger ledger = CreditLedger.of(rule, 100L, "zhangsan", 7L, OCCURRED, 9L, "补记");
        assertEquals(new BigDecimal("5.0"), ledger.getCredit());
        assertEquals(2026, ledger.getPeriodYear());
        assertEquals("EXAM-07", ledger.getRuleCode());
        assertEquals(CreditSource.EXAM, ledger.getSourceType());
        assertEquals(OCCURRED, ledger.getOccurredAt());
    }

    @Test
    @DisplayName("跨年补记归入业务发生年度")
    void backfillShouldKeepOriginalYear() {
        CreditLedger ledger = CreditLedger.of(rule(0L), 100L, "zhangsan", 5L,
                LocalDateTime.of(2025, 12, 20, 10, 0), 9L, null);
        assertEquals(2025, ledger.getPeriodYear());
    }

    @Test
    @DisplayName("无规则不可生成台账")
    void ledgerShouldRequireRule() {
        BizException exception = assertThrows(BizException.class,
                () -> CreditLedger.of(null, 100L, "zhangsan", 1L, OCCURRED, 9L, null));
        assertEquals(LmsErrorCode.CREDIT_RULE_MISSING, exception.getCode());
    }

    @Test
    @DisplayName("台账缺少学员或来源时报参数错误")
    void ledgerShouldValidateIdentity() {
        CreditRule rule = rule(0L);
        assertEquals(LmsErrorCode.CREDIT_INVALID,
                assertThrows(BizException.class,
                        () -> CreditLedger.of(rule, null, "a", 1L, OCCURRED, 9L, null)).getCode());
        assertEquals(LmsErrorCode.CREDIT_INVALID,
                assertThrows(BizException.class,
                        () -> CreditLedger.of(rule, 100L, "a", null, OCCURRED, 9L, null)).getCode());
    }

    @Test
    @DisplayName("来源解析：无法识别时 parse 返回空、require 抛错")
    void shouldParseSource() {
        assertNull(CreditSource.parse("MANUAL"));
        assertNull(CreditSource.parse(null));
        assertEquals(CreditSource.EXAM, CreditSource.parse(" exam "));
        BizException exception = assertThrows(BizException.class, () -> CreditSource.require("MANUAL"));
        assertEquals(LmsErrorCode.CREDIT_RULE_INVALID, exception.getCode());
    }

    /**
     * 构造规则。
     *
     * @param matchId 匹配对象 ID
     * @return 规则
     */
    private CreditRule rule(Long matchId) {
        CreditRule rule = new CreditRule();
        rule.setRuleCode("EXAM-DEFAULT");
        rule.setName("考试通过默认学分");
        rule.setSourceType(CreditSource.EXAM);
        rule.setMatchId(matchId);
        rule.setCredit(new BigDecimal("2.0"));
        rule.setStatus(CreditRuleStatus.ENABLED);
        return rule;
    }
}
