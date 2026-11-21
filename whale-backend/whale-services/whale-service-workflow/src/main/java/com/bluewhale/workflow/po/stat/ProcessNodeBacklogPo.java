package com.bluewhale.workflow.po.stat;

import lombok.Data;

/**
 * 按审批节点聚合的待办积压（政事殿概览 T5.6）。
 *
 * <p>按 {@code node_name} 分组而不是 {@code node_key}：这是给人看的柱图，
 * 节点键是 {@code n2}、{@code task_5f3a} 这类机器名，读不出"堵在哪个环节"。</p>
 *
 * @author BlueWhale
 */
@Data
public class ProcessNodeBacklogPo {

    /** 节点名称（历史数据允许为空，由口径实现折成"未命名节点"） */
    private String nodeName;

    /** 该节点的待办数 */
    private Long pending;
}
