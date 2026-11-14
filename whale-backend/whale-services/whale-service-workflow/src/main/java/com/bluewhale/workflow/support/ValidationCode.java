package com.bluewhale.workflow.support;

/**
 * 流程定义校验码：错误码与警告码的权威枚举。
 *
 * <p>错误码（{@link ValidationLevel#ERROR}）使 {@code valid=false}；警告码
 * （{@link ValidationLevel#WARNING}）仅提示不阻断。XML 侧校验码由
 * {@code support.bpmn.BpmnXmlParser} / {@code BpmnXmlSupport} 产出。</p>
 *
 * @author BlueWhale
 */
public enum ValidationCode {

    /** 缺少 nodes（或 nodes 为空） */
    MISSING_NODES(ValidationLevel.ERROR, "流程定义缺少节点"),

    /** 定义 type 非空但不是 stateMachine */
    UNKNOWN_DEFINITION_TYPE(ValidationLevel.ERROR, "未知的流程定义类型"),

    /** 节点缺少 key */
    NODE_KEY_MISSING(ValidationLevel.ERROR, "节点缺少 key"),

    /** 节点 key 不合法（字符集/长度） */
    NODE_KEY_INVALID(ValidationLevel.ERROR, "节点 key 不合法"),

    /** 节点 key 重复 */
    NODE_KEY_DUPLICATE(ValidationLevel.ERROR, "节点 key 重复"),

    /** 节点 type 缺失或不识别 */
    UNKNOWN_NODE_TYPE(ValidationLevel.ERROR, "未知的节点类型"),

    /** 审批节点缺少 assignee */
    USER_TASK_ASSIGNEE_REQUIRED(ValidationLevel.ERROR, "审批节点缺少 assignee"),

    /** 审批节点表单编码非法（非空且不匹配 {@code ^[A-Za-z0-9_-]{1,64}$}） */
    USER_TASK_FORM_CODE_INVALID(ValidationLevel.ERROR, "审批节点表单编码不合法"),

    /** 审批节点表单绑定不完整（formId 与 formCode 必须成对非空） */
    USER_TASK_FORM_INCOMPLETE(ValidationLevel.ERROR, "审批节点表单绑定不完整"),

    /** 开始事件数量不等于 1 */
    START_EVENT_COUNT(ValidationLevel.ERROR, "开始事件数量必须为 1"),

    /** 缺少结束事件 */
    END_EVENT_REQUIRED(ValidationLevel.ERROR, "流程缺少结束事件"),

    /** 流转 key 重复 */
    FLOW_KEY_DUPLICATE(ValidationLevel.ERROR, "流转 key 重复"),

    /** 流转缺少 key */
    FLOW_KEY_MISSING(ValidationLevel.ERROR, "流转缺少 key"),

    /** 流转引用了不存在的节点 */
    FLOW_REFERENCE_MISSING(ValidationLevel.ERROR, "流转引用了不存在的节点"),

    /** 流转自环（from == to） */
    FLOW_SELF_LOOP(ValidationLevel.ERROR, "流转不能自环"),

    /** 非结束事件节点无出流（悬空） */
    DEAD_END(ValidationLevel.ERROR, "节点无出流且非结束事件"),

    /** 存在从入口不可达的节点 */
    UNREACHABLE_NODE(ValidationLevel.ERROR, "存在不可达节点"),

    /** 流程存在环 */
    CYCLE_DETECTED(ValidationLevel.ERROR, "流程存在环"),

    /** 网关没有出流 */
    GATEWAY_NO_OUTGOING(ValidationLevel.ERROR, "网关没有出流"),

    /** 网关多分支缺少默认流转（isDefault） */
    GATEWAY_MULTI_BRANCH_NO_DEFAULT(ValidationLevel.ERROR, "网关多分支缺少默认流转"),

    /** 节点数超出上限 */
    TOO_MANY_NODES(ValidationLevel.ERROR, "节点数超出上限"),

    /** 流转数超出上限 */
    TOO_MANY_FLOWS(ValidationLevel.ERROR, "流转数超出上限"),

    /** XML 不是良构文档 */
    XML_NOT_WELL_FORMED(ValidationLevel.ERROR, "XML 不是合法格式"),

    /** XML 含 DOCTYPE（禁止，防 XXE） */
    XML_DOCTYPE_FORBIDDEN(ValidationLevel.ERROR, "XML 禁止使用 DOCTYPE"),

    /** XML 体积超出上限 */
    XML_TOO_LARGE(ValidationLevel.ERROR, "XML 体积超出上限"),

    /** BPMN 根元素无效 */
    BPMN_ROOT_INVALID(ValidationLevel.ERROR, "BPMN 根元素无效"),

    /** BPMN process 数量不为 1 */
    BPMN_PROCESS_COUNT(ValidationLevel.ERROR, "BPMN 必须且只能包含一个 process"),

    /** BPMN 含不支持的元素 */
    BPMN_UNSUPPORTED_ELEMENT(ValidationLevel.ERROR, "BPMN 包含不支持的元素"),

    /** 流转条件表达式语法非法（SpEL 解析失败） */
    CONDITION_SYNTAX_INVALID(ValidationLevel.ERROR, "流转条件表达式语法非法"),

    /** 流转条件表达式超长 */
    CONDITION_TOO_LONG(ValidationLevel.ERROR, "流转条件表达式长度超出上限"),

    /** 流程变量求值失败（运行期） */
    CONDITION_EVALUATION_FAILED(ValidationLevel.ERROR, "流转条件求值失败"),

    /** 多分支条件均未命中且无默认流转（运行期） */
    NO_MATCHING_FLOW(ValidationLevel.ERROR, "无匹配的流转分支"),

    /** 单次推进的网关透传跳数/步数超出上限 */
    GATEWAY_HOPS_EXCEEDED(ValidationLevel.ERROR, "网关流转超出上限"),

    /** 会签节点缺少 assignees */
    COUNTER_SIGN_ASSIGNEES_REQUIRED(ValidationLevel.ERROR, "会签节点缺少 assignees"),

    /** 会签策略非法（须为 ALL / ANY / RATIO:n） */
    COUNTER_SIGN_POLICY_INVALID(ValidationLevel.ERROR, "会签策略不合法"),

    /** 抄送节点缺少 assignees（收件人） */
    CC_TASK_RECIPIENTS_REQUIRED(ValidationLevel.ERROR, "抄送节点缺少收件人 assignees"),

    /** 分组 key 缺失或不合法（字符集/长度） */
    GROUP_KEY_INVALID(ValidationLevel.ERROR, "分组 key 不合法"),

    /** 分组 key 重复 */
    GROUP_KEY_DUPLICATE(ValidationLevel.ERROR, "分组 key 重复"),

    /** 节点引用了不存在的分组 */
    NODE_GROUP_UNKNOWN(ValidationLevel.ERROR, "节点引用了不存在的分组"),

    /** 分组数超出上限 */
    TOO_MANY_GROUPS(ValidationLevel.ERROR, "分组数超出上限"),

    /** 节点超时时长非法（须为正整数且不超过上限） */
    NODE_TIMEOUT_INVALID(ValidationLevel.ERROR, "节点超时时长不合法");

    /** 级别 */
    private final ValidationLevel level;

    /** 默认提示文案 */
    private final String defaultMessage;

    /**
     * 构造校验码。
     *
     * @param level 级别
     * @param defaultMessage 默认提示文案
     */
    ValidationCode(ValidationLevel level, String defaultMessage) {
        this.level = level;
        this.defaultMessage = defaultMessage;
    }

    /**
     * 获取级别。
     *
     * @return 级别
     */
    public ValidationLevel getLevel() {
        return level;
    }

    /**
     * 获取默认提示文案。
     *
     * @return 默认提示文案
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * 是否警告级（不阻断）。
     *
     * @return 警告级返回 {@code true}
     */
    public boolean isWarning() {
        return level == ValidationLevel.WARNING;
    }
}
