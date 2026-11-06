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
