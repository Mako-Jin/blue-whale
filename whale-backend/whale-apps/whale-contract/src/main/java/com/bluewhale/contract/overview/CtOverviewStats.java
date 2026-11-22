package com.bluewhale.contract.overview;

import java.util.List;

/**
 * 盟府（CONTRACT）的一次性统计快照。
 *
 * <p><b>为什么单独一组计数，而不是让概览 provider 直接查 Mapper。</b>
 * 概览 provider 属于 3D 城的取数层，一旦它开始自己写查询条件，
 * "本月到期""已过期仍生效"的判定口径就有两份（2D 台账的 expiringSoon
 * 筛选按一套窗口算，3D 屏风自己数），迟早出现"后台说 3 份、城里说 5 份"。
 * 这里把所有口径收在 {@link CtOverviewStatsService} 一处，provider 只负责排版。</p>
 *
 * <p><b>状态码与 2D 台账同源。</b>合同状态枚举 {@code ContractStatus} 有十一个值，
 * 屏风的牌只聚合成"生效 / 在途 / 到期 / 过期"四类；驳回与撤回合并在
 * {@code rejectWithdrawn} 里——两件事对盟府都是"这版没成，得重来"，
 * 分开报不改变要做的动作。</p>
 *
 * @param total           合同总数（含所有状态）
 * @param effective       生效中合同数（status = EFFECTIVE）
 * @param inFlight        在途合同数（审批中 + 已批待签 + 签署中 + 已签待生效）
 * @param approving       审批中的合同数
 * @param signing         签署中的合同数
 * @param draft           起草未提交的合同数（status = DRAFT）
 * @param rejectWithdrawn 被驳回或撤回的合同数（REJECTED + WITHDRAWN）
 * @param archived        已归档合同数
 * @param terminated      已终止合同数（含作废 VOID）
 * @param expiringSoon    30 天内到期的生效中合同数（与 2D 台账 expiringSoon 同窗口）
 * @param expiredActive   已过期但仍在生效的合同数（expireDate 早于今天且 status = EFFECTIVE）
 * @param effectiveMoney  生效中合同按类别的金额合计（CNY；外币不计入）
 * @param foreignCurrency 生效中的外币合同数（未计入金额图，需单独提示）
 * @author BlueWhale
 */
public record CtOverviewStats(
        long total,
        long effective,
        long inFlight,
        long approving,
        long signing,
        long draft,
        long rejectWithdrawn,
        long archived,
        long terminated,
        long expiringSoon,
        long expiredActive,
        List<CategoryAmount> effectiveMoney,
        long foreignCurrency) {

    /**
     * 类别金额：图表的横轴标签在服务端定死，屏风不再翻译码值。
     *
     * @param label  中文标签（采购 / 销售 / 服务 / 租赁 / 其他）
     * @param amount 金额合计（生效中、CNY）
     */
    public record CategoryAmount(String label, double amount) {
    }
}
