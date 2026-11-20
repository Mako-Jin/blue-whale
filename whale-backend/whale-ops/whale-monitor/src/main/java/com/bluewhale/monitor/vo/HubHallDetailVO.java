package com.bluewhale.monitor.vo;

import java.util.List;

/**
 * 总控里一座殿的详情（屏风页点行之后的那一页）。
 *
 * <p>详情不去问下游第二次：它就是从同一次聚合里取该殿的快照展开，
 * 因此"表格里的数"和"详情里的数"必然同一时刻 —— 点进去看到不一样才是最伤信任的。</p>
 *
 * @param nodeKey 城内节点 key
 * @param name 殿名
 * @param state 连通状态
 * @param subtitle 下游给的口径与期间，可空
 * @param updatedLabel 数据时刻（MM-dd HH:mm）；从未成功时为空串
 * @param note 说明：降级原因、口径备注与下游告警
 * @param lines 该殿全部 KPI（已排版）
 * @author BlueWhale
 */
public record HubHallDetailVO(
        String nodeKey,
        String name,
        String state,
        String subtitle,
        String updatedLabel,
        String note,
        List<HubLineVO> lines) {
}
