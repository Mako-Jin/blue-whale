package com.bluewhale.lms.credit.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.credit.application.command.CreditAccrueCommand;
import com.bluewhale.lms.credit.application.view.CreditLedgerView;
import com.bluewhale.lms.credit.application.view.CreditSummaryView;
import com.bluewhale.lms.credit.domain.model.CreditLedger;
import com.bluewhale.lms.credit.domain.model.CreditLedgerQuery;
import com.bluewhale.lms.credit.domain.model.CreditRule;
import com.bluewhale.lms.credit.domain.model.CreditRuleStatus;
import com.bluewhale.lms.credit.domain.model.CreditSource;
import com.bluewhale.lms.credit.domain.repository.CreditLedgerRepository;
import com.bluewhale.lms.credit.domain.repository.CreditRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学分台账：同一事件幂等、无规则不记账、汇总从台账聚合。
 *
 * @author BlueWhale
 */
class CreditAppServiceImplTest {

    /** 学员用户 ID */
    private static final long HOLDER_ID = 100L;

    /** 考试 ID（学分来源业务 ID） */
    private static final long EXAM_ID = 7L;

    /** 写入后的台账 ID */
    private static final long SAVED_ID = 66L;

    /** 已有台账 ID */
    private static final long EXISTING_ID = 55L;

    /** 操作人用户 ID */
    private static final long OPERATOR_ID = 9L;

    private CreditLedgerRepository creditLedgerRepository;

    private CreditRuleRepository creditRuleRepository;

    private CreditAppServiceImpl service;

    private LmsActor admin;

    private LmsActor member;

    @BeforeEach
    void setUp() {
        creditLedgerRepository = mock(CreditLedgerRepository.class);
        creditRuleRepository = mock(CreditRuleRepository.class);
        service = new CreditAppServiceImpl(creditLedgerRepository, creditRuleRepository, new LmsProperties());
        admin = new LmsActor(OPERATOR_ID, "admin", Set.of("lms_admin"), Set.of());
        member = new LmsActor(HOLDER_ID, "member", Set.of("lms_member"), Set.of());
        when(creditLedgerRepository.save(any())).thenReturn(SAVED_ID);
        when(creditLedgerRepository.findBySource(anyLong(), any(), anyLong())).thenReturn(Optional.empty());
        when(creditLedgerRepository.count(any())).thenReturn(0L);
        when(creditLedgerRepository.page(any(), anyLong(), anyInt())).thenReturn(List.of());
    }

    @Test
    @DisplayName("命中规则即按规则学分裂算入账")
    void shouldAccrueByMatchedRule() {
        when(creditRuleRepository.findEffective(CreditSource.EXAM, EXAM_ID))
                .thenReturn(Optional.of(rule(new BigDecimal("5.0"))));
        Long id = service.accrue(command(), admin);
        assertEquals(SAVED_ID, id);
        ArgumentCaptor<CreditLedger> captor = ArgumentCaptor.forClass(CreditLedger.class);
        verify(creditLedgerRepository).save(captor.capture());
        CreditLedger saved = captor.getValue();
        assertEquals(new BigDecimal("5.0"), saved.getCredit());
        assertEquals("EXAM-07", saved.getRuleCode());
        assertEquals(2026, saved.getPeriodYear());
        assertEquals(OPERATOR_ID, saved.getRecordedBy());
    }

    @Test
    @DisplayName("同一业务事件重复记账返回已有台账")
    void shouldReturnExistingLedger() {
        CreditLedger existing = ledger(EXISTING_ID, CreditSource.EXAM, "5.0");
        when(creditLedgerRepository.findBySource(HOLDER_ID, CreditSource.EXAM, EXAM_ID))
                .thenReturn(Optional.of(existing));
        assertEquals(EXISTING_ID, service.accrue(command(), admin));
        verify(creditLedgerRepository, never()).save(any());
    }

    @Test
    @DisplayName("未配置规则时拒绝记账，不落 0 分记录")
    void shouldRejectMissingRule() {
        when(creditRuleRepository.findEffective(CreditSource.EXAM, EXAM_ID)).thenReturn(Optional.empty());
        BizException exception = assertThrows(BizException.class, () -> service.accrue(command(), admin));
        assertEquals(LmsErrorCode.CREDIT_RULE_MISSING, exception.getCode());
        verify(creditLedgerRepository, never()).save(any());
    }

    @Test
    @DisplayName("规则停用时拒绝记账")
    void shouldRejectDisabledRule() {
        CreditRule rule = rule(new BigDecimal("5.0"));
        rule.setStatus(CreditRuleStatus.DISABLED);
        when(creditRuleRepository.findEffective(CreditSource.EXAM, EXAM_ID)).thenReturn(Optional.of(rule));
        BizException exception = assertThrows(BizException.class, () -> service.accrue(command(), admin));
        assertEquals(LmsErrorCode.CREDIT_RULE_DISABLED, exception.getCode());
    }

    @Test
    @DisplayName("汇总按来源分组并合计")
    void shouldSummarizeBySource() {
        when(creditLedgerRepository.listByUser(HOLDER_ID, 2026)).thenReturn(List.of(
                ledger(1L, CreditSource.EXAM, "5.0"),
                ledger(2L, CreditSource.EXAM, "3.0"),
                ledger(3L, CreditSource.COURSE, "2.0")));
        CreditSummaryView summary = service.summary(null, 2026, member);
        assertEquals(new BigDecimal("10.0"), summary.getTotalCredit());
        assertEquals(2, summary.getItems().size());
        assertEquals(HOLDER_ID, summary.getUserId());
    }

    @Test
    @DisplayName("非管理员查看他人学分会被拒绝")
    void shouldRejectOthersSummary() {
        BizException exception = assertThrows(BizException.class,
                () -> service.summary(999L, 2026, member));
        assertEquals(LmsErrorCode.CREDIT_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("管理员可查看他人学分")
    void adminShouldReadOthersSummary() {
        when(creditLedgerRepository.listByUser(999L, 2026)).thenReturn(List.of());
        CreditSummaryView summary = service.summary(999L, 2026, admin);
        assertEquals(999L, summary.getUserId());
        assertEquals(BigDecimal.ZERO, summary.getTotalCredit());
    }

    @Test
    @DisplayName("我的学分强制以当前登录人为学员")
    void mineShouldForceCurrentUser() {
        CreditLedgerQuery query = new CreditLedgerQuery();
        query.setUserId(999L);
        PageResult<CreditLedgerView> result = service.mine(query, member);
        assertEquals(0L, result.getTotal());
        assertEquals(HOLDER_ID, query.getUserId());
    }

    /**
     * 构造记账命令。
     *
     * @return 命令
     */
    private CreditAccrueCommand command() {
        CreditAccrueCommand command = new CreditAccrueCommand();
        command.setUserId(HOLDER_ID);
        command.setUsername("zhangsan");
        command.setSourceType(CreditSource.EXAM.name());
        command.setSourceId(EXAM_ID);
        command.setOccurredAt(LocalDateTime.of(2026, 3, 15, 9, 0));
        return command;
    }

    /**
     * 构造规则。
     *
     * @param credit 学分
     * @return 规则
     */
    private CreditRule rule(BigDecimal credit) {
        CreditRule rule = new CreditRule();
        rule.setId(3L);
        rule.setRuleCode("EXAM-07");
        rule.setName("安全生产考试");
        rule.setSourceType(CreditSource.EXAM);
        rule.setMatchId(EXAM_ID);
        rule.setCredit(credit);
        rule.setStatus(CreditRuleStatus.ENABLED);
        return rule;
    }

    /**
     * 构造台账。
     *
     * @param id 台账 ID
     * @param source 来源类型
     * @param credit 学分
     * @return 台账
     */
    private CreditLedger ledger(long id, CreditSource source, String credit) {
        CreditLedger ledger = new CreditLedger();
        ledger.setId(id);
        ledger.setUserId(HOLDER_ID);
        ledger.setSourceType(source);
        ledger.setSourceId(EXAM_ID);
        ledger.setCredit(new BigDecimal(credit));
        ledger.setPeriodYear(2026);
        ledger.setOccurredAt(LocalDateTime.of(2026, 3, 15, 9, 0));
        return ledger;
    }
}
