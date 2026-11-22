#!/usr/bin/env python3
"""业务异常中文文案 → i18n key 迁移工具。

把 ``new BizException(..., "中文")`` 改写为
``new BizException(..., "biz.<module>.<slug>", "中文模板", args...)``。

动态拼接（如 ``"配置键已存在: " + key``）会转成 ``{0}`` 占位符并把表达式作为
格式化参数传给构造器；GlobalExceptionHandler 通过 MessageSource 按 Locale 解析。

用法：
  python migrate_biz_messages.py scan [--app <module>]
  python migrate_biz_messages.py migrate [--app <module>] [--commit]
  python migrate_biz_messages.py check
"""
from __future__ import annotations

import argparse
import re
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import migrate_validation_messages as vm  # noqa: E402

BACKEND = vm.BACKEND
HAN = vm.HAN
TERMS = dict(vm.TERMS)
BIZ_TERMS = {
    "快照": "snapshot", "发布": "publish", "已发布": "published", "未发布": "unpublished",
    "作废": "void", "封批": "batch closure", "消耗": "consumption", "子件": "component",
    "父件": "parent part", "源": "source", "目标": "target", "派生": "derive",
    "取消": "cancel", "取消后": "after cancellation", "不允许": "not allowed",
    "不能": "cannot", "不可": "cannot", "可以": "can", "必须": "must", "不得": "must not",
    "早于": "earlier than", "晚于": "later than", "超过": "exceed", "上限": "limit",
    "下限": "lower limit", "明细": "detail", "报工": "work report", "派工": "dispatch",
    "回调": "callback", "报文": "payload", "验签": "signature verification",
    "窗口": "window", "停用": "disabled", "启用": "enabled", "关闭": "close",
    "解决": "resolve", "生效": "take effect", "待处理": "pending", "草稿": "draft",
    "自身": "itself", "同级": "same level", "重复": "duplicate", "存在": "exist",
    "不足": "insufficient", "高于": "above", "低于": "below", "允许": "allow",
    "已存在": "already exists", "不存在": "not found", "未配置": "not configured",
    "当前状态": "current status", "指标": "metric", "启用": "enabled", "停用": "disabled",
    "异常": "exception", "结论": "conclusion", "结论格式": "conclusion format",
    "校验": "verification", "解析": "parse", "序列化": "serialize", "响应": "response",
    "空响应": "empty response", "缺少": "missing", "缺少事件": "missing event",
    "ID": "ID", "编码": "code", "阈值": "threshold", "样本": "sample", "子组": "subgroup",
    "工序": "operation", "工单": "work order", "计划": "plan", "版本": "version",
    "文件": "file", "文档": "document", "目录": "directory", "分类": "category",
    "物料": "material", "零件": "part", "库存": "inventory", "采购": "purchase",
    "订单": "order", "合同": "contract", "印章": "seal", "签署": "sign",
    "审批": "approval", "申请": "application", "变更": "change", "通知": "notification",
    "任务": "task", "项目": "project", "看板": "kanban", "迭代": "iteration",
    "状态": "status", "类型": "type", "名称": "name", "数量": "quantity",
    "日期": "date", "时间": "time", "用户": "user", "权限": "permission",
    "服务": "service", "失败": "failed", "超时": "timeout", "重试": "retry",
    "同步": "sync", "下发": "dispatch", "推送": "push", "读取": "read",
    "写入": "write", "生成": "generate", "导出": "export", "导入": "import",
    "上传": "upload", "下载": "download", "关联": "associate", "绑定": "bind",
    "参考": "reference", "来源": "source", "目标": "target", "执行": "execute",
    "完成": "complete", "开始": "start", "结束": "end", "关闭": "close",
    "有效": "valid", "无效": "invalid", "合法": "valid", "非法": "invalid",
    "仅": "only", "同一": "same", "其他": "other", "本人": "self",
    "重复": "duplicate", "子": "sub", "父": "parent", "下": "under",
    "字段": "field", "参数": "parameter", "配置": "configuration", "内容": "content",
    "处理": "handling", "方式": "method", "动作": "action", "事件": "event",
    "记录": "record", "数据": "data", "格式": "format", "范围": "range",
    "工作项": "work item", "父工作项": "parent work item", "子工作项": "child work item",
    "工单": "work order", "工艺路线": "routing", "检验": "inspection", "保养": "maintenance",
    "停机": "downtime", "备件": "spare part", "不良": "defect", "合格": "qualified",
    "返工": "rework", "扫码": "scan", "条码": "barcode", "流水": "serial",
    "发件箱": "outbox", "游标": "cursor", "重新加载": "reload",
    "入档": "archive", "签章件": "sealed document", "主数据": "master data",
    "电子签": "e-sign", "已": "", "未": "not ", "返回": "return", "单号": "number",
    "缺失": "missing", "未知": "unknown", "无法": "cannot", "补偿": "compensate",
    "只能": "can only", "一次": "one", "主体": "subject", "请勿": "please do not",
    "同时": "at the same time", "填写": "fill in", "撤回": "withdraw",
    "内网": "intranet", "驳回": "reject", "通过": "pass", "成品": "finished product",
    "部件": "component", "回滚": "rollback", "修改": "modify", "删除": "delete",
    "提交": "submit", "开工": "start work", "尚未": "not yet", "均": "all",
    "给出": "provide", "项名": "item name", "应为": "should be", "净": "net",
    "准备": "setup", "过": "past", "到": "reach", "领料单": "material requisition",
    "退换货": "return/exchange", "只读": "read-only", "谱系": "lineage",
    "进行中": "in progress", "启动": "start", "前": "before", "需": "need",
    "超长": "too long", "图": "drawing", "子项": "child item", "可用": "available",
    "开启": "enabled", "对象": "object", "过多": "too many", "排序": "sort",
    "空间": "space", "锚点": "anchor", "暂": "temporarily", "在线": "online",
    "形成": "form", "环": "cycle", "结构树": "structure tree", "根": "root",
    "结束日期": "end date", "生效日期": "effective date", "开工时间": "start time",
    "完工时间": "completion time", "已取消": "cancelled", "取消": "cancel",
    "重复": "duplicate", "检查": "check", "到位": "in place", "带": "with",
    "合计": "total", "更换": "replace", "审计": "audit", "上级": "parent",
    "继续": "continue", "作为": "as", "项目管理员": "project administrator",
    "派工状态": "dispatch status", "全部": "all", "属于": "belong to",
    "保养项": "maintenance item", "负": "negative", "原图": "original image",
    "图片": "image", "事件类型": "event type", "允许": "allow",
    "审批流程": "approval workflow", "定义": "definition", "设置": "set",
    "编号生成": "number generation", "谱系链接": "lineage link", "同零件": "same part",
    "启动迭代": "start iteration", "报文": "payload", "时间戳": "timestamp",
    "样本序号": "sample number", "已满": "full", "应开启": "should open",
    "子工作项": "child work item", "迁移": "migrate", "完工回传": "completion callback",
    "实际运行时间": "actual runtime", "大于": "greater than", "计划时间": "planned time",
    "功能": "feature", "工作流": "workflow", "已取消": "cancelled",
    "拆分": "split", "变更为": "change to", "工序顺序": "operation sequence",
    "计划数量": "planned quantity", "等于": "equal", "不能再": "cannot",
    "无工序": "has no operation", "手工": "manually", "一道工序": "one operation",
    "排班": "shift schedule", "编辑": "edit", "布尔": "boolean",
    "配置值": "configuration value", "平台操作": "platform operation", "中断": "interrupted",
    "开始与结束日期": "start and end dates", "提供": "provide", "开工门禁": "start gate",
    "归档目标表": "archive target table", "不受支持": "unsupported",
    "当前变更单": "current change order", "不支持": "not supported", "录入": "enter",
    "检验结果": "inspection result", "影响对象": "affected object", "必须提供": "must provide",
    "对象ID": "object ID", "所选": "selected", "车间": "workshop",
    "工作中心": "work center", "报废数量": "scrap quantity", "剩余": "remaining",
    "可执行": "executable", "损耗率": "loss rate", "之间": "between",
    "空间": "space", "锚点": "anchor", "正确": "correct", "文件内容": "file content",
    "校验和": "checksum", "计算": "calculate", "检出": "check out", "检入": "check in",
    "重试": "retry", "无权": "no permission", "管理": "manage", "资源": "resource",
    "访问": "access", "识别": "identify", "操作人": "operator", "有效期起": "validity start",
    "有效期止": "validity end", "期间": "period", "锁定": "locked", "禁止": "forbidden",
    "重算": "recalculate", "未找到": "not found", "相对方": "counterparty",
    "登记": "register", "签署人": "signer", "用章授权": "seal authorization",
    "当前用户": "current user", "用于": "used for", "降级": "degrade",
    "人工": "manually", "流转": "transition", "流转配置": "transition configuration",
    "相关": "related", "尚未接入": "not integrated", "实现": "implement",
    "概览节点": "overview node", "请用": "please use", "指定": "specify",
    "标准工时": "standard hours", "产生": "generate", "检验任务": "inspection task",
    "检验项": "inspection item", "上下限": "upper and lower limits", "判定": "judge",
    "应检": "required", "已检": "checked", "榜单": "leaderboard", "内容类型": "content type",
    "没有": "no", "失败通知": "failed notification", "派工单": "dispatch order",
    "无明细": "has no detail", "无法撤回": "cannot withdraw", "消耗数量": "consumption quantity",
    "投入量": "input quantity", "可消耗": "can consume", "业务ID": "business ID",
    "相同": "same", "保留零件": "retained part", "主体": "main body", "结构中": "in the structure",
    "使用": "used", "父子": "parent-child", "关系": "relationship",
    "片单": "piece list", "发布状态": "publish status", "负数": "negative number",
    "主版本": "major version", "次版本": "minor version", "仍被": "still",
    "引用": "reference", "班次": "shift", "开始时间": "start time", "结束时间": "end time",
    "提前": "in advance", "用户ID": "user ID", "必须为数字": "must be numeric",
    "用户身份": "user identity", "目标列": "target column", "WIP 上限": "WIP limit",
    "先完成": "complete first", "进行中的": "in-progress",
    "目标版本号": "target version number", "目标状态": "target status",
    "当前看板列": "current kanban column", "确认令牌": "confirmation token",
    "过期": "expired", "重新发起": "restart", "签署任务": "signing task",
    "拒签": "refuse signing", "累计报工数量": "cumulative reported quantity",
    "已派工数量": "dispatched quantity", "线边库存": "line-side inventory", "余额": "balance",
    "统计区间": "statistics period", "1 年": "1 year", "统计日期": "statistics date",
    "用户上下文": "user context", "租户上下文": "tenant context",
    "被评价的消息": "evaluated message", "下发校验和": "dispatch checksum",
    "评价取值": "rating value", "评审": "review", "归档": "archived",
    "当日无报工记录": "no work report for the day", "来源工时": "source work hours",
    "关联": "association", "变更通知": "change notification", "同步动作": "sync action",
    "手动重试": "manual retry", "员工": "employee", "工位": "workstation",
    "班组": "team", "技能": "skill", "主操作员": "primary operator",
    "创作者": "creator", "昵称": "nickname", "简介": "bio", "状态": "status",
    "会话": "conversation", "消息": "message", "支付": "payment", "退款": "refund",
    "开票": "invoicing", "渠道": "channel", "档案": "archive", "归档": "archive",
    "交付物": "deliverable", "对比": "compare", "投影": "projection",
}
TERMS.update(BIZ_TERMS)
SORTED_TERMS = sorted(TERMS, key=len, reverse=True)

BIZ_CALL = re.compile(r"new\s+BizException\s*\(")
STR_LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
PLACEHOLDER = re.compile(r"\{\d+\}")

# 业务文案高频句式。分组内容再由词典翻译；仍有中文则判为未覆盖，进入人工清单。
PHRASE_RULES: list[tuple[str, str]] = [
    (r"(.+?)失败[：:]\s*(.+)", "Failed to {g1}: {g2}"),
    (r"(.+?)失败[：:]\s*", "Failed to {g1}: "),
    (r"(.+?)失败", "Failed to {g1}"),
    (r"只有(.+?)允许删除", "Only {g1} can be deleted"),
    (r"只有(.+?)可以(.+)", "Only {g1} can {g2}"),
    (r"只能(.+)", "can only {g1}"),
    (r"(.+?)必须形如(.+)", "{g1} must be in the format {g2}"),
    (r"至少需要(.+?)才能(.+)", "At least {g1} is required to {g2}"),
    (r"(.+?)在(.+?)中形成环", "{g1} forms a cycle in {g2}"),
    (r"(.+?)形成环", "{g1} forms a cycle"),
    (r"(.+?)返回数据格式异常", "{g1} returned data in an invalid format"),
    (r"(.+?)与(.+?)不匹配", "{g1} does not match {g2}"),
    (r"(.+?)与(.+?)不一致", "{g1} is inconsistent with {g2}"),
    (r"不支持的(.+?)[：:]?", "Unsupported {g1}"),
    (r"响应缺少(.+)", "Response is missing {g1}"),
    (r"仅(.+?)可(.+)", "Only {g1} can {g2}"),
    (r"不能把(.+?)移动到(.+?)下", "Cannot move {g1} under {g2}"),
    (r"(.+?)下存在(.+?)，不能删除", "There are {g2} under {g1}; cannot delete"),
    (r"(.+?)当前状态不可(.+)", "Current status of {g1} does not allow {g2}"),
    (r"(.+?)必须等于(.+)", "{g1} must equal {g2}"),
    (r"(.+?)必须大于(.+)", "{g1} must be greater than {g2}"),
    (r"(.+?)必须小于(.+)", "{g1} must be less than {g2}"),
    (r"(.+?)不得早于(.+)", "{g1} must not be earlier than {g2}"),
    (r"(.+?)不能早于(.+)", "{g1} must not be earlier than {g2}"),
    (r"(.+?)不得晚于(.+)", "{g1} must not be later than {g2}"),
    (r"(.+?)不能晚于(.+)", "{g1} must not be later than {g2}"),
    (r"不存在[：:]?", "not found"),
    (r"不能为空", "must not be empty"),
    (r"已被引用，不能删除", "is referenced and cannot be deleted"),
    (r"已被其他用户修改，请刷新后重试", "was modified by another user. Please refresh and retry."),
    (r"已被其他用户修改，请刷新后重试", "was modified by another user. Please refresh and retry."),
    (r"(.+?)已被其他用户修改，请刷新后重试", "{g1} was modified by another user. Please refresh and retry."),
    (r"(.+?)已被他人修改，请刷新后重试", "{g1} was modified by another user. Please refresh and retry."),
    (r"(.+?)已被他人检出", "{g1} has been checked out by another user"),
    (r"(.+?)不存在或不属于当前(.+)", "{g1} does not exist or does not belong to the current {g2}"),
    (r"(.+?)不存在或不属于该(.+)", "{g1} does not exist or does not belong to the {g2}"),
    (r"(.+?)不存在或未发布", "{g1} does not exist or has not been published"),
    (r"(.+?)不存在[：:]?", "{g1} not found"),
    (r"(.+?)已存在[：:]?", "{g1} already exists"),
    (r"(.+?)不能为空", "{g1} must not be empty"),
    (r"(.+?)不得为空", "{g1} must not be empty"),
    (r"(.+?)不能少于(.+)", "{g1} must not be less than {g2}"),
    (r"(.+?)不能大于(.+)", "{g1} must not be greater than {g2}"),
    (r"(.+?)不能超过(.+)", "{g1} must not exceed {g2}"),
    (r"(.+?)不能早于(.+)", "{g1} must not be earlier than {g2}"),
    (r"(.+?)不能晚于(.+)", "{g1} must not be later than {g2}"),
    (r"(.+?)仅支持(.+)", "{g1} only supports {g2}"),
    (r"(.+?)只允许(.+)", "{g1} only allows {g2}"),
    (r"(.+?)必须是(.+)", "{g1} must be {g2}"),
    (r"(.+?)只能为(.+)", "{g1} can only be {g2}"),
    (r"(.+?)格式不正确", "{g1} has an invalid format"),
    (r"(.+?)格式非法", "{g1} has an invalid format"),
    (r"(.+?)不合法", "{g1} is invalid"),
    (r"(.+?)不可用", "{g1} is unavailable"),
    (r"(.+?)不可修改", "{g1} cannot be modified"),
    (r"(.+?)不可删除", "{g1} cannot be deleted"),
    (r"(.+?)已被占用", "{g1} is already in use"),
    (r"(.+?)被引用", "{g1} is referenced"),
    (r"(.+?)超出长度上限", "{g1} exceeds the length limit"),
    (r"(.+?)超出(.+)上限", "{g1} exceeds the {g2} limit"),
    (r"(.+?)失败[：:]?", "Failed to {g1}"),
    (r"缺少(.+?)，请通过网关登录后访问", "{g1} is missing. Please sign in via the gateway."),
    (r"缺少(.+?)，请通过网关访问", "{g1} is missing. Please access via the gateway."),
    (r"缺少(.+)", "{g1} is missing"),
    (r"需提供(.+)", "{g1} is required"),
    (r"请选择(.+)", "Please select {g1}"),
    (r"请填写(.+)", "Please enter {g1}"),
    (r"无权(.+)", "No permission to {g1}"),
    (r"未知(.+)[：:]?", "Unknown {g1}"),
    (r"(.+?)已过期", "{g1} has expired"),
    (r"(.+?)不匹配", "{g1} does not match"),
    (r"(.+?)不一致", "{g1} mismatch"),
    (r"(.+?)未配置", "{g1} is not configured"),
    (r"(.+?)尚未接入", "{g1} is not integrated"),
    (r"(.+?)状态异常", "{g1} has an invalid status"),
    (r"(.+?)响应为空", "{g1} returned an empty response"),
    (r"(.+?)返回失败[：:]?", "{g1} request failed"),
    (r"(.+?)服务不可用", "{g1} service is unavailable"),
    (r"(.+?)请先(.+)", "Please {g2} before {g1}"),
    (r"(.+?)后重试", "Retry after {g1}"),
]

STOPWORDS = {
    "the", "a", "an", "is", "are", "to", "of", "for", "not", "must", "please",
    "by", "with", "be", "has", "have", "only", "and", "or", "this", "that",
    "current", "does", "do", "no", "before", "after", "another", "user",
}


def java_literal(text: str) -> str:
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n") + '"'


def split_top_args(text: str, open_pos: int) -> tuple[list[str], int]:
    """切分构造器实参（字符串/括号感知）。open_pos 为 '(' 的下标。"""
    args: list[str] = []
    depth = 0
    quote = None
    escaped = False
    start = None
    close = -1
    for i in range(open_pos, len(text)):
        ch = text[i]
        if quote:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == quote:
                quote = None
            continue
        if ch in ('"', "'"):
            quote = ch
            continue
        if ch in "([{":
            depth += 1
            if depth == 1 and ch == "(":
                start = i + 1
        elif ch in ")]}":
            depth -= 1
            if depth == 0:
                close = i
                break
        elif ch == "," and depth == 1:
            args.append(text[start:i].strip())
            start = i + 1
    if close < 0 or start is None:
        return [], -1
    args.append(text[start:close].strip())
    return args, close


def top_level_strings(text: str) -> list[str]:
    """只取实参顶层的字符串字面量；嵌套调用（如 response.get("code")）不算文案。"""
    out: list[str] = []
    depth = 0
    quote = None
    escaped = False
    start = 0
    for i, ch in enumerate(text):
        if quote:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == quote:
                if depth == 0 and quote == '"':
                    out.append(text[start:i])
                quote = None
            continue
        if ch in ('"', "'"):
            quote = ch
            start = i + 1
        elif ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
    return out


def has_han_literal(text: str) -> bool:
    return any(HAN.search(s) for s in top_level_strings(text))


def split_concat(expr: str) -> list[str]:
    """按顶层 '+' 拆分拼接表达式；字符串、括号、方括号内的 '+' 不拆。"""
    parts: list[str] = []
    depth = 0
    quote = None
    escaped = False
    start = 0
    for i, ch in enumerate(expr):
        if quote:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == quote:
                quote = None
            continue
        if ch in ('"', "'"):
            quote = ch
        elif ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "+" and depth == 0:
            parts.append(expr[start:i])
            start = i + 1
    parts.append(expr[start:])
    return parts


def literal_content(seg: str) -> str | None:
    """整段是一个字符串字面量时返回其内容；否则返回 None。"""
    s = seg.strip()
    if len(s) < 2 or not s.startswith('"') or not s.endswith('"'):
        return None
    buf = []
    i = 1
    while i < len(s) - 1:
        ch = s[i]
        if ch == "\\" and i + 1 < len(s) - 1:
            buf.append(s[i + 1])
            i += 2
            continue
        if ch == '"':
            return None
        buf.append(ch)
        i += 1
    return "".join(buf)


def parse_message_expr(expr: str) -> tuple[str, list[str]]:
    """把 "A" + x + "B" 拆成模板 A{0}B 与动态参数 [x]。

    只按顶层 ``+`` 切分：``response.get("message")`` 这类嵌套调用里的字符串字面量
    属于表达式本身，必须整体作为占位参数，不能当作文案片段拼进模板（旧实现按字符
    扫描引号，把嵌套字面量拆成片段，产出了 ``{0}message{1}`` 的坏模板）。
    """
    template = ""
    dynamic: list[str] = []
    for seg in split_concat(expr):
        literal = literal_content(seg)
        if literal is not None:
            template += literal
            continue
        code = seg.strip()
        if not code:
            continue
        template += "{" + str(len(dynamic)) + "}"
        dynamic.append(code)
    return template, dynamic


def render_call(col: int, args: list[str], max_len: int = 150) -> str:
    """渲染 ``new BizException(...)``，超行宽时按逗号折行（续行缩进 8 格）。

    :param col: ``new`` 所在列的列号（0 基），用于计算绝对行宽
    :param args: 已渲染好的实参文本
    :param max_len: checkstyle LineLength 上限
    """
    one = "new BizException(" + ", ".join(args) + ")"
    if col + len(one) <= max_len:
        return one
    cont = " " * (col + 8)
    head = "new BizException("
    lines: list[str] = []
    cur = head
    cur_len = col + len(head)
    for idx, arg in enumerate(args):
        token = arg + ("," if idx < len(args) - 1 else ")")
        sep = "" if cur == head else " "
        if cur != head and cur_len + len(sep) + len(token) > max_len:
            lines.append(cur)
            cur = cont + token
            cur_len = len(cont) + len(token)
        else:
            cur += sep + token
            cur_len += len(sep) + len(token)
    lines.append(cur)
    return "\n".join(lines)


def zh_words(text: str) -> str | None:
    """用领域词典把中文片段翻成英文；遇到未覆盖中文返回 None。"""
    if not text or not HAN.search(text):
        return text.strip()
    out: list[str] = []
    i = 0
    while i < len(text):
        hit = None
        for term in SORTED_TERMS:
            if text.startswith(term, i) and term:
                hit = (TERMS[term], len(term))
                break
        if hit:
            word = hit[0].strip()
            if word:
                out.append(word)
            i += hit[1]
            continue
        ch = text[i]
        if HAN.match(ch):
            return None
        out.append(ch)
        i += 1
    return " ".join(x for x in out if x)


def normalize_punct(text: str) -> str:
    text = (text.replace("：", ": ").replace("，", ", ").replace("；", "; ")
            .replace("（", " (").replace("）", ")").replace("。", ". ")
            .replace("、", ", ").replace("—", "-").replace("–", "-"))
    # 词条翻译后常残留 " ," " :" 这类前置空格（片段自带尾空格），统一收紧
    text = re.sub(r"\s+([,;:.!?])", r"\1", text)
    return re.sub(r"\s+", " ", text).strip()


def translate_chunk(chunk: str) -> str | None:
    chunk = chunk.strip()
    if not chunk:
        return ""
    for pattern, template in PHRASE_RULES:
        m = re.fullmatch(pattern, chunk)
        if not m:
            continue
        values: dict[str, str] = {}
        for idx in range(1, (m.lastindex or 0) + 1):
            translated = zh_words(m.group(idx))
            if translated is None:
                return None
            values[f"g{idx}"] = translated
        try:
            result = template.format(**values)
        except (KeyError, IndexError):
            return None
        return normalize_punct(result)
    translated = zh_words(chunk)
    return normalize_punct(translated) if translated is not None else None


MANUAL_EN: dict[str, str] = {
    "BOM 至少需要一行子件才能发布": "BOM requires at least one component line to be published",
    "BOM 行数超过 2000，暂不支持在线对比": "BOM has more than 2000 lines; online comparison is not supported",
    "BOM_CYCLE_DETECTED: 零件 {0} 在结构树中形成环": "BOM_CYCLE_DETECTED: Part {0} forms a cycle in the structure tree",
    "ERP 应收查询失败: {0}": "Failed to query ERP accounts receivable: {0}",
    "ERP 退货下发失败: {0}": "Failed to dispatch ERP return: {0}",
    "NCR 当前状态不可关闭（需完成处置并执行）：{0}": "NCR in current status cannot be closed (disposition and execution must be completed): {0}",
    "bizType 与 bizId 不能为空": "bizType and bizId must not be empty",
    "bizType 与源/目标业务 ID 不能为空": "bizType and source/target business ID must not be empty",
    "feed 游标非法，请重新加载": "Invalid feed cursor. Please reload.",
    "{0} 零件 {1} 会形成环": "Part {1} in {0} would form a cycle",
    "不支持的电子签渠道: {0}，可选: {1}": "Unsupported e-sign channel: {0}. Available: {1}",
    "中文": "Chinese",
    "主体标识不能超过 64 个字符": "Subject identifier must not exceed 64 characters",
    "交接班记录不存在：{0}": "Shift handover record not found: {0}",
    "产线 ID 与产线编码必须同时填写": "Production line ID and code must be provided together",
    "仅审批通过或签署中的合同可发起签署，当前状态: {0}": "Only approved or signing contracts can start signing. Current status: {0}",
    "仅已解决异常可以关闭": "Only resolved exceptions can be closed",
    "仅待处理异常可以确认": "Only pending exceptions can be confirmed",
    "仅支持派生 PBOM 或 MBOM": "Only PBOM or MBOM can be derived",
    "仅项目管理员可执行该操作": "Only project administrators can perform this operation",
    "任务{0}不是待派工状态": "Task {0} is not in dispatchable status",
    "任务{0}已有报工，不允许撤回": "Task {0} already has work reports and cannot be withdrawn",
    "任务不属于当前工单": "Task does not belong to the current work order",
    "保养项不属于该工单：{0}": "Maintenance item does not belong to the work order: {0}",
    "保养项名称与结论均不能为空": "Maintenance item name and conclusion must not be empty",
    "停用零件不能加入 BOM": "Disabled part cannot be added to BOM",
    "净工时不能为负": "Net work hours must not be negative",
    "准备工时不能为负": "Setup work hours must not be negative",
    "创作者昵称不能超过 64 个字符": "Creator nickname must not exceed 64 characters",
    "创作者简介不能超过 500 个字符": "Creator bio must not exceed 500 characters",
    "到期日期不得早于生效日期": "Expiration date must not be earlier than effective date",
    "印章原图为空: {0}": "Seal original image is empty: {0}",
    "印章图片必须为图片类型": "Seal image must be an image type",
    "原领料单不属于当前工单": "Original material requisition does not belong to the current work order",
    "发件箱事件幂等键与事件类型不能为空": "Outbox event idempotency key and event type must not be empty",
    "变更单当前状态不允许该操作：{0}": "Current status of change order does not allow this operation: {0}",
    "变更审批调用失败: {0}": "Change approval call failed: {0}",
    "变更通知当前状态不允许该操作：{0}": "Current status of change notification does not allow this operation: {0}",
    "只读成员不可执行该操作": "Read-only members cannot perform this operation",
    "合同审批流程未配置，请设置 whale.contract.approval.definition-id": "Contract approval workflow is not configured. Please set whale.contract.approval.definition-id",
    "合同快照入档失败: 响应缺少档案条目ID": "Failed to archive contract snapshot: response is missing archive item ID",
    "合同服务不可用: {0}": "Contract service unavailable: {0}",
    "合同编号生成失败，请稍后重试": "Failed to generate contract number. Please retry later.",
    "同一批次不能建立谱系链接": "Lineage link cannot be created within the same batch",
    "同零件同版本工艺路线已存在": "Routing for the same part and version already exists",
    "回调报文为空": "Callback payload is empty",
    "回调渠道与签署任务不一致: {0}": "Callback channel is inconsistent with the signing task: {0}",
    "回调验签失败或时间戳超出允许窗口": "Callback signature verification failed or timestamp is outside the allowed window",
    "子组内样本序号重复：{0}": "Duplicate sample number within subgroup: {0}",
    "子组样本数已满：{0}，应开启新子组": "Subgroup sample count is full: {0}. A new subgroup should be opened.",
    "对象类型与对象ID不能为空": "Object type and object ID must not be empty",
    "导出任务过多，请稍后重试": "Too many export tasks. Please retry later.",
    "工作项不属于当前迭代": "Work item does not belong to the current iteration",
    "工作项不属于当前项目": "Work item does not belong to the current project",
    "工作项编号生成失败，请稍后重试": "Failed to generate work item number. Please retry later.",
    "工单状态不能从 {0} 变更为 {1}": "Work order status cannot change from {0} to {1}",
    "工序顺序 {0} 的计划数量合计应等于工单计划数量 {1}": "The sum of planned quantities at operation sequence {0} must equal work order planned quantity {1}",
    "工时不能为负": "Work hours must not be negative",
    "工艺路线无工序，请手工拆分": "Routing has no operations. Please split manually.",
    "工艺路线至少需要一道工序才能发布": "Routing requires at least one operation to be published",
    "布尔类型配置值只能是 true / false": "Boolean configuration value can only be true / false",
    "所选产线不属于所选车间": "Selected production line does not belong to the selected workshop",
    "所选工作中心不属于所选产线": "Selected work center does not belong to the selected production line",
    "报价审批发起失败，请稍后重试": "Failed to start quotation approval. Please retry later.",
    "损耗率必须在 0-100 之间": "Loss rate must be between 0 and 100",
    "排序空间不足，请重试": "Insufficient sort space. Please retry.",
    "排序锚点不能同时为空": "Sort anchors must not both be empty",
    "排序锚点不能是自身": "Sort anchor cannot be itself",
    "排序锚点顺序不正确": "Sort anchor order is incorrect",
    "文件内容为空: {0}": "File content is empty: {0}",
    "文档由他人检出，不能检入": "Document is checked out by another user and cannot be checked in",
    "文档编号生成失败，请重试": "Failed to generate document number. Please retry.",
    "无权操作该文档": "No permission to operate this document",
    "无权管理该资源的权限": "No permission to manage this resource",
    "无权访问该文档": "No permission to access this document",
    "期间编码不合法：{0}": "Invalid period code: {0}",
    "未找到可签署的相对方，请在合同中登记签署人": "No signable counterparty found. Please register a signer in the contract.",
    "未找到物料【{0}】的已发布工艺路线，请手工拆分": "No published routing found for material [{0}]. Please split manually.",
    "未知殿：{0}": "Unknown hall: {0}",
    "未获用章授权：印章[{0}] 不可由当前用户用于该合同": "No seal authorization: seal [{0}] cannot be used by the current user for this contract",
    "未配置该状态流转": "This status transition is not configured",
    "本地签章件由本地签署接口生成，不提供下载": "Local sealed documents are generated by the local signing API and cannot be downloaded",
    "本服务尚未接入概览（未实现 OverviewProvider）": "This service has not integrated overview (OverviewProvider is not implemented)",
    "本服务提供多个概览节点，请用 node 参数指定：{0}": "This service provides multiple overview nodes. Please specify with the node parameter: {0}",
    "标准工时不能为负": "Standard hours must not be negative",
    "检验项上下限不合法：{0}": "Invalid upper/lower limits for inspection item: {0}",
    "检验项不属于该计划：{0}": "Inspection item does not belong to the plan: {0}",
    "没有可重试的失败通知": "No failed notification available for retry",
    "源与目标业务 ID 不能相同": "Source and target business IDs must not be the same",
    "源状态与目标状态不能相同": "Source and target statuses must not be the same",
    "源零件与保留零件不能相同": "Source part and retained part must not be the same",
    "源零件为 BOM 主体，不能合并": "Source part is the BOM main body and cannot be merged",
    "源零件在 BOM 结构中作为父件使用，不能合并": "Source part is used as a parent part in the BOM structure and cannot be merged",
    "父件不在当前 BOM 结构中": "Parent part is not in the current BOM structure",
    "父工作项不能是自身": "Parent work item cannot be itself",
    "版本 {0} 不存在或无可回滚的值": "Version {0} does not exist or has no value to roll back to",
    "版本号不能为负数": "Version number must not be negative",
    "状态不属于当前项目": "Status does not belong to the current project",
    "状态变更请使用流转接口": "Please use the transition API to change status",
    "班次开始时间和结束时间不能为空": "Shift start time and end time must not be empty",
    "班次开始时间和结束时间不能相同": "Shift start time and end time must not be the same",
    "目标列已超过 WIP 上限（{0}），请先完成在进行中的工作项": "Target column has exceeded WIP limit ({0}). Please complete in-progress work items first.",
    "目标版本号必须在 1 到 {0} 之间": "Target version number must be between 1 and {0}",
    "目标状态未配置在当前看板列中": "Target status is not configured in the current kanban column",
    "确认令牌已过期，请重新发起": "Confirmation token has expired. Please start again.",
    "该交付物已归档": "Deliverable has been archived",
    "该任务当日无报工记录，不能登记报工来源工时": "No work report for this task on that day; source work hours cannot be recorded",
    "该关联已存在": "Association already exists",
    "该变更单已存在变更通知": "Change notification already exists for this change order",
    "该同步动作不支持手动重试": "This sync action does not support manual retry",
    "该员工已在此工位班次排班": "Employee is already scheduled for this workstation shift",
    "该员工已在班组中：{0}": "Employee is already in the team: {0}",
    "该员工此任务开始时间的工时已存在": "Work hours for this employee at the task start time already exist",
    "该员工此技能已登记：{0}": "Skill is already registered for this employee: {0}",
    "该工位班次已存在主操作员": "Primary operator already exists for this workstation shift",
    "该文件已登记为当前工作项附件": "File has already been registered as an attachment of the current work item",
    "该期间暂无月度绩效统计，无法锁定": "No monthly performance statistics for this period; cannot lock",
    "该流转要求填写评论": "This transition requires a comment",
    "该状态已映射到当前看板的其它列": "Status is already mapped to another column of the current kanban",
    "该状态流转已存在": "Status transition already exists",
    "该用户已是项目成员": "User is already a project member",
    "该行仍有下级零件，不能删除": "This line still has child parts and cannot be deleted",
    "该计划存在未完成的保养工单": "Plan has unfinished maintenance work orders",
    "该运行不在待确认状态": "Run is not in pending confirmation status",
    "请确认签署意愿后再提交": "Please confirm signing intent before submitting",
    "谱系成环，禁止建链：{0} -> {1}": "Lineage cycle detected; link creation is forbidden: {0} -> {1}",
    "进度回传报文序列化失败": "Failed to serialize progress callback payload",
    "迭代下仍有工作项，请先移出后再删除": "Iteration still has work items. Please move them out before deletion.",
    "迭代已关闭，不可再加入工作项": "Iteration is closed; no more work items can be added",
    "部分派工任务不存在": "Some dispatched tasks do not exist",
    "配置项已处于版本 {0}": "Configuration item is already at version {0}",
    "非项目成员，无权访问": "Not a project member; no permission to access",
    "项目已归档，请先恢复为进行中": "Project is archived. Please restore it to in progress first.",
    "项目未配置默认状态，请先完善状态配置": "Project default status is not configured. Please complete the status configuration first.",
    "项目至少保留一名管理员": "Project must retain at least one administrator",
    "项目负责人不可移除，请先变更负责人": "Project owner cannot be removed. Please change the owner first.",
    "项目负责人必须保留管理员角色": "Project owner must retain the administrator role",
    "项目键需以字母开头，2–10 位大写字母或数字": "Project key must start with a letter and contain 2-10 uppercase letters or digits",
    "预览尚未就绪：{0}": "Preview is not ready: {0}",
}


def translate_biz(template: str) -> str | None:
    if template in MANUAL_EN:
        return MANUAL_EN[template]
    parts = PLACEHOLDER.split(template)
    placeholders = PLACEHOLDER.findall(template)
    out: list[str] = []
    for part in parts:
        translated = translate_chunk(part)
        if translated is None:
            return None
        out.append(translated)
    result = ""
    for idx, part in enumerate(out):
        result += part
        if idx < len(placeholders):
            result += placeholders[idx]
    result = re.sub(r":\{", ": {", result)
    result = re.sub(r"(?<=[A-Za-z0-9])\{", " {", result)
    result = re.sub(r"\}(?=[A-Za-z0-9])", "} ", result)
    return normalize_punct(result)


def key_slug(en: str, used: set[str]) -> str:
    words = re.findall(r"[A-Za-z0-9]+", PLACEHOLDER.sub(" ", en))
    words = [w.lower() for w in words if w.lower() not in STOPWORDS]
    slug = "-".join(words[:6]) or "message"
    slug = slug.strip("-")
    candidate = slug
    n = 2
    while candidate in used:
        candidate = f"{slug}-{n}"
        n += 1
    used.add(candidate)
    return candidate


def module_paths(root: Path, module: str) -> Path:
    base = root / "src" / "main" / "resources" / "i18n"
    return base if module == "i18n" else base / module


def collect(commit: bool = False):
    """返回待迁移条目：path / 起止偏移 / 新构造器文本 / key / 中文模板 / 英文 / 动态参数。"""
    pending = []
    problems = []
    for path in sorted(BACKEND.rglob("*.java")):
        if "target" in path.parts or "test" in path.parts:
            continue
        root = None
        for parent in path.parents:
            if (parent / "pom.xml").exists():
                root = parent
                break
        if root is None:
            continue
        module = vm.infer_module(path)
        sub_match = re.search(r"com[\\/]bluewhale[\\/]" + re.escape(module) + r"[\\/](\w+)", str(path))
        subdomain = vm.kebab(sub_match.group(1)) if sub_match else ""
        text = path.read_text(encoding="utf-8")
        used_keys: set[str] = set()
        for m in BIZ_CALL.finditer(text):
            args, close = split_top_args(text, m.end() - 1)
            if not args:
                continue
            literals = [s for arg in args for s in top_level_strings(arg)]
            if any("." in s and not HAN.search(s) for s in literals):
                continue  # 已有 messageKey
            if len(args) >= 3 and not HAN.search(args[1]):
                continue  # 第二参已是 key 变量
            msg_idx = next((i for i, arg in enumerate(args) if has_han_literal(arg)), None)
            if msg_idx is None:
                continue
            template, dynamic = parse_message_expr(args[msg_idx])
            if not HAN.search(template):
                # 文案全为动态（如三元分支各带中文），抽成 key 会丢文案，保留原写法
                continue
            en = translate_biz(template)
            if not en or HAN.search(en):
                problems.append(f"{path}: 英文未覆盖: {template}")
                continue
            prefix = args[:msg_idx]
            slug = key_slug(en, used_keys)
            key = f"biz.{module}.{subdomain}.{slug}" if subdomain and subdomain != module else f"biz.{module}.{slug}"
            new_args = prefix + [java_literal(key), java_literal(template)] + dynamic
            col = m.start() - (text.rfind("\n", 0, m.start()) + 1)
            new_call = render_call(col, new_args)
            open_pos = new_call.find("(")
            if vm._matching_paren(new_call, open_pos) != len(new_call) - 1:
                problems.append(f"{path}: 生成构造器括号不平衡: {new_call}")
                continue
            pending.append({
                "path": path, "root": root, "module": module, "start": m.start(), "end": close + 1,
                "old": text[m.start():close + 1], "new": new_call,
                "key": key, "zh": template, "en": en,
            })
    return pending, problems


def write_bundles(pending):
    by_module: dict[tuple[Path, str], list[dict]] = defaultdict(list)
    for item in pending:
        by_module[(item["root"], item["module"])].append(item)
    header = "# 文案词条（校验/业务异常，由 scripts/i18n/migrate_*.py 生成）\n"
    for (root, module), items in sorted(by_module.items()):
        res_dir = module_paths(root, module)
        res_dir.mkdir(parents=True, exist_ok=True)

        def merge(name: str, mapping: dict[str, str]):
            path = res_dir / name
            merged: dict[str, str] = {}
            if path.exists():
                for line in path.read_text(encoding="utf-8").splitlines():
                    s = line.strip()
                    if s and not s.startswith("#") and "=" in s:
                        k, v = s.split("=", 1)
                        merged[k] = v
            merged.update(mapping)
            path.write_text(header + "\n".join(f"{k}={merged[k]}" for k in sorted(merged)) + "\n", encoding="utf-8")

        merge("messages.properties", {i["key"]: i["zh"] for i in items})
        merge("messages_zh_CN.properties", {i["key"]: i["zh"] for i in items})
        merge("messages_en_US.properties", {i["key"]: i["en"] for i in items})
        print(f"  词条: {res_dir.relative_to(BACKEND)} (+{len(items)})")


def migrate(commit: bool):
    pending, problems = collect()
    print(f"=== 待迁移 {len(pending)} 处 / {len(set(i['key'] for i in pending))} 个 key / {len(problems)} 未覆盖 ===")
    for p in problems[:40]:
        print("  UNTRANSLATED:", p)
    if problems:
        print("存在未覆盖文案，先补词典/句式再迁移。")
        return
    for item in pending[:15]:
        print(f"  {item['key']:62} | {item['zh']} -> {item['en']}")
    if not commit:
        print("\n[DRY-RUN] 未写入。加 --commit 实际执行。")
        return
    by_file: dict[Path, list[dict]] = defaultdict(list)
    for item in pending:
        by_file[item["path"]].append(item)
    for path, items in by_file.items():
        text = path.read_text(encoding="utf-8")
        for item in sorted(items, key=lambda x: x["start"], reverse=True):
            text = text[:item["start"]] + item["new"] + text[item["end"]:]
        path.write_text(text, encoding="utf-8")
        print(f"  已替换 {path.relative_to(BACKEND)} ({len(items)})")
    write_bundles(pending)
    modules = {(i["root"], i["module"]) for i in pending}
    for root, module in sorted(modules):
        if vm.add_i18n_dependency(root, module, commit=True):
            print(f"  已加依赖 {root.relative_to(BACKEND)}")


def check_biz():
    problems = []
    refs: dict[str, Path] = {}
    for path in sorted(BACKEND.rglob("*.java")):
        if "target" in path.parts or "test" in path.parts:
            continue
        text = path.read_text(encoding="utf-8")
        for m in BIZ_CALL.finditer(text):
            args, close = split_top_args(text, m.end() - 1)
            for arg in args:
                for literal in STR_LITERAL.findall(arg):
                    if literal.startswith("biz.") and "." in literal[4:]:
                        refs[literal] = path
    bundles: dict[Path, dict[str, set[str]]] = defaultdict(dict)
    for p in BACKEND.rglob("messages*.properties"):
        if "target" in p.parts or "test" in p.parts or "i18n" not in p.parts:
            continue
        keys = set()
        for line in p.read_text(encoding="utf-8").splitlines():
            s = line.strip()
            if s and not s.startswith("#") and "=" in s:
                keys.add(s.split("=", 1)[0])
        bundles[p.parent][p.name] = keys
    all_keys = set()
    for files in bundles.values():
        all_keys |= files.get("messages.properties", set())
    for key, path in refs.items():
        if key not in all_keys:
            problems.append(f"业务异常引用 {key} 无词条 (引用于 {path})")
    for directory, files in bundles.items():
        default = files.get("messages.properties", set())
        for variant in ("messages_zh_CN.properties", "messages_en_US.properties"):
            other = files.get(variant, set())
            if default - other:
                problems.append(f"{directory}: {variant} 缺 {len(default - other)} 键")
            if other - default:
                problems.append(f"{directory}: {variant} 多 {len(other - default)} 键")
        en = directory / "messages_en_US.properties"
        if en.exists():
            for line in en.read_text(encoding="utf-8").splitlines():
                s = line.strip()
                if s and not s.startswith("#") and "=" in s and HAN.search(s):
                    problems.append(f"{en}: 英文词条含中文: {s[:70]}")
    if problems:
        print(f"业务异常 i18n 校验失败：{len(problems)} 项")
        for p in problems[:40]:
            print("  FAIL:", p)
        return 1
    print(f"业务异常 i18n 校验通过：{len(refs)} 个 biz key / {len(bundles)} 组词条。")
    return 0


def format_calls() -> int:
    """把已迁移但仍超行宽的 BizException 调用按逗号折行（幂等，不动短调用）。"""
    changed = 0
    for path in sorted(BACKEND.rglob("*.java")):
        if "target" in path.parts or "test" in path.parts:
            continue
        text = path.read_text(encoding="utf-8")
        edits = []
        for m in BIZ_CALL.finditer(text):
            args, close = split_top_args(text, m.end() - 1)
            if len(args) < 2 or not any(a.strip().startswith('"biz.') for a in args):
                continue
            old = text[m.start():close + 1]
            if "\n" in old:
                continue
            line_start = text.rfind("\n", 0, m.start()) + 1
            line_end = text.find("\n", close)
            line = text[line_start:line_end if line_end >= 0 else len(text)]
            if len(line) <= 150:
                continue
            col = m.start() - line_start
            # 行尾还有 ";" 等收尾字符，折行预算要扣掉
            suffix = line[close + 1 - line_start:]
            rendered = render_call(col, args, max_len=150 - len(suffix))
            edits.append((m.start(), close + 1, rendered))
        if not edits:
            continue
        for start, end, new in sorted(edits, reverse=True):
            text = text[:start] + new + text[end:]
        path.write_text(text, encoding="utf-8")
        changed += len(edits)
    print(f"折行完成：{changed} 处")
    return changed


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    p_mig = sub.add_parser("migrate")
    p_mig.add_argument("--app", help="只处理指定模块（如 crm）")
    p_mig.add_argument("--commit", action="store_true")
    sub.add_parser("check")
    sub.add_parser("format")
    args = ap.parse_args()
    if args.cmd == "check":
        sys.exit(check_biz())
    if args.cmd == "format":
        format_calls()
        return
    migrate(commit=args.commit)


if __name__ == "__main__":
    main()
