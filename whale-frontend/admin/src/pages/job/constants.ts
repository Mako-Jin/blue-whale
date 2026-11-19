// 定时任务管理页常量与类型：表单值类型、查询参数类型、下拉选项、文案/颜色映射

export interface JobFormValues {
  jobName: string;
  jobGroup?: string;
  cronExpression: string;
  handlerBean: string;
  handlerParam?: string;
  misfirePolicy?: string;
  maxRetry?: number;
  retryIntervalSeconds?: number;
  shardTotal?: number;
  status?: number;
  remark?: string;
  /** 上游依赖任务 ID（仅编辑场景，任务本身字段之外单独提交；后端 Long 序列化为字符串） */
  upstreamJobIds?: string[];
}

export type JobQuery = { keyword?: string; status?: number };
export type JobLogQuery = { jobId?: string; status?: number };
export type JobAlertQuery = { jobId?: string; level?: string; channel?: string };

export const MISFIRE_OPTIONS = [
  { value: 'SMART', label: 'SMART（智能补跑）' },
  { value: 'FIRE_ONCE', label: 'FIRE_ONCE（补跑一次）' },
  { value: 'DO_NOTHING', label: 'DO_NOTHING（忽略）' },
];

export const JOB_STATUS_OPTIONS = [
  { value: 1, label: '运行中' },
  { value: 0, label: '已停止' },
];

export const LOG_STATUS_OPTIONS = [
  { value: 1, label: '成功' },
  { value: 0, label: '执行中' },
  { value: -1, label: '失败' },
];

export const TRIGGER_TYPE_LABELS: Record<string, string> = {
  CRON: '定时触发',
  MANUAL: '手动触发',
  CHAIN: '链式触发',
};

export const ALERT_LEVEL_COLOR: Record<string, string> = {
  INFO: 'blue',
  WARN: 'warning',
  WARNING: 'warning',
  ERROR: 'error',
  CRITICAL: 'error',
};

export const ALERT_LEVEL_OPTIONS = [
  { value: 'INFO', label: 'INFO（恢复通知）' },
  { value: 'WARN', label: 'WARN' },
  { value: 'ERROR', label: 'ERROR' },
];

/** 告警通道：channel 为逗号拼接值，查询按单通道模糊匹配 */
export const ALERT_CHANNEL_OPTIONS = [
  { value: 'LOG', label: 'LOG（仅流水）' },
  { value: 'WEBHOOK', label: 'WEBHOOK' },
  { value: 'DINGTALK', label: 'DINGTALK' },
  { value: 'FEISHU', label: 'FEISHU' },
];
