package com.bluewhale.party.fee.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.fee.application.command.FeePayCommand;
import com.bluewhale.party.fee.application.command.FeeRuleSaveCommand;
import com.bluewhale.party.fee.application.service.FeeAppService;
import com.bluewhale.party.fee.application.view.FeeBillView;
import com.bluewhale.party.fee.application.view.FeeGenerateResultView;
import com.bluewhale.party.fee.application.view.FeeRuleView;
import com.bluewhale.party.fee.domain.model.DuesCalculator;
import com.bluewhale.party.fee.domain.model.FeeBill;
import com.bluewhale.party.fee.domain.model.FeeBillQuery;
import com.bluewhale.party.fee.domain.model.FeeRule;
import com.bluewhale.party.fee.domain.repository.FeeBillRepository;
import com.bluewhale.party.fee.domain.repository.FeeRuleRepository;
import com.bluewhale.party.member.domain.model.PartyMember;
import com.bluewhale.party.member.domain.repository.PartyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 党费台账应用服务实现。
 *
 * <p>生成账单是<b>幂等 + 只增</b>：已生成的月份不重复生成、不覆盖已缴记录。
 * 重复点击「生成本月」不会产生第二份账单（有唯一键兜底），也不会把已缴清的账单
 * 重置成未缴——这一点比「重新生成一次保证数据最新」更重要，因为党费一旦收上来，
 * 台账就不能再被批量操作改写。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class FeeAppServiceImpl implements FeeAppService {

    /** 未匹配档位时最多回显的党员姓名数（够定位问题，又不至于刷屏） */
    private static final int NO_RULE_SAMPLE_LIMIT = 20;

    private final FeeRuleRepository feeRuleRepository;

    private final FeeBillRepository feeBillRepository;

    private final PartyMemberRepository partyMemberRepository;

    private final PartyProperties properties;

    @Override
    public List<FeeRuleView> rules(PartyActor actor) {
        assertGate();
        return feeRuleRepository.listAll().stream().map(FeeRuleView::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeeRuleView saveRule(Long id, FeeRuleSaveCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        FeeRule rule = id == null ? new FeeRule() : feeRuleRepository.findById(id)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "党费档位不存在"));
        rule.setRuleName(command.getRuleName());
        rule.setMinBase(command.getMinBase());
        rule.setMaxBase(command.getMaxBase());
        rule.setRate(command.getRate());
        rule.setStatus(StringUtils.hasText(command.getStatus()) ? command.getStatus() : "ACTIVE");
        rule.setSortNo(command.getSortNo());
        rule.setRemark(StringUtils.hasText(command.getRemark()) ? command.getRemark().trim() : null);
        rule.validate();
        assertNoOverlap(rule);
        feeRuleRepository.save(rule);
        return FeeRuleView.from(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<FeeRuleView> initDefaultRules(PartyActor actor) {
        assertGate();
        actor.requireUserId();
        if (feeRuleRepository.count() > 0) {
            return rules(actor);
        }
        List<FeeRule> defaults = new ArrayList<>();
        defaults.add(defaultRule("3000 元以下", BigDecimal.ZERO, BigDecimal.valueOf(3000), "0.005", 1));
        defaults.add(defaultRule("3000–5000 元", BigDecimal.valueOf(3000), BigDecimal.valueOf(5000), "0.01", 2));
        defaults.add(defaultRule("5000–10000 元", BigDecimal.valueOf(5000), BigDecimal.valueOf(10000), "0.015", 3));
        defaults.add(defaultRule("10000 元以上", BigDecimal.valueOf(10000), null, "0.02", 4));
        for (FeeRule rule : defaults) {
            feeRuleRepository.save(rule);
        }
        return feeRuleRepository.listAll().stream().map(FeeRuleView::from).toList();
    }

    @Override
    public PageResult<FeeBillView> bills(FeeBillQuery query, PartyActor actor) {
        assertGate();
        FeeBillQuery safe = query == null ? new FeeBillQuery() : query;
        long size = Math.min(safe.getSize() == null || safe.getSize() < 1L ? 20L : safe.getSize(),
                properties.getMemberPageLimit());
        long current = safe.getCurrent() == null || safe.getCurrent() < 1L ? 1L : safe.getCurrent();
        List<FeeBillView> views = feeBillRepository.page(safe, (current - 1L) * size, (int) size)
                .stream().map(FeeBillView::from).toList();
        return PageResult.of(views, feeBillRepository.count(safe), current, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeeGenerateResultView generate(String periodMonth, Long branchId, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        String period = periodMonth == null ? "" : periodMonth.trim();
        if (!FeeBill.isValidPeriod(period)) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_INVALID, "所属月份格式应为 yyyyMM");
        }
        List<FeeRule> rules = feeRuleRepository.listActive();
        if (rules.isEmpty()) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_RULE_MISSING,
                    "尚未配置党费标准档位，请先初始化或新建档位");
        }
        FeeGenerateResultView result = new FeeGenerateResultView();
        result.setPeriodMonth(period);
        for (PartyMember member : partyMemberRepository.listPayable(branchId)) {
            if (!member.getStatus().shouldPayDues()) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            if (feeBillRepository.findByMemberAndPeriod(member.getId(), period).isPresent()) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            FeeRule rule = matchOrRecord(member, rules, result);
            if (rule == null) {
                continue;
            }
            FeeBill bill = FeeBill.of(member.getId(), member.getBranchId(), member.getMemberName(),
                    period, member.getDuesBase(), rule);
            bill.validate();
            feeBillRepository.save(bill);
            result.setCreated(result.getCreated() + 1);
            result.setDueTotal(result.getDueTotal().add(bill.getDueAmount()));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeeBillView pay(Long billId, FeePayCommand command, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        FeeBill bill = requireBill(billId);
        bill.pay(command.getAmount(), command.getPaidDate());
        if (StringUtils.hasText(command.getRemark())) {
            bill.setRemark(appendRemark(bill.getRemark(), command.getRemark()));
        }
        bill.validate();
        feeBillRepository.save(bill);
        return FeeBillView.from(bill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeeBillView waive(Long billId, String reason, PartyActor actor) {
        assertGate();
        actor.requireUserId();
        FeeBill bill = requireBill(billId);
        bill.waive(reason);
        bill.validate();
        feeBillRepository.save(bill);
        return FeeBillView.from(bill);
    }

    /**
     * 匹配档位；未命中时计入统计并返回 null（不中断整批生成）。
     *
     * <p>单人基数未配档位不该让整个月的账单都生成失败：把问题记下来，
     * 让其余人能正常生成，缺档位的人补配置后单独重跑即可。</p>
     *
     * @param member 党员
     * @param rules 启用中的档位
     * @param result 生成结果（累计未命中人数）
     * @return 命中的档位；未命中返回 null
     */
    private FeeRule matchOrRecord(PartyMember member, List<FeeRule> rules, FeeGenerateResultView result) {
        try {
            return DuesCalculator.match(member.getDuesBase(), rules);
        } catch (BizException exception) {
            result.setNoRule(result.getNoRule() + 1);
            if (result.getNoRuleMembers().size() < NO_RULE_SAMPLE_LIMIT) {
                result.getNoRuleMembers().add(member.getMemberName());
            }
            return null;
        }
    }

    /**
     * 校验档位区间不与既有启用档位重叠。
     *
     * @param rule 待保存档位
     */
    private void assertNoOverlap(FeeRule rule) {
        if (!rule.isActive()) {
            return;
        }
        for (FeeRule other : feeRuleRepository.listActive()) {
            if (rule.getId() != null && rule.getId().equals(other.getId())) {
                continue;
            }
            if (rule.overlaps(other)) {
                throw PartyErrorCode.exception(PartyErrorCode.FEE_RULE_OVERLAP,
                        "与档位「" + other.getRuleName() + "」的基数区间重叠");
            }
        }
    }

    /**
     * 构造标准档位。
     *
     * @param name 档位名称
     * @param minBase 基数下限
     * @param maxBase 基数上限（可空）
     * @param rate 比例文本
     * @param sortNo 排序号
     * @return 档位
     */
    private FeeRule defaultRule(String name, BigDecimal minBase, BigDecimal maxBase, String rate, int sortNo) {
        FeeRule rule = new FeeRule();
        rule.setRuleName(name);
        rule.setMinBase(minBase);
        rule.setMaxBase(maxBase);
        rule.setRate(new BigDecimal(rate));
        rule.setStatus("ACTIVE");
        rule.setSortNo(sortNo);
        rule.setRemark("标准档位（按中央组织部党费收缴规定）");
        rule.validate();
        return rule;
    }

    /**
     * 追加备注。
     *
     * @param origin 原备注
     * @param extra 追加内容
     * @return 合并后的备注
     */
    private String appendRemark(String origin, String extra) {
        String merged = StringUtils.hasText(origin) ? origin.trim() + "；" + extra.trim() : extra.trim();
        return merged.length() > FeeBill.REMARK_MAX_LENGTH
                ? merged.substring(0, FeeBill.REMARK_MAX_LENGTH) : merged;
    }

    /**
     * 按 ID 取账单，不存在即抛错。
     *
     * @param billId 账单 ID
     * @return 账单
     */
    private FeeBill requireBill(Long billId) {
        if (billId == null || billId <= 0) {
            throw PartyErrorCode.exception(PartyErrorCode.FEE_BILL_NOT_FOUND);
        }
        return feeBillRepository.findById(billId)
                .orElseThrow(() -> PartyErrorCode.exception(PartyErrorCode.FEE_BILL_NOT_FOUND));
    }

    /**
     * 需求门禁。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw PartyErrorCode.exception(PartyErrorCode.GATE_CLOSED);
        }
    }
}
