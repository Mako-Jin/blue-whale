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

    /** 条件表达式文本（运行期参与条件选路；支持直接键名与 #变量 两种写法） */
    private String condition;

    /** 是否为默认流转（排他网关多分支的兜底分支） */
    private Boolean isDefault;
}
