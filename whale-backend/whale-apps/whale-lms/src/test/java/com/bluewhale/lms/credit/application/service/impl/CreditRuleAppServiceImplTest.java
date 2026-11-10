package com.bluewhale.lms.credit.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.credit.application.command.CreditRuleSaveCommand;
import com.bluewhale.lms.credit.domain.model.CreditRule;
import com.bluewhale.lms.credit.domain.model.CreditRuleStatus;
import com.bluewhale.lms.credit.domain.model.CreditSource;
import com.bluewhale.lms.credit.domain.repository.CreditRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学分规则维护：编码归一查重、同来源同对象只能一条规则。
 *
 * @author BlueWhale
 */
class CreditRuleAppServiceImplTest {

    /** 规则 ID */
    private static final long RULE_ID = 3L;

    /** 匹配对象 ID（考试） */
    private static final long EXAM_ID = 7L;

    private CreditRuleRepository creditRuleRepository;

    private CreditRuleAppServiceImpl service;

    private LmsActor admin;

    @BeforeEach
    void setUp() {
        creditRuleRepository = mock(CreditRuleRepository.class);
        service = new CreditRuleAppServiceImpl(creditRuleRepository, new LmsProperties());
        admin = new LmsActor(9L, "admin", Set.of("lms_admin"), Set.of());
        when(creditRuleRepository.save(any())).thenReturn(RULE_ID);
    }

    @Test
    @DisplayName("新建规则：编码归一为启用状态")
    void shouldCreateRule() {
        when(creditRuleRepository.findByCode("EXAM-07")).thenReturn(Optional.empty());
        when(creditRuleRepository.findByMatch(CreditSource.EXAM, EXAM_ID)).thenReturn(Optional.empty());
        Long id = service.create(command(" exam-07 "), admin);
        assertEquals(RULE_ID, id);
        ArgumentCaptor<CreditRule> captor = ArgumentCaptor.forClass(CreditRule.class);
        verify(creditRuleRepository).save(captor.capture());
        assertEquals("EXAM-07", captor.getValue().getRuleCode());
        assertEquals(CreditRuleStatus.ENABLED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("编码重复时拒绝新建")
    void shouldRejectDuplicateCode() {
        when(creditRuleRepository.findByCode("EXAM-07"))
                .thenReturn(Optional.of(rule(RULE_ID, "EXAM-07", EXAM_ID)));
        BizException exception = assertThrows(BizException.class,
                () -> service.create(command("EXAM-07"), admin));
        assertEquals(LmsErrorCode.CREDIT_RULE_CODE_CONFLICT, exception.getCode());
        verify(creditRuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("同来源同对象已有规则时拒绝，提示改原规则")
    void shouldRejectDuplicateMatch() {
        when(creditRuleRepository.findByCode("EXAM-07-B")).thenReturn(Optional.empty());
        when(creditRuleRepository.findByMatch(CreditSource.EXAM, EXAM_ID))
                .thenReturn(Optional.of(rule(RULE_ID, "EXAM-07", EXAM_ID)));
        BizException exception = assertThrows(BizException.class,
                () -> service.create(command("EXAM-07-B"), admin));
        assertEquals(LmsErrorCode.CREDIT_RULE_MATCH_CONFLICT, exception.getCode());
        assertTrue(exception.getMessage().contains("安全生产考试学分"));
    }

    @Test
    @DisplayName("编辑时同对象命中自己不算冲突")
    void updateShouldIgnoreSelf() {
        CreditRule rule = rule(RULE_ID, "EXAM-07", EXAM_ID);
        when(creditRuleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));
        when(creditRuleRepository.findByMatch(CreditSource.EXAM, EXAM_ID)).thenReturn(Optional.of(rule));
        service.update(RULE_ID, command("EXAM-07"), admin);
        verify(creditRuleRepository).save(rule);
    }

    @Test
    @DisplayName("停用规则写入 DISABLED")
    void shouldDisableRule() {
        CreditRule rule = rule(RULE_ID, "EXAM-07", EXAM_ID);
        when(creditRuleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));
        service.changeStatus(RULE_ID, false, admin);
        assertEquals(CreditRuleStatus.DISABLED, rule.getStatus());
    }

    @Test
    @DisplayName("matchId 为空按通配处理")
    void shouldDefaultMatchToAll() {
        when(creditRuleRepository.findByCode("EXAM-ALL")).thenReturn(Optional.empty());
        when(creditRuleRepository.findByMatch(CreditSource.EXAM, CreditRule.MATCH_ALL)).thenReturn(Optional.empty());
        CreditRuleSaveCommand command = command("EXAM-ALL");
        command.setMatchId(null);
        service.create(command, admin);
        ArgumentCaptor<CreditRule> captor = ArgumentCaptor.forClass(CreditRule.class);
        verify(creditRuleRepository).save(captor.capture());
        assertEquals(CreditRule.MATCH_ALL, captor.getValue().getMatchId());
    }

    /**
     * 构造保存命令。
     *
     * @param code 规则编码
     * @return 命令
     */
    private CreditRuleSaveCommand command(String code) {
        CreditRuleSaveCommand command = new CreditRuleSaveCommand();
        command.setRuleCode(code);
        command.setName("安全生产考试学分");
        command.setSourceType(CreditSource.EXAM.name());
        command.setMatchId(EXAM_ID);
        command.setCredit(new BigDecimal("5.0"));
        return command;
    }

    /**
     * 构造规则。
     *
     * @param id 规则 ID
     * @param code 规则编码
     * @param matchId 匹配对象 ID
     * @return 规则
     */
    private CreditRule rule(long id, String code, long matchId) {
        CreditRule rule = new CreditRule();
        rule.setId(id);
        rule.setRuleCode(code);
        rule.setName("安全生产考试学分");
        rule.setSourceType(CreditSource.EXAM);
        rule.setMatchId(matchId);
        rule.setCredit(new BigDecimal("5.0"));
        rule.setStatus(CreditRuleStatus.ENABLED);
        return rule;
    }
}
