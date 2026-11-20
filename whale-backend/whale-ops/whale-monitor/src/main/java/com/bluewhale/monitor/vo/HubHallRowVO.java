package com.bluewhale.monitor.vo;

/**
 * 数枢殿总控表格的一行（一座殿）。
 *
 * <p>字段全部是<b>展示就绪</b>的：{@code headline}、{@code note}、{@code updatedLabel}
 * 已经是可读文本，前端不再二次格式化 —— 一行里可能混着金额、计数与百分比，
 * 前端没有一份统一口径可用。</p>
 *
 * @param id 行主键（等于 {@code nodeKey}，屏风页的详情请求用它）
 * @param nodeKey 城内节点 key
 * @param name 殿名
 * @param state 连通状态：live / stale / degraded / denied
 * @param headline 主指标文本，例如「营收 ¥123.4万」
 * @param note 说明：刷新时刻或降级原因
 * @param updatedLabel 数据时刻（MM-dd HH:mm）；从未成功时为空串
 * @author BlueWhale
 */
public record HubHallRowVO(
        String id,
        String nodeKey,
        String name,
        String state,
        String headline,
        String note,
        String updatedLabel) {
}
