package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 驳回（退回）选项：供审批页渲染驳回弹窗。
 *
 * @author BlueWhale
 */
@Data
public class TaskRejectOptionsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前任务是否允许驳回 */
    private boolean rejectable;

    /** 不允许驳回时的原因（如「发起节点不支持驳回」） */
    private String reason;

    /** 是否可退回发起人 */
    private boolean initiator;

    /** 唯一的上一审批节点；为空表示没有或存在多个（此时只能指定节点） */
    private NodeOption previous;

    /** 可选的上游审批节点（按定义顺序） */
    private List<NodeOption> candidates = new ArrayList<>();

    /** 是否会签节点（会签由策略自动决定退回目标，忽略请求中的 target） */
    private boolean counterSign;

    /** 会签策略（仅会签节点返回，如 {@code ALL}/{@code ANY}/{@code RATIO:60}） */
    private String policy;

    /**
     * 节点选项。
     */
    @Data
    public static class NodeOption implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 节点键 */
        private String nodeKey;

        /** 节点名称 */
        private String nodeName;

        /**
         * 构造节点选项。
         *
         * @param nodeKey 节点键
         * @param nodeName 节点名称
         */
        public NodeOption(String nodeKey, String nodeName) {
            this.nodeKey = nodeKey;
            this.nodeName = nodeName;
        }
    }
}
