package com.bluewhale.contract.overview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.contract.domain.ContractStatus;
import com.bluewhale.contract.entity.CtContract;
import com.bluewhale.contract.mapper.CtContractMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 盟府（CONTRACT）统计口径：合同台账一本账，按状态与到期窗口切面。
 *
 * <p><b>到期窗口与 2D 台账同刻度。</b>"30 天内到期"直接沿用
 * {@code ContractServiceImpl} 的 {@code EXPIRING_DAYS = 30}——
 * 台账的"即将到期"筛选与屏风的"本月到期"牌必须是同一扇窗户，
 * 否则屋里屋外看到的到期数对不上。</p>
 *
 * <p><b>"已过期仍生效"比"到期"更要紧。</b>到期日过了但状态还在 EFFECTIVE，
 * 意味着这份合同要么该续签、要么该终止，却没人动它——它不会出现在
 * 任何"即将到期"清单里（窗口往后看，不回头看），这正是要单摆一张牌的原因。</p>
 *
 * <p><b>金额只合计 CNY。</b>合同带 {@code currency} 字段，人民币与美元直接相加
 * 是把两种东西当一个数；外币合同的数量单独统计，在告警里提示"有 N 份外币
 * 合同未计入金额图"，不悄悄丢数。</p>
 *
 * <p><b>不写 GROUP BY。</b>类别只有五种且码值固定（PURCHASE / SALES / SERVICE /
 * LEASE / OTHER），按类别各 count 一次比 CSV 聚合直观；金额合计只查生效中
 * 合同的三个列（类别 / 金额 / 币种），不拉全行。</p>
 *
 * <p>统计只读，不写库：概览被前端按 30s 轮询，任何写操作都会变成"每 30 秒写一次"。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CtOverviewStatsService {

    /** 到期提醒窗口（天）：与 ContractServiceImpl 的 EXPIRING_DAYS 同值 */
    private static final long EXPIRING_DAYS = 30L;

    /** 币种：人民币（金额图只合计这一种） */
    private static final String CURRENCY_CNY = "CNY";

    private final CtContractMapper contractMapper;

    /**
     * 取一次统计快照。
     *
     * @return 统计快照
     */
    public CtOverviewStats snapshot() {
        long total = count(contractMapper.selectCount(null));
        long effective = countByStatus(ContractStatus.EFFECTIVE);
        long approving = countByStatus(ContractStatus.APPROVING);
        long signing = countByStatus(ContractStatus.SIGNING);
        long draft = countByStatus(ContractStatus.DRAFT);
        long rejectWithdrawn = countByStatus(ContractStatus.REJECTED)
                + countByStatus(ContractStatus.WITHDRAWN);
        long archived = countByStatus(ContractStatus.ARCHIVED);
        long terminated = countByStatus(ContractStatus.TERMINATED)
                + countByStatus(ContractStatus.VOID);
        // 已批待签 + 已签待生效与审批/签署同属"在途"：进了流程但还没生效
        long approved = countByStatus(ContractStatus.APPROVED);
        long signed = countByStatus(ContractStatus.SIGNED);

        LocalDate today = LocalDate.now();
        long expiringSoon = count(contractMapper.selectCount(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getStatus, ContractStatus.EFFECTIVE.name())
                .between(CtContract::getExpireDate, today, today.plusDays(EXPIRING_DAYS))));
        long expiredActive = count(contractMapper.selectCount(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getStatus, ContractStatus.EFFECTIVE.name())
                .lt(CtContract::getExpireDate, today)));

        MoneyTotals money = effectiveMoney();
        return new CtOverviewStats(total, effective,
                approving + approved + signing + signed,
                approving, signing, draft, rejectWithdrawn, archived, terminated,
                expiringSoon, expiredActive, money.amounts(), money.foreignCurrency());
    }

    /**
     * 按状态计合同数。
     *
     * @param status 合同状态
     * @return 计数
     */
    private long countByStatus(ContractStatus status) {
        return count(contractMapper.selectCount(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getStatus, status.name())));
    }

    /**
     * 生效中合同的类别金额合计。
     *
     * <p>只 select 类别 / 金额 / 币种三列：金额图不需要正文与版本号。
     * 外币合同不参与合计（不同币种相加没有意义），数量单独返回，
     * 让 provider 在告警里把这笔账交代清楚。</p>
     *
     * @return 类别金额与外币合同数
     */
    private MoneyTotals effectiveMoney() {
        List<CtContract> rows = contractMapper.selectList(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getStatus, ContractStatus.EFFECTIVE.name())
                .select(CtContract::getCategory, CtContract::getAmount, CtContract::getCurrency));
        List<CtOverviewStats.CategoryAmount> amounts = new ArrayList<>(5);
        appendAmount(amounts, rows, "采购", "PURCHASE");
        appendAmount(amounts, rows, "销售", "SALES");
        appendAmount(amounts, rows, "服务", "SERVICE");
        appendAmount(amounts, rows, "租赁", "LEASE");
        appendAmount(amounts, rows, "其他", "OTHER");
        long foreign = rows.stream()
                .filter(row -> row.getCurrency() != null && !CURRENCY_CNY.equals(row.getCurrency()))
                .count();
        return new MoneyTotals(amounts, foreign);
    }

    /**
     * 合计一个类别并追加。
     *
     * @param amounts 目标列表
     * @param rows    生效中合同行
     * @param label   中文标签
     * @param category 类别码值
     */
    private static void appendAmount(List<CtOverviewStats.CategoryAmount> amounts,
            List<CtContract> rows, String label, String category) {
        BigDecimal sum = rows.stream()
                .filter(row -> category.equals(row.getCategory()))
                .filter(row -> row.getCurrency() == null || CURRENCY_CNY.equals(row.getCurrency()))
                .map(CtContract::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        amounts.add(new CtOverviewStats.CategoryAmount(label, sum.doubleValue()));
    }

    /**
     * 计数兜底：{@code selectCount} 在极端情况下返回 {@code null}，直接拆箱就是 NPE。
     *
     * @param value 计数值，可空
     * @return 非空计数
     */
    private static long count(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 金额合计的中间结果。
     *
     * @param amounts        五个类别的金额合计（顺序固定，与屏风图例一致）
     * @param foreignCurrency 外币合同数
     */
    private record MoneyTotals(List<CtOverviewStats.CategoryAmount> amounts, long foreignCurrency) {
    }
}
