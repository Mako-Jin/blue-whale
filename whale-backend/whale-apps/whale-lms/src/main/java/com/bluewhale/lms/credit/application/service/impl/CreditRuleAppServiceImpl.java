package com.bluewhale.lms.credit.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.credit.application.command.CreditRuleSaveCommand;
import com.bluewhale.lms.credit.application.service.CreditRuleAppService;
import com.bluewhale.lms.credit.application.view.CreditRuleView;
import com.bluewhale.lms.credit.domain.model.CreditRule;
import com.bluewhale.lms.credit.domain.model.CreditRuleQuery;
import com.bluewhale.lms.credit.domain.model.CreditRuleStatus;
import com.bluewhale.lms.credit.domain.model.CreditSource;
import com.bluewhale.lms.credit.domain.repository.CreditRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学分规则应用服务实现。
 *
 * <p>规则表上有一条容易被忽略的唯一约束：{@code (租户, 来源, 匹配对象)} 只能有一条规则。
 * 没有它，「精确匹配」本身就可能命中多条，记账时只能任取一条——同一批学员拿到的学分
 * 可能不一样，而台账看起来又都「有规则依据」。所以冲突必须在保存时拦住，
 * 并把话说清楚：要改某门课的学分，请改那条规则，而不是再加一条。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CreditRuleAppServiceImpl implements CreditRuleAppService {

    private final CreditRuleRepository creditRuleRepository;

    private final LmsProperties properties;

    @Override
    public PageResult<CreditRuleView> page(CreditRuleQuery query, LmsActor actor) {
        assertGate();
        long current = Math.max(query.getCurrent() == null ? 1L : query.getCurrent(), 1L);
        long size = Math.min(Math.max(query.getSize() == null ? 20L : query.getSize(), 1L),
                properties.getCertPageLimit());
        long total = creditRuleRepository.count(query);
        List<CreditRuleView> views = creditRuleRepository.page(query, (current - 1) * size, (int) size)
                .stream().map(CreditRuleView::from).toList();
        return PageResult.of(views, total, current, size);
    }

    @Override
    public CreditRuleView detail(Long id, LmsActor actor) {
        assertGate();
        return CreditRuleView.from(requireRule(id));
    }

    @Override
    @Transactional
    public Long create(CreditRuleSaveCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        CreditRule rule = new CreditRule();
        rule.setRuleCode(requireUniqueCode(command.getRuleCode(), null));
        rule.setStatus(CreditRuleStatus.ENABLED);
        applyCommand(rule, command);
        rule.validateBasics();
        assertMatchFree(rule.getSourceType(), rule.getMatchId(), null);
        return creditRuleRepository.save(rule);
    }

    @Override
    @Transactional
    public void update(Long id, CreditRuleSaveCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        CreditRule rule = requireRule(id);
        rule.setRuleCode(requireUniqueCode(command.getRuleCode(), rule.getRuleCode()));
        applyCommand(rule, command);
        rule.validateBasics();
        assertMatchFree(rule.getSourceType(), rule.getMatchId(), id);
        creditRuleRepository.save(rule);
    }

    @Override
    @Transactional
    public void delete(Long id, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        requireRule(id);
        creditRuleRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, boolean enabled, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        CreditRule rule = requireRule(id);
        rule.setStatus(enabled ? CreditRuleStatus.ENABLED : CreditRuleStatus.DISABLED);
        creditRuleRepository.save(rule);
    }

    /**
     * 断言需求门禁通过。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw LmsErrorCode.exception(LmsErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 加载规则，不存在按 404 处理。
     *
     * @param id 规则 ID
     * @return 规则
     */
    private CreditRule requireRule(Long id) {
        return creditRuleRepository.findById(id)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.CREDIT_RULE_NOT_FOUND));
    }

    /**
     * 归一编码并校验唯一性。
     *
     * @param rawCode 原始编码
     * @param currentCode 当前编码（编辑时用于跳过自身）
     * @return 归一后的编码
     */
    private String requireUniqueCode(String rawCode, String currentCode) {
        String code = CreditRule.normalizeCode(rawCode);
        if (currentCode != null && code.equals(currentCode)) {
            return code;
        }
        creditRuleRepository.findByCode(code).ifPresent(existing -> {
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_RULE_CODE_CONFLICT);
        });
        return code;
    }

    /**
     * 校验同一来源对象未被其他规则占用。
     *
     * @param sourceType 来源类型
     * @param matchId 匹配对象 ID
     * @param currentId 当前规则 ID（编辑时排除自身）
     */
    private void assertMatchFree(CreditSource sourceType, Long matchId, Long currentId) {
        creditRuleRepository.findByMatch(sourceType, matchId).ifPresent(existing -> {
            if (currentId != null && currentId.equals(existing.getId())) {
                return;
            }
            String scope = matchId == null || matchId == CreditRule.MATCH_ALL
                    ? "该来源的全部对象" : "该来源对象（ID " + matchId + "）";
            throw LmsErrorCode.exception(LmsErrorCode.CREDIT_RULE_MATCH_CONFLICT,
                    scope + "已配置规则「" + existing.getName() + "」，请直接修改该规则");
        });
    }

    /**
     * 将保存命令写入规则。
     *
     * @param rule 目标规则
     * @param command 保存命令
     */
    private void applyCommand(CreditRule rule, CreditRuleSaveCommand command) {
        rule.setName(command.getName().trim());
        rule.setSourceType(CreditSource.require(command.getSourceType()));
        rule.setMatchId(command.getMatchId() == null || command.getMatchId() < 0
                ? CreditRule.MATCH_ALL : command.getMatchId());
        rule.setCredit(command.getCredit() == null ? BigDecimal.ZERO : command.getCredit());
        rule.setRemark(command.getRemark());
    }
}
