#!/usr/bin/env python3
"""
校验注解中文提示 → i18n key 迁移工具。

范式来源：whale-services 已迁移的 968 处 {validation.xxx} 注解。
key 规则：validation.<module>.<entity>.<field>-<rule>
  - module  : i18n 目录名（如 mes/oa/crm）
  - entity  : DTO 类名去通用后缀转 kebab-case（CustomerSaveCommand → customer）
  - field   : Java 字段名转 kebab-case（workOrderCode → work-order-code）
  - rule    : 由注解类型推断（required/size/pattern/min/max/range/email/...）

三份 properties：messages.properties 与 messages_zh_CN.properties 同为中文兜底，
messages_en_US.properties 为英文。校验类文案数字写死、不用占位符（与既有范式一致）。

用法：
  python migrate_validation_messages.py scan <app>              # 只读：列出将被迁移的注解
  python migrate_validation_messages.py migrate <app> [--commit]# 迁移单个应用；默认 dry-run
  python migrate_validation_messages.py migrate-all [--commit]  # 迁移整个 whale-backend；默认 dry-run
  python migrate_validation_messages.py regen-en                # 只补缺失/仍为中文的 validation 英文词条
  python migrate_validation_messages.py check                   # 完整性门禁
"""
from __future__ import annotations

import argparse
import bisect
import os
import re
import sys
from collections import defaultdict
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[2] / "whale-backend"
APPS = BACKEND / "whale-apps"
HAN = re.compile(r"[\u4e00-\u9fff]")

# ---------------------------------------------------------------------------
# 中英词典：长词优先匹配。字段描述 = 修饰词 + 中心词，整体贪心替换。
# ---------------------------------------------------------------------------
TERMS: dict[str, str] = {
    # 中心词
    "名称": "name", "编码": "code", "编号": "number", "状态": "status", "类型": "type",
    "金额": "amount", "数量": "quantity", "标题": "title", "内容": "content", "描述": "description",
    "备注": "remark", "说明": "description", "原因": "reason", "事由": "reason", "结论": "conclusion",
    "时间": "time", "日期": "date", "周期": "period", "期限": "period", "阶段": "stage",
    "姓名": "name", "账号": "account", "单号": "number", "账号": "account", "电话": "phone",
    "地址": "address", "地点": "location", "文件": "file", "文件名": "file name", "路径": "path",
    "分类": "category", "类别": "category", "类目": "category", "等级": "level", "规格": "specification",
    "型号": "model", "单位": "unit", "单价": "unit price", "折扣": "discount", "税率": "tax rate",
    "人": "person", "负责人": "owner", "申请人": "applicant", "联系人": "contact", "经办人": "handler",
    "场景": "scene", "项目": "project", "任务": "task", "计划": "plan", "规则": "rule",
    "明细": "item", "详情": "detail", "附件": "attachment", "关键词": "keywords", "标签": "tags",
    "版本": "version", "渠道": "channel", "来源": "source", "范围": "scope", "目标": "target",
    "结果": "result", "意见": "comment", "评论": "comment", "摘要": "summary", "主题": "subject",
    "批次": "batch", "工单": "work order", "工序": "operation", "产线": "production line",
    "车间": "workshop", "设备": "equipment", "物料": "material", "产品": "product", "供应商": "supplier",
    "客户": "customer", "伙伴": "partner", "线索": "lead", "商机": "opportunity", "报价": "quotation",
    "合同": "contract", "订单": "order", "发票": "invoice", "库存": "inventory", "仓库": "warehouse",
    "活动": "campaign", "培训班": "training class", "场次": "session", "试卷": "exam paper",
    "题目": "question", "文档": "document", "版块": "board", "帖子": "post", "消息": "message",
    "邮箱": "email", "音乐": "music", "视图": "view", "看板": "kanban", "日历": "calendar",
    "权限": "permission", "角色": "role", "部门": "department", "用户": "user", "组织": "organization",
    "请假": "leave", "考勤": "attendance", "薪资": "payroll", "绩效": "performance", "员工": "employee",
    "保养": "maintenance", "点检": "inspection", "检验": "inspection", "缺陷": "defect", "产能": "capacity",
    "产量": "output", "不良": "defect", "工时": "work hours", "班次": "shift", "报警": "alarm",
    "预警": "alert", "阈值": "threshold", "指标": "metric", "报表": "report", "公式": "formula",
    "字段": "field", "参数": "parameter", "配置": "configuration", "模式": "mode", "效果": "effect",
    "对象": "object", "内容类型": "content type", "签章": "seal", "印章": "seal", "公文": "official document",
    "用车": "vehicle use", "行程": "itinerary", "费用": "expense", "预算": "budget", "成本": "cost",
    "赢单": "won deal", "回执": "receipt", "收件人": "recipient", "抄送人": "cc recipient",
    "错误码": "error code", "错误说明": "error description", "人群": "audience", "快照": "snapshot",
    "触达": "outreach", "行动项": "action item", "行动": "action", "下级": "subordinate", "上级": "superior",
    "父级": "parent", "最大": "maximum", "最小": "minimum", "有效": "valid", "无效": "invalid",
    "关闭": "close", "审批": "approval", "流转": "flow", "授权": "authorization", "密级": "security level",
    "优先级": "priority", "紧急度": "urgency", "公开": "public", "可见": "visible", "重量": "weight",
    "小数": "decimal places", "天数": "days", "分钟": "minutes", "秒": "seconds", "字符": "characters",
    "个字": "characters", "字": "characters", "条": "items", "道": "questions", "次": "times", "天": "days",
    "年": "years", "月": "months", "个": "items", "位": "places",
    # 修饰/动词
    "目标": "target", "实际": "actual", "预计": "estimated", "计划": "planned", "核准": "approved",
    "调整": "adjusted", "调整后": "adjusted", "归因": "attributed", "整单": "order-level",
    "行": "line", "新": "new", "旧": "old", "详细": "detailed", "短": "short", "全称": "full",
    "开始": "start", "结束": "end", "创建": "creation", "修改": "modification", "更新": "update",
    "跟进": "follow-up", "关闭原因": "closure reason", "无效原因": "invalid reason",
    "强制": "mandatory", "允许": "allowed", "指定": "designated", "重新": "re-", "通过": "approval",
    "业务标识": "business identifier", "biz": "business", "导入": "import", "导出": "export",
    "币种": "currency", "无效": "invalid", "流程": "process", "实例": "instance",
    "营销": "marketing", "折扣率": "discount rate", "裁决": "decision", "预测": "forecast",
    "行折扣率": "line discount rate", "导入内容": "import content", "导出类型": "export type",
    # --- 领域修饰词/中心词补充（whale-apps 批量） ---
    "工作中心": "work center", "计量": "measurement", "计量单位": "unit of measure", "申请": "application",
    "模板": "template", "动作": "action", "联系": "contact", "变更": "change", "技能": "skill",
    "合格": "qualified", "采集": "collection", "生产": "production", "备件": "spare part",
    "判定": "judgment", "相对方": "counterparty", "采购": "purchase", "科目": "account",
    "需求": "demand", "选项": "option", "商品": "product", "券": "coupon", "工艺路线": "routing",
    "操作": "operation", "验收": "acceptance", "执行": "execution", "退料": "return material",
    "代码": "code", "线边仓": "line-side warehouse", "叫料": "material call", "交接": "handover",
    "建议": "suggestion", "补卡": "make-up punch", "会议": "meeting", "适用": "applicable",
    "假期": "leave", "加班": "overtime", "聚合": "aggregation", "提案": "proposal", "用量": "usage",
    "工装": "tooling", "单据": "document", "事件键": "event key", "入库": "inbound", "证书": "certificate",
    "章节": "chapter", "课程": "course", "讲师": "instructor", "课件": "courseware", "考试": "exam",
    "标准": "standard", "班组": "team", "交班": "shift handover", "接班": "shift takeover",
    "定义": "definition", "生效": "effective", "成员": "member", "祝福": "greeting", "主体": "subject",
    "报销": "reimbursement", "支部": "branch", "评审": "review", "目录": "directory", "子件": "component",
    "工作项": "work item", "迭代": "iteration", "源": "source", "拒签": "refusal", "到期日": "due date",
    "凭证": "voucher", "所属": "owning", "效率": "efficiency", "撤销": "reversal", "期数": "period",
    "学员": "trainee", "学分": "credit", "题干": "question stem", "补签": "make-up sign",
    "课时": "class hours", "承运商": "carrier", "退货物流": "return logistics", "价格": "price",
    "发货": "shipment", "派工": "dispatch", "排程": "scheduling", "顺序": "sequence", "停机": "downtime",
    "完工登记": "completion registration", "理论节拍": "theoretical takt", "维修": "maintenance",
    "验证": "verification", "故障": "fault", "严重度": "severity", "领用": "requisition",
    "跨日标识": "cross-day flag", "子组": "subgroup", "流水号": "serial number", "消耗": "consumption",
    "确认": "confirmation", "期间": "period", "锁定": "lock", "车牌号": "plate number", "司机": "driver",
    "加签": "add-sign",
    "驾驶证号": "driver license number", "里程": "mileage", "用章": "seal use", "路由": "route",
    "代理": "agent", "退回节点": "reject node", "解析器": "resolver", "转办": "transfer",
    "日程": "schedule", "提醒": "reminder", "参会": "attendee", "纪要": "minutes", "公告": "announcement",
    "投票": "vote", "审核": "review", "来文": "incoming document", "批阅": "endorsement",
    "承办": "handling", "税额": "tax amount", "限额": "limit", "异动": "change", "在职": "employment",
    "时段": "time slot", "序号": "sequence number", "出发地": "departure", "到达地": "arrival",
    "交通方式": "transport mode", "出差": "business trip", "党员": "party member", "反馈": "feedback",
    "办理": "processing", "会员": "member", "福利": "benefit", "发放标准": "payment standard",
    "通知": "notification", "资源": "resource", "列": "column", "键": "key", "故事点": "story point",
    "签署": "signature", "收货": "goods receipt", "零件": "part", "简称": "abbreviation",
    "开户银行": "bank", "评级": "rating", "应付": "payable", "应收": "receivable", "报文": "message",
    "报废": "scrap", "持证": "certificate holder", "简介": "introduction", "引用": "reference",
    "外链": "external link", "切屏": "screen switch", "抽题": "question selection", "抽取": "extraction",
    "判分": "grading", "作答": "answer", "课时": "class hours", "开班": "class start", "结班": "class end",
    "培训": "training", "价目": "price list", "图片": "image", "品牌": "brand", "主图": "main image",
    "支付": "payment", "作废": "void", "策略": "policy", "保留": "retention", "调度表达式": "cron expression",
    "布局": "layout", "协议": "protocol", "接入": "access", "采样": "sampling", "制造厂商": "manufacturer",
    "基线": "baseline", "初始": "initial", "报修": "repair request", "关联": "association",
    "处理": "handling", "补料": "material replenishment", "抽样": "sampling", "巡检": "inspection",
    "返工": "rework", "控制图": "control chart", "命中处理": "hit handling", "绑定": "binding",
    "码制": "barcode type", "请求号": "request number", "扫码": "scan", "冲销": "reversal",
    "遗留问题": "remaining issue", "统计层级": "statistics level", "排班": "shift schedule",
    "启停标记": "start-stop flag", "暂停": "pause", "当前": "current", "准驾车型": "allowed vehicle type",
    "起点": "start point", "终点": "end point", "车辆": "vehicle", "油费": "fuel cost", "发起": "initiator",
    "打卡": "punch", "上班": "clock-in", "下班": "clock-out", "纬度": "latitude", "经度": "longitude",
    "起始": "start", "共享": "shared", "接收": "recipient", "用途": "purpose", "回复": "reply",
    "红头文字": "red-header text", "版记文字": "imprint text", "文号前缀": "document number prefix",
    "分隔符": "separator", "主送": "primary recipient", "抄送": "cc", "签发": "issuer",
    "会计科目": "accounting subject", "登录": "login", "工号": "employee number",
    "补偿方式": "compensation method", "预支": "advance", "节假日": "holiday", "主持": "host",
    "签到": "sign-in", "书记": "secretary", "正文": "body text", "基数下限": "base lower limit",
    "缴费比例": "payment ratio", "学历": "education", "入党": "party admission", "党费": "party fee",
    "名额": "quota", "评分": "score", "工会会员": "union member", "慰问": "condolence",
    "入会": "membership", "会费": "membership fee", "变更单": "change order", "影响": "impact",
    "预览": "preview", "材质": "material", "损耗率": "loss rate", "父件": "parent part",
    "合并": "merge", "路线": "route", "准备": "preparation", "上限": "upper limit", "下限": "lower limit",
    "附加": "additional", "扩展": "extension", "客户端": "client", "统一社会信用": "unified social credit",
    "纳税识别号": "tax ID", "单件": "unit piece", "净重": "net weight", "毛重": "gross weight",
    "题": "question", "份": "copy", "年度": "annual", "月度": "monthly", "日": "day",
    "位小数": "decimal places", "上限不能": "upper limit must not", "效率不能": "efficiency must not",
    "上级": "superior", "下级": "subordinate", "批次号": "batch number", "业务": "business",
    "主体标识": "subject identifier", "事件": "event", "会议": "meeting", "会议室": "meeting room",
    "会费": "membership fee", "缴至": "paid through", "位置": "location", "余额": "balance",
    "方向": "direction", "保养": "maintenance", "项": "item", "结论": "conclusion", "催办": "reminder",
    "党费": "party fee", "基数": "base", "兜底": "fallback", "参数": "parameter", "报告": "report",
    "目的": "destination", "出车": "dispatch", "回车": "return trip", "切屏": "screen switch",
    "次数": "count", "刷新": "refresh", "间隔": "interval", "归档": "archive", "条": "records",
    "规则": "rule", "道": "questions", "单次": "single", "时长": "duration", "学分": "credit",
    "条码": "barcode", "笔": "transaction", "原": "original", "在职": "employment", "及格": "passing",
    "发票": "invoice", "可见性": "visibility", "处置": "disposition", "外部": "external",
    "幂等键": "idempotency key", "子组": "subgroup", "大小": "size", "学员": "trainee",
    "用户名": "username", "安全库存": "safety stock", "完工": "completion", "完成百分比": "completion percentage",
    "实测": "measured", "实际": "actual", "运行": "runtime", "客户端": "client", "对象": "object",
    "巡检": "inspection", "频次": "frequency", "工位": "workstation", "工作日期": "work date",
    "工作日": "working day", "周": "week", "路": "line", "制造": "manufacturing", "途中": "in transit",
    "延期": "delay", "执行人": "executor", "判定人": "judge", "检验员": "inspector", "审核人": "reviewer",
    "余人": "remaining", "违约金": "penalty", "账号": "account", "登录账号": "login account",
    "清单": "list", "目的地": "destination", "条数": "count", "单笔": "single transaction",
    "及格": "passing", "发票": "invoice", "处理": "handling", "子组号": "subgroup number",
    "实测": "measured", "客户端": "client", "请求": "request", "版本号": "version", "工作项": "work item",
    "列表": "list", "工序": "operation", "年度": "annual", "默认": "default", "额度": "quota",
    "开考": "exam start", "归档": "archive", "目标表": "target table", "当日": "daily",
    "标准工时": "standard hours", "影响": "impact", "可用率": "availability", "评估": "assessment",
    "必修": "mandatory", "产量": "output", "总额": "total amount", "截止": "deadline",
    "报工": "work report", "抽取": "extraction", "指标": "metric", "提醒": "reminder",
    "收货": "goods receipt", "区域": "area", "文件夹": "folder", "文档": "document",
    "是否": "whether", "暂停": "pause", "秒数": "seconds", "有效期": "validity", "月数": "months",
    "本题": "current question", "分值": "score", "得分": "score", "前缀": "prefix", "来文": "incoming",
    "标准答案": "standard answer", "样本": "sample", "档位": "tier", "检验": "inspection",
    "每班": "per shift", "每题": "per question", "流水": "serial", "位数": "digits",
    "班组": "team", "组长": "leader", "生成": "generated", "答案": "answer", "解析": "analysis",
    "纳税": "tax", "结果": "result", "缴纳": "payment", "考勤组": "attendance group",
    "节点": "node", "标识": "identifier", "行程序号": "itinerary sequence", "被": "assigned",
    "来源": "source", "评分": "score", "费用": "expense", "发生": "occurrence", "车型": "vehicle type",
    "运单号": "waybill number", "退回": "reject", "采集点": "collection point", "银行": "bank",
    "零件号": "part number", "计划停机": "planned downtime", "领料": "material requisition",
    "题型": "question type", "年份": "year", "最早": "earliest", "最晚": "latest",
    "最大": "maximum", "最小": "minimum", "流水位数": "serial digits",
    "发票号码": "invoice number", "非计划": "unplanned", "退": "return", "位数": "digits",
    "种子": "seed", "及格分": "passing score", "方式": "method", "名单": "roster", "分配": "assignment",
    "总数": "total", "产量": "output", "值": "value", "分钟数": "minutes", "天数": "days",
    "停机": "downtime", "单批": "single batch", "条规则": "rules", "道": "questions",
    "识别号": "identification number", "文号": "document number", "单": "single", "号": "number",
    "总": "total", "总产量": "total output",
    "或": "or", "、": "/", "大写字母": "uppercase letters", "数字": "digits",
    "下划线": "underscores", "派生": "derived",
    # --- 全后端补齐（services/registry/ops/common/sdk）---
    "页码": "page number", "每页条数": "page size", "每页": "per page",
    "认领人": "claimant", "认领": "claim", "告警": "alert", "级别": "level",
    "指纹": "fingerprint", "监控": "monitoring", "比较": "comparison", "运算符": "operator",
    "通知": "notification", "通道": "channel", "接收人": "recipient", "关键字": "keyword",
    "静默": "silence", "创建人": "creator", "语言环境": "locale", "模块": "module",
    "字母": "letters", "开头": "start", "含": "including", "组合": "combination",
    "的": "", "级": "level", "生命周期": "lifecycle", "取值": "value",
}

# 规则：中文触发词 → (rule 后缀, 英文模板)。{f}=字段英文, {n}=数字, {e}=枚举/后缀
RULE_PATTERNS = [
    # (正则, rule, 英文模板) —— 顺序敏感：更具体的规则在前
    (r"不能为空|不得为空|不可为空|必须填写|必须指定|必须选择|请填写|请选择|请指定|请提供|请确认|请至少选择|必须关联|至少需要|至少包含一个|至少包含一项|至少需要一行|至少需要一条",
     "required", "{f} must not be empty"),
    (r"长度不能超过\s*(\d+)|长度不得超过\s*(\d+)|长度不能大于\s*(\d+)|长度不能小于\s*(\d+)|最多\s*(\d+)\s*(?:个?字|字符|位|条|次|天|分钟|道|美元|个)|最长\s*(\d+)|不能超过\s*(\d+)\s*(?:个?字|字符)|不得超过\s*(\d+)\s*(?:个?字|字符)|不得多于\s*(\d+)",
     "size", "{f} must not exceed {n} characters"),
    (r"不得为负数|不能为负数|不得为负|不能为负|必须为正|不能小于\s*0|不能低于\s*0|不能为负|必须大于\s*0|必须大于\s*(\d+)|不能少于\s*(\d+)|不能小于\s*(\d+)|不能低于\s*(\d+)|必须大于\s*(\d+)|至少\s*(\d+)|至少为\s*(\d+)|最小为\s*(\d+)|从\s*(\d+)\s*开始|不能早于\s*(\d+)|不能早于|不能小于",
     "min", "{f} must not be negative"),
    (r"不能超过\s*(\d+)|不能大于\s*(\d+)|不得大于\s*(\d+)|高于\s*(\d+)|不超过\s*(\d+)|不得超过\s*(\d+)\s*字符|最大为\s*(\d+)|不能晚于今天|不能晚于\s*(\d+)|不能晚于\s*(\d+)\s*年|不能晚于",
     "max", "{f} must not exceed the maximum"),
    (r"不能高于\s*(\d+)|评分不能高于|效率不能大于",
     "max", "{f} must not exceed the maximum"),
    (r"仅支持\s*(.+)|只允许\s*(.+)|只能为\s*(.+)|只能是\s*(.+)|仅含\s*(.+)|取值仅为\s*(.+)",
     "pattern", "{f} must be {e}"),
    (r"格式应为\s*(.+)|必须为\s*(.+)|需以\s*(.+)|必须以\s*(.+)|格式不正确|格式错误|不合法|异常|非法|须以大写字母开头",
     "pattern", "{f} has an invalid format"),
    (r"取值\s*(\d+)\s*-\s*(\d+)|取值范围\s*(.+)|只能为\s*0\s*或\s*1|只能为\s*\d+\s*或\s*\d+",
     "range", "{f} is out of range"),
]

ANNOTATION_RULE = {
    "NotBlank": "required", "NotNull": "required", "NotEmpty": "required",
    "Size": "size", "Length": "size", "Pattern": "pattern", "Email": "email",
    "Min": "min", "DecimalMin": "min", "Positive": "min", "PositiveOrZero": "min",
    "Max": "max", "DecimalMax": "max", "Negative": "max", "NegativeOrZero": "max",
    "Digits": "digits", "Future": "future", "Past": "past",
}

SUFFIXES = [
    "SaveRequest", "UpdateRequest", "CreateRequest", "QueryRequest", "BatchRequest",
    "SaveCommand", "UpdateCommand", "CreateCommand", "DeleteCommand", "QueryCommand",
    "Request", "Command", "Param", "Params", "Form", "DTO", "VO", "PO", "Req", "Query", "Input",
]


def kebab(name: str) -> str:
    s = re.sub(r"(?<!^)(?=[A-Z])", "-", name).lower()
    return s.replace("_", "-")


def entity_of(class_name: str) -> str:
    name = class_name
    for suf in SUFFIXES:
        if name.endswith(suf):
            name = name[: -len(suf)]
            break
    return kebab(name)


def translate_field(zh: str) -> str:
    """把中文字段描述翻成英文短语：按中文词在原文中的位置顺序拼接，保持修饰关系。"""
    # 贪心切分：从左到右扫描，每位置取最长匹配词
    matched: list[tuple[int, str]] = []  # (位置, 英文)
    i = 0
    sorted_terms = sorted(TERMS, key=len, reverse=True)
    while i < len(zh):
        hit = None
        for term in sorted_terms:
            if zh.startswith(term, i):
                hit = (i, TERMS[term], len(term))
                break
        if hit:
            pos, eng, ln = hit
            matched.append((pos, eng))
            i += ln
        else:
            i += 1
    # 未匹配到的中文字符 → 翻译失败
    covered = set()
    for term in sorted_terms:
        start = 0
        while True:
            idx = zh.find(term, start)
            if idx < 0:
                break
            for k in range(idx, idx + len(term)):
                covered.add(k)
            start = idx + len(term)
    leftover = "".join(ch for j, ch in enumerate(zh) if j not in covered)
    if HAN.search(leftover):
        return None
    # 按出现位置排序，同位置按先长后短已被贪心保证；去重保序
    seen, uniq = set(), []
    for _, eng in sorted(matched, key=lambda x: x[0]):
        if eng not in seen:
            seen.add(eng)
            uniq.append(eng)
    return " ".join(uniq)


def strip_zh_rules(zh: str) -> str:
    """从完整 message 中剥掉规则部分，留下字段描述。"""
    desc = zh
    rules = ["长度不能超过", "长度不得超过", "长度不能大于", "长度不能小于", "不得为负数", "不能为负数",
             "不得为负", "不能为负", "必须为正", "必须大于", "不能少于", "不能小于", "不能低于",
             "不能大于", "不得大于", "不能晚于今天", "不能晚于", "不能早于", "不能超过", "不得超过",
             "不得多于", "不能为空", "不得为空", "不可为空", "必须填写", "必须指定", "必须选择",
             "请填写", "请选择", "请指定", "请提供", "请确认", "请至少选择", "必须关联",
             "至少需要", "至少包含", "至少需要一行", "至少需要一条", "仅支持", "只允许", "只能为",
             "只能是", "仅含", "最多抽取", "最多", "最长", "至少", "取值", "取值范围", "格式应为",
             "必须为", "需以", "必须以", "不合法", "异常", "非法", "格式不正确", "格式错误",
             "须以大写字母开头", "最大为", "最小为", "必须从", "不能高于", "不能为负数", "必须为"]
    for r in sorted(rules, key=len, reverse=True):
        idx = desc.find(r)
        if idx >= 0:
            desc = desc[:idx]
    # 去掉数字与单位尾部
    desc = re.sub(r"\s*\d+.*$", "", desc)
    return desc.strip(" 　")


def infer_rule(annotation: str) -> str:
    m = re.match(r"@(\w+)", annotation)
    return ANNOTATION_RULE.get(m.group(1), "pattern") if m else "pattern"


def cap_phrase(phrase: str) -> str:
    """首字母大写，其余保持（保留 'Work order code' 风格）。"""
    return phrase[:1].upper() + phrase[1:] if phrase else phrase


def build_en_message(zh: str, field_en: str, annotation: str) -> str | None:
    """用规则模板生成英文文案；字段英文来自词典。"""
    label = cap_phrase(field_en)
    for pattern, rule, template in RULE_PATTERNS:
        m = re.search(pattern, zh)
        if not m:
            continue
        nums = [g for g in m.groups() if g and g.isdigit()]
        n = nums[0] if nums else ""
        # 枚举/后缀：取匹配到的非数字分组
        enum = next((g for g in m.groups() if g and not g.isdigit()), "")
        enum = re.sub(r"^(?:取值范围?|仅含|只允许?|只能是?|仅支持)\s*", "", enum).strip()
        # 枚举里可能夹中文（"CODE128 或 QR"、"大写字母/数字/下划线"），逐词翻译
        for term in sorted(TERMS, key=len, reverse=True):
            if term in enum:
                enum = enum.replace(term, TERMS[term])
        enum = re.sub(r"(?<=[A-Za-z])start\b", " start", enum)
        enum = re.sub(r"\s+", " ", enum).strip()
        try:
            return template.format(f=label, n=n, e=enum)
        except (KeyError, IndexError):
            return None
    # fallback：按注解规则的通用英文
    rule = infer_rule(annotation)
    generic = {
        "required": f"{label} must not be empty",
        "size": f"{label} has invalid length",
        "min": f"{label} must not be negative",
        "max": f"{label} exceeds the maximum",
        "pattern": f"{label} has an invalid format",
        "range": f"{label} is out of range",
        "email": f"{label} must be a valid email",
    }
    return generic.get(rule)


def _matching_paren(text: str, open_pos: int) -> int:
    """字符串感知的括号配对，避免 regexp="^(?i)..." 里的括号干扰。"""
    depth = 0
    quote = None
    escaped = False
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
        elif ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return i
    return -1


def _strip_annotations(text: str) -> str:
    """把注解（含参数与跨行）从字段声明片段里去掉，保留类型与字段名。"""
    out: list[str] = []
    i = 0
    while i < len(text):
        if text[i] == "@" and re.match(r"@\w+", text[i:]):
            m = re.match(r"@\w+", text[i:])
            j = i + len(m.group(0))
            while j < len(text) and text[j].isspace():
                j += 1
            if j < len(text) and text[j] == "(":
                end = _matching_paren(text, j)
                if end >= 0:
                    i = end + 1
                    continue
            i = j
            continue
        out.append(text[i])
        i += 1
    return "".join(out)


def _field_after(text: str, pos: int) -> str | None:
    """从注解结束位置向后找归属字段；支持跨行注解与泛型内注解。"""
    snippet = " ".join(text[pos:pos + 2400].splitlines()[:16])
    snippet = re.sub(r"/\*.*?\*/", " ", snippet, flags=re.S)
    snippet = re.sub(r"//.*?(?=\n|$)", " ", snippet)
    snippet = _strip_annotations(snippet)
    for m in re.finditer(r"\b(?:private|protected|public)\b[^;{}]*;", snippet, re.S):
        decl = m.group(0).split("=", 1)[0]
        names = re.findall(r"\b[A-Za-z_$][\w$]*\b", decl)
        if names:
            return names[-1]
    return None


def _field_around(text: str, ann_start: int, ann_end: int) -> str | None:
    """先向后找字段；找不到再向前找当前字段声明的修饰符（泛型内注解场景）。"""
    field = _field_after(text, ann_end)
    if field:
        return field
    region_start = max(0, ann_start - 2400)
    for cm in reversed(list(re.finditer(r"\b(?:private|protected|public)\b", text[region_start:ann_start]))):
        pos = region_start + cm.start()
        segment = text[pos:ann_start]
        if ";" in segment or "{" in segment or "}" in segment:
            break
        return _field_after(text, pos)
    return None


def parse_java(text: str):
    """解析出 (annotation_full, annotation_type, field_name, class_name, zh_message)。

    支持：跨行注解、注解之间夹空行/注释、泛型内部注解、嵌套静态类。
    """
    results: list[tuple[str, str, str, str, str]] = []
    lines = text.splitlines()
    class_lines: list[int] = []
    class_names: list[str] = []
    line_starts = [0]
    for line in lines:
        line_starts.append(line_starts[-1] + len(line) + 1)
    for idx, line in enumerate(lines):
        cm = re.match(r"\s*(?:public\s+|private\s+|protected\s+|static\s+|final\s+|abstract\s+|sealed\s+|non-sealed\s+)*(?:class|record|interface)\s+(\w+)", line)
        if cm:
            class_lines.append(idx + 1)
            class_names.append(cm.group(1))

    for m in re.finditer(r"@([A-Za-z_$][\w$]*)\s*\(", text):
        ann_type = m.group(1)
        if ann_type not in ANNOTATION_RULE:
            continue
        open_pos = m.end() - 1
        close = _matching_paren(text, open_pos)
        if close < 0:
            continue
        block = text[m.start():close + 1]
        zh_m = re.search(r'message\s*=\s*"([^"]*[\u4e00-\u9fff][^"]*)"', block)
        if not zh_m:
            continue
        field = _field_around(text, m.start(), close + 1)
        if not field:
            continue
        line_no = bisect.bisect_left(line_starts, m.start())
        class_idx = bisect.bisect_right(class_lines, line_no) - 1
        cls = class_names[class_idx] if class_idx >= 0 else "Unknown"
        results.append((re.sub(r"\s+", " ", block), ann_type, field, cls, zh_m.group(1)))
    return results


def infer_module(path: Path) -> str:
    """从 Java 路径推断 i18n 模块名：com/bluewhale/<module>/...。"""
    m = re.search(r"com[\\/]bluewhale[\\/](\w+)", str(path))
    return m.group(1) if m else path.parent.name


def scan_tree(root: Path, module: str | None = None):
    """扫描单个 Maven 模块的 src/main/java，返回待迁移注解条目。"""
    java_root = root / "src" / "main" / "java"
    if not java_root.exists():
        return []
    entries = []
    for p in sorted(java_root.rglob("*.java")):
        if "target" in p.parts or "src/test" in str(p):
            continue
        mod = module or infer_module(p)
        txt = p.read_text(encoding="utf-8")
        # 子域：包名 com.bluewhale.<module>.<subdomain> 中的 subdomain，用于同名 DTO 消歧
        subdomain = ""
        pm = re.search(r"com[\\/]bluewhale[\\/]" + re.escape(mod) + r"[\\/](\w+)", str(p))
        if pm:
            subdomain = kebab(pm.group(1))
        for block, ann_type, field, cls, zh in parse_java(txt):
            entries.append({
                "path": p, "module": mod, "entity": entity_of(cls), "field": kebab(field),
                "field_java": field, "class": cls, "ann_type": ann_type, "zh": zh, "block": block,
                "subdomain": subdomain,
            })
    return entries


def scan_app(app: str):
    app_dir = APPS / app if app.startswith("whale-") else APPS / f"whale-{app}"
    module = app.replace("whale-", "")
    if not app_dir.exists():
        print(f"应用不存在: {app_dir}", file=sys.stderr)
        return []
    return scan_tree(app_dir, module)


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    p_scan = sub.add_parser("scan")
    p_scan.add_argument("app")
    p_mig = sub.add_parser("migrate")
    p_mig.add_argument("app")
    p_mig.add_argument("--commit", action="store_true", help="实际写入；默认 dry-run")
    p_all = sub.add_parser("migrate-all")
    p_all.add_argument("--commit", action="store_true", help="实际写入；默认 dry-run")
    sub.add_parser("regen-en")
    sub.add_parser("check")
    args = ap.parse_args()

    if args.cmd == "scan":
        entries = scan_app(args.app)
        print(f"=== {args.app}: {len(entries)} 处中文校验 message ===")
        untranslated = []
        for e in entries:
            desc = strip_zh_rules(e["zh"])
            fen = translate_field(desc)
            rule = infer_rule("@" + e["ann_type"])
            key = f"validation.{e['module']}.{e['entity']}.{e['field']}-{rule}"
            flag = "" if fen else "  <-- 需人工词典"
            print(f"  {key:60} | {e['zh']}{flag}")
            if not fen:
                untranslated.append(e["zh"])
        print(f"\n未识别字段描述 {len(set(untranslated))} 种:")
        for u in sorted(set(untranslated)):
            print("   ", u)
        return

    if args.cmd == "check":
        check_all()
        return

    if args.cmd == "migrate":
        migrate_app(args.app, commit=args.commit)
        return

    if args.cmd == "migrate-all":
        migrate_all(commit=args.commit)
        return

    if args.cmd == "regen-en":
        regen_en()
        return

    if args.cmd == "check":
        sys.exit(check_all())


def build_plan(entries):
    """返回 (key→{zh,en,module,entry}, 替换映射)；同名 DTO 冲突时用子域前缀消歧。"""
    plan: dict[str, dict] = {}
    conflicts = []
    # entry_index: 最终每个 entry 采用 key（替换阶段要用同一 key）
    assignment: dict[int, str] = {}
    for idx, e in enumerate(entries):
        rule = infer_rule("@" + e["ann_type"])
        desc = strip_zh_rules(e["zh"])
        fen = translate_field(desc)
        if fen is None:
            conflicts.append(f"字段描述无法翻译: {e['zh']}")
            continue
        # 字段描述为空（如 "whale.file.xxx 必须大于 0"）时回退到 key 字段名
        label_source = fen if fen else field_label_from_key(
            f"validation.{e['module']}.{e['entity']}.{e['field']}-{infer_rule('@' + e['ann_type'])}")
        en = build_en_message(e["zh"], label_source, "@" + e["ann_type"])
        if en is None:
            conflicts.append(f"规则模板未命中: {e['zh']}")
            continue
        base = f"validation.{e['module']}.{e['entity']}.{e['field']}-{rule}"
        key = base
        if key in plan and plan[key]["zh"] != e["zh"]:
            # 冲突：尝试用子域前缀消歧
            if e.get("subdomain") and e["subdomain"] != e["entity"]:
                key = f"validation.{e['module']}.{e['subdomain']}-{e['entity']}.{e['field']}-{rule}"
            if key in plan and plan[key]["zh"] != e["zh"]:
                conflicts.append(f"key 冲突 {key}: '{plan[key]['zh']}' vs '{e['zh']}'")
                continue
        plan[key] = {"zh": e["zh"], "en": en, "module": e["module"]}
        assignment[idx] = key
    return plan, conflicts, assignment


I18N_DEPENDENCY = """        <!-- 国际化 SDK：按请求 Locale 解析 {validation.*} 校验文案键，本应用自带 i18n 文案 -->
        <dependency>
            <groupId>com.bluewhale</groupId>
            <artifactId>whale-i18n-sdk</artifactId>
            <version>${project.version}</version>
        </dependency>
"""


def add_i18n_dependency(root: Path, module: str, commit: bool):
    """给模块 pom 幂等添加 whale-i18n-sdk 依赖；i18n/common 自身不加（自环/循环依赖）。"""
    if module in ("i18n", "common"):
        return False
    pom = root / "pom.xml"
    if not pom.exists():
        return False
    txt = pom.read_text(encoding="utf-8")
    if "<artifactId>whale-i18n-sdk</artifactId>" in txt:
        return False
    anchor = re.search(
        r"(<dependency>\s*<groupId>com\.bluewhale</groupId>\s*<artifactId>whale-spring-boot-starter</artifactId>.*?</dependency>)",
        txt, re.S)
    if anchor:
        txt = txt[: anchor.end()] + "\n" + I18N_DEPENDENCY + txt[anchor.end() :]
    else:
        idx = txt.find("</dependencies>")
        if idx < 0:
            print(f"  WARN {root.name}: 未找到 dependencies 锚点，跳过加依赖")
            return False
        txt = txt[:idx] + I18N_DEPENDENCY + txt[idx:]
    if commit:
        pom.write_text(txt, encoding="utf-8")
    return True


def migrate_all(commit: bool):
    """迁移 whale-backend 全部含中文校验 message 的 Maven 模块。"""
    todo = []
    for pom in sorted(BACKEND.rglob("pom.xml")):
        if "target" in pom.parts:
            continue
        root = pom.parent
        entries = scan_tree(root)
        if entries:
            todo.append((root, entries[0]["module"], entries))
    print(f"=== 待迁移 {len(todo)} 个模块, 共 {sum(len(e) for _, _, e in todo)} 处 ===")
    for root, module, entries in todo:
        label = root.relative_to(BACKEND)
        print(f"\n### {label} [{module}]: {len(entries)} 处")
        added = add_i18n_dependency(root, module, commit)
        print(f"  pom 依赖: {'已添加' if added else '已存在/跳过'}")
        migrate_root(root, module, commit, str(label))


def migrate_app(app: str, commit: bool):
    app_dir = APPS / app if app.startswith("whale-") else APPS / f"whale-{app}"
    module = app.replace("whale-", "")
    migrate_root(app_dir, module, commit, app)


def migrate_root(root: Path, module: str, commit: bool, label: str):
    entries = scan_tree(root, module)
    plan, conflicts, assignment = build_plan(entries)
    print(f"=== {label}: {len(entries)} 处 / {len(plan)} 个 key / {len(conflicts)} 冲突 ===")
    for c in conflicts:
        print("  CONFLICT:", c)
    if conflicts:
        print("存在冲突，中止迁移。请先修词典或规则。")
        return
    print("\n=== 英文词条预览（前 10）===")
    for key in sorted(plan)[:10]:
        print(f"  {key} = {plan[key]['en']}")
    if not commit:
        print("\n[DRY-RUN] 未写入。加 --commit 实际执行。")
        return
    # 按文件分组替换：每处用 assignment 中对应 key；同一 zh 文本多次出现按序消费
    by_file: dict[Path, list[tuple[str, str]]] = defaultdict(list)
    for idx, e in enumerate(entries):
        if idx not in assignment:
            continue
        by_file[e["path"]].append((e["zh"], assignment[idx]))
    for path, pairs in by_file.items():
        txt = path.read_text(encoding="utf-8")
        # 按 zh 文本分组，保持 key 顺序
        queues: dict[str, list[str]] = defaultdict(list)
        for zh, key in pairs:
            queues[zh].append(key)
        for zh, keys in queues.items():
            old = f'message = "{zh}"'
            for key in keys:
                new = f'message = "{{{key}}}"'
                pos = txt.find(old)
                if pos < 0:
                    print(f"  WARN 未找到待替换文本: {path.name}: {old}")
                    continue
                txt = txt[:pos] + new + txt[pos + len(old) :]
        path.write_text(txt, encoding="utf-8")
        print(f"  已替换 {path.relative_to(BACKEND)}")
    # 写三份词条文件；i18n-sdk 沿用其根 messages 兜底源
    res_base = root / "src" / "main" / "resources" / "i18n"
    res_dir = res_base if module == "i18n" else res_base / module
    res_dir.mkdir(parents=True, exist_ok=True)
    keys = sorted(plan)
    header = "# 校验提示文案（由 scripts/i18n/migrate_validation_messages.py 生成）\n"

    def merge_write(path: Path, new_lines: list[str]):
        """增量合并：保留已有 key，新 key 覆盖同名。"""
        merged: dict[str, str] = {}
        if path.exists():
            for line in path.read_text(encoding="utf-8").splitlines():
                s = line.strip()
                if s and not s.startswith("#") and "=" in s:
                    k, v = s.split("=", 1)
                    merged[k] = v
        for line in new_lines:
            k, v = line.split("=", 1)
            merged[k] = v
        path.write_text(header + "\n".join(f"{k}={merged[k]}" for k in sorted(merged)) + "\n", encoding="utf-8")

    merge_write(res_dir / "messages.properties", [f"{k}={plan[k]['zh']}" for k in keys])
    merge_write(res_dir / "messages_zh_CN.properties", [f"{k}={plan[k]['zh']}" for k in keys])
    merge_write(res_dir / "messages_en_US.properties", [f"{k}={plan[k]['en']}" for k in keys])
    print(f"  已合并词条: {res_dir}/messages*.properties (新增 {len(keys)} 键)")


RULE_TO_ANNOTATION = {
    "required": "NotBlank", "size": "Size", "pattern": "Pattern", "min": "Min",
    "max": "Max", "range": "Min", "email": "Email", "digits": "Digits",
    "future": "Future", "past": "Past",
}


def field_label_from_key(key: str) -> str:
    """从 key 的 field 段生成英文标签（比中文描述可靠：key 一定是字段名）。"""
    parts = key.split(".")
    tail = parts[-1]
    rule = RULE_SET_MATCH.findall(tail)
    # field = 去掉末尾 -<rule>
    field = tail.rsplit("-", 1)[0] if "-" in tail else tail
    words = field.replace("-", " ").split()
    out = []
    for w in words:
        if w.isupper() or any(c.isdigit() for c in w):
            out.append(w)
        else:
            out.append(w[:1].upper() + w[1:])
    return " ".join(out)


RULE_SET = {"required", "size", "pattern", "min", "max", "range", "email", "digits", "future", "past"}
RULE_SET_MATCH = re.compile(r"-(?:" + "|".join(RULE_SET) + r")$")


def regen_en():
    """补齐 en_US 中缺失/仍为中文的 validation 键，保留已有英文译文。"""
    count = 0
    for zh_file in sorted(BACKEND.rglob("messages.properties")):
        if "target" in zh_file.parts or "src/test" in str(zh_file) or "i18n" not in zh_file.parts:
            continue
        en_file = zh_file.with_name("messages_en_US.properties")
        existing: dict[str, str] = {}
        if en_file.exists():
            for line in en_file.read_text(encoding="utf-8").splitlines():
                s = line.strip()
                if s and not s.startswith("#") and "=" in s:
                    k, v = s.split("=", 1)
                    existing[k] = v
        entries: dict[str, str] = {}
        for line in zh_file.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, zh = line.split("=", 1)
            if key in existing and not HAN.search(existing[key]):
                # 已有英文译文（含非 validation 业务词条）一律保留
                entries[key] = existing[key]
                continue
            if not key.startswith("validation."):
                entries[key] = existing.get(key, zh)
                continue
            rule = key.rsplit("-", 1)[-1]
            annotation = RULE_TO_ANNOTATION.get(rule, "Pattern")
            # 优先用中文描述翻译；纯规则文案描述为空时回退到 key 字段名
            desc = strip_zh_rules(zh)
            fen = translate_field(desc) if desc else None
            label = fen if fen else field_label_from_key(key)
            en = build_en_message(zh, label, "@" + annotation)
            entries[key] = en if en else existing.get(key, zh)
            if en:
                count += 1
        header = "# 校验提示文案（由 scripts/i18n/migrate_validation_messages.py 生成）\n"
        en_file.write_text(header + "\n".join(f"{k}={entries[k]}" for k in sorted(entries)) + "\n", encoding="utf-8")
        print(f"  刷新 {en_file.relative_to(BACKEND)}")
    print(f"已补齐英文词条 {count} 条")


def check_all():
    """完整性门禁：注解引用的 key 在三份 properties 齐全、en 无中文、key 集合对齐。"""
    problems = []
    # 收集所有 {validation.xxx} 引用
    refs = defaultdict(set)
    for root, dirs, files in os.walk(BACKEND):
        dirs[:] = [d for d in dirs if d != "target"]
        for f in files:
            if not f.endswith(".java"):
                continue
            p = Path(root) / f
            if "src/test" in str(p) or "test" in p.parts:
                continue
            txt = p.read_text(encoding="utf-8")
            for key in re.findall(r'\{(validation\.[^}]+)\}', txt):
                if key.endswith(".xxx") or "example" in key or "*" in key:
                    continue  # 文档/代码中的示例占位符
                refs[key].add(str(p))
    # 收集三份 properties 的 key（按所在目录分组；只看 i18n/<module>/ 结构）
    bundles = defaultdict(dict)
    for p in BACKEND.rglob("messages*.properties"):
        if ("target" in p.parts or "test" in p.parts or "src/test" in str(p)
                or "i18n" not in p.parts):
            continue
        keys = set()
        for line in p.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                keys.add(line.split("=", 1)[0])
        bundles[str(p.parent)][p.name] = keys
    # 每个引用 key 必须在某个 messages.properties 中存在
    all_default_keys = set()
    for files in bundles.values():
        all_default_keys |= files.get("messages.properties", set())
    for key in refs:
        if key not in all_default_keys:
            problems.append(f"注解引用 {key} 无对应 messages.properties 词条 (引用于 {list(refs[key])[0]})")
    # 每个 bundle 三份 key 对齐 + en 无中文
    for path, files in bundles.items():
        default = files.get("messages.properties", set())
        for variant in ("messages_zh_CN.properties", "messages_en_US.properties"):
            other = files.get(variant, set())
            missing = default - other
            extra = other - default
            if missing:
                problems.append(f"{path}: {variant} 缺 {len(missing)} 键: {sorted(missing)[:3]}")
            if extra:
                problems.append(f"{path}: {variant} 多 {len(extra)} 键: {sorted(extra)[:3]}")
        en = Path(path) / "messages_en_US.properties"
        if en.exists():
            for line in en.read_text(encoding="utf-8").splitlines():
                s = line.strip()
                if s and not s.startswith("#") and HAN.search(s):
                    problems.append(f"{en}: 英文词条含中文: {s[:60]}")
    if problems:
        print(f"校验失败：{len(problems)} 项")
        for p in problems[:50]:
            print("  FAIL:", p)
        return 1
    print(f"校验通过：{len(refs)} 个校验 key 引用 / {len(bundles)} 组词条文件，全部齐全且中英文对齐。")
    return 0


if __name__ == "__main__":
    sys.exit(main() or 0)
