package com.bluewhale.workflow.support;

import lombok.Data;

/**
 * 流程模型流转（规范化 JSON 的 {@code flows[]} 元素），对应 BPMN 的 {@code sequenceFlow}。
 *
 * @author BlueWhale
 */
@Data
public class ProcessModelFlow {

    /** 流转键（流程内唯一） */
    private String key;

    /** 源节点键 */
    private String from;

    /** 目标节点键 */
    private String to;

    /** 条件表达式文本（本轮仅保留往返，运行期不参与选路） */
    private String condition;

    /** 是否为默认流转（排他网关多分支的兜底分支） */
    private Boolean isDefault;
}
