// CRM 子应用通用文案 / 字典 / 时间格式化

/** 客户状态下拉选项 */
export const CUSTOMER_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: '有效' },
  { value: 'INACTIVE', label: '停用' },
  { value: 'MERGED', label: '已合并' },
];

/** 客户状态文案 */
export const CUSTOMER_STATUS_LABEL: Record<string, string> = {
  ACTIVE: '有效',
  INACTIVE: '停用',
  MERGED: '已合并',
};

/** 客户等级下拉选项 */
export const CUSTOMER_LEVEL_OPTIONS = [
  { value: 'A', label: 'A 重点' },
  { value: 'B', label: 'B 普通' },
  { value: 'C', label: 'C 潜力' },
  { value: 'D', label: 'D 观察' },
];

/** 客户等级文案（兼容存量 KEY / NORMAL / POTENTIAL） */
export const CUSTOMER_LEVEL_LABEL: Record<string, string> = {
  A: 'A 重点',
  B: 'B 普通',
  C: 'C 潜力',
  D: 'D 观察',
  KEY: 'A 重点',
  NORMAL: 'C 潜力',
  POTENTIAL: 'D 观察',
};

/** 公海状态文案 */
export const POOL_STATUS_LABEL: Record<string, string> = {
  OWNED: '私有',
  IN_POOL: '公海',
};

/** 跟进渠道下拉选项 */
export const FOLLOW_UP_CHANNEL_OPTIONS = [
  { value: 'CALL', label: '电话' },
  { value: 'VISIT', label: '拜访' },
  { value: 'EMAIL', label: '邮件' },
  { value: 'IM', label: '即时通讯' },
  { value: 'WECHAT', label: '微信' },
  { value: 'OTHER', label: '其他' },
];

/** 跟进渠道文案 */
export const FOLLOW_UP_CHANNEL_LABEL: Record<string, string> = {
  CALL: '电话',
  VISIT: '拜访',
  EMAIL: '邮件',
  IM: '即时通讯',
  WECHAT: '微信',
  OTHER: '其他',
};

/** 负责人变更动作文案 */
export const OWNER_LOG_ACTION_LABEL: Record<string, string> = {
  CLAIM: '领取',
  RELEASE: '释放',
  ASSIGN: '分配',
  RECYCLE: '回收',
};

/** 格式化后端 LocalDateTime（兼容 ISO 与 yyyy-MM-dd HH:mm:ss） */
export function formatDateTime(value?: string | null): string {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
}

/** 线索状态下拉选项 */
export const LEAD_STATUS_OPTIONS = [
  { value: 'NEW', label: '新线索' },
  { value: 'CONTACTED', label: '已联系' },
  { value: 'QUALIFIED', label: '已确认' },
  { value: 'CONVERTED', label: '已转化' },
  { value: 'INVALID', label: '无效' },
];

/** 线索状态文案 */
export const LEAD_STATUS_LABEL: Record<string, string> = {
  NEW: '新线索',
  CONTACTED: '已联系',
  QUALIFIED: '已确认',
  CONVERTED: '已转化',
  INVALID: '无效',
};

/** 线索状态标签色 */
export const LEAD_STATUS_COLOR: Record<string, string> = {
  NEW: 'blue',
  CONTACTED: 'cyan',
  QUALIFIED: 'green',
  CONVERTED: 'purple',
  INVALID: 'red',
};

/** 商机阶段下拉选项 */
export const OPPORTUNITY_STAGE_OPTIONS = [
  { value: 'CONTACT', label: '接洽' },
  { value: 'PROPOSAL', label: '方案' },
  { value: 'NEGOTIATION', label: '谈判' },
  { value: 'WON', label: '赢单' },
  { value: 'LOST', label: '输单' },
];

/** 商机阶段文案 */
export const OPPORTUNITY_STAGE_LABEL: Record<string, string> = {
  CONTACT: '接洽',
  PROPOSAL: '方案',
  NEGOTIATION: '谈判',
  WON: '赢单',
  LOST: '输单',
};

/** 商机阶段标签色 */
export const OPPORTUNITY_STAGE_COLOR: Record<string, string> = {
  CONTACT: 'blue',
  PROPOSAL: 'cyan',
  NEGOTIATION: 'orange',
  WON: 'green',
  LOST: 'red',
};

/** 报价状态下拉选项 */
export const QUOTE_STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING_APPROVAL', label: '审批中' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
];

/** 报价状态文案 */
export const QUOTE_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_APPROVAL: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
};

/** 报价状态标签色 */
export const QUOTE_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
};

/** 预测 / 目标周期类型下拉选项 */
export const FORECAST_PERIOD_TYPE_OPTIONS = [
  { value: 'MONTH', label: '月度' },
  { value: 'QUARTER', label: '季度' },
];

/** 预测 / 目标周期类型文案 */
export const FORECAST_PERIOD_TYPE_LABEL: Record<string, string> = {
  MONTH: '月度',
  QUARTER: '季度',
};

/** 预测 / 目标统计范围下拉选项 */
export const FORECAST_SCOPE_TYPE_OPTIONS = [
  { value: 'USER', label: '按负责人' },
  { value: 'DEPT', label: '按部门' },
];

/** 预测 / 目标统计范围文案 */
export const FORECAST_SCOPE_TYPE_LABEL: Record<string, string> = {
  USER: '按负责人',
  DEPT: '按部门',
};

/** 预测调整字段下拉选项 */
export const FORECAST_FIELD_OPTIONS = [
  { value: 'PIPELINE', label: '管道金额' },
  { value: 'BEST', label: '最佳金额' },
  { value: 'COMMIT', label: '承诺金额' },
];

/** 预测调整字段文案 */
export const FORECAST_FIELD_LABEL: Record<string, string> = {
  PIPELINE: '管道金额',
  BEST: '最佳金额',
  COMMIT: '承诺金额',
};

/** 合同续约状态下拉选项 */
export const CONTRACT_RENEWAL_OPTIONS = [
  { value: 'PENDING', label: '待续约' },
  { value: 'RENEWED', label: '已续约' },
  { value: 'NOT_RENEWED', label: '不续约' },
];

/** 合同续约状态文案 */
export const CONTRACT_RENEWAL_LABEL: Record<string, string> = {
  PENDING: '待续约',
  RENEWED: '已续约',
  NOT_RENEWED: '不续约',
};

/** 合同续约状态标签色 */
export const CONTRACT_RENEWAL_COLOR: Record<string, string> = {
  PENDING: 'gold',
  RENEWED: 'green',
  NOT_RENEWED: 'red',
};

/** 回款计划状态下拉选项 */
export const COLLECTION_STATUS_OPTIONS = [
  { value: 'OPEN', label: '进行中' },
  { value: 'CANCELLED', label: '已取消' },
];

/** 回款计划状态文案 */
export const COLLECTION_STATUS_LABEL: Record<string, string> = {
  OPEN: '进行中',
  CANCELLED: '已取消',
};

/** 回款催收优先级文案 */
export const COLLECTION_PRIORITY_LABEL: Record<string, string> = {
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
};

/** 回款催收优先级标签色 */
export const COLLECTION_PRIORITY_COLOR: Record<string, string> = {
  HIGH: 'red',
  MEDIUM: 'orange',
  LOW: 'default',
};

/** 催收动作下拉选项 */
export const COLLECTION_ACTION_OPTIONS = [
  { value: 'CALL', label: '电话催收' },
  { value: 'VISIT', label: '上门催收' },
  { value: 'EMAIL', label: '邮件催收' },
  { value: 'REMINDER', label: '系统提醒' },
  { value: 'PROMISE', label: '承诺还款' },
];

/** 催收动作文案 */
export const COLLECTION_ACTION_LABEL: Record<string, string> = {
  CALL: '电话催收',
  VISIT: '上门催收',
  EMAIL: '邮件催收',
  REMINDER: '系统提醒',
  PROMISE: '承诺还款',
};

/** 商务订单状态下拉选项 */
export const ORDER_STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING_APPROVAL', label: '审批中' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'PUSHING', label: '下发中' },
  { value: 'PUSHED', label: '已下发' },
  { value: 'PUSH_FAILED', label: '下发失败' },
  { value: 'CANCELLED', label: '已取消' },
];

/** 商务订单状态文案 */
export const ORDER_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_APPROVAL: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  PUSHING: '下发中',
  PUSHED: '已下发',
  PUSH_FAILED: '下发失败',
  CANCELLED: '已取消',
};

/** 商务订单状态标签色 */
export const ORDER_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
  PUSHING: 'processing',
  PUSHED: 'green',
  PUSH_FAILED: 'red',
  CANCELLED: 'default',
};

/** 商务订单来源类型文案 */
export const ORDER_SOURCE_LABEL: Record<string, string> = {
  MANUAL: '手工创建',
  QUOTE: '报价转订单',
  CONTRACT: '合同转订单',
};

/** 开票申请状态下拉选项 */
export const INVOICE_STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING_APPROVAL', label: '审批中' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'ISSUED', label: '已开票' },
  { value: 'SENT', label: '已寄送' },
  { value: 'RED_FLUSHED', label: '已红冲' },
  { value: 'CANCELLED', label: '已取消' },
];

/** 开票申请状态文案 */
export const INVOICE_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_APPROVAL: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  ISSUED: '已开票',
  SENT: '已寄送',
  RED_FLUSHED: '已红冲',
  CANCELLED: '已取消',
};

/** 开票申请状态标签色 */
export const INVOICE_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
  ISSUED: 'green',
  SENT: 'cyan',
  RED_FLUSHED: 'purple',
  CANCELLED: 'default',
};

/** 开票申请类型文案 */
export const INVOICE_REQUEST_TYPE_LABEL: Record<string, string> = {
  NORMAL: '正常开票',
  RED_FLUSH: '红冲',
};

/** 发票类型文案 */
export const INVOICE_TYPE_LABEL: Record<string, string> = {
  VAT_SPECIAL: '增值税专用发票',
  VAT_NORMAL: '增值税普通发票',
  ELECTRONIC: '电子发票',
};

/** 抬头类型文案 */
export const INVOICE_TITLE_TYPE_LABEL: Record<string, string> = {
  COMPANY: '单位',
  PERSONAL: '个人',
};
