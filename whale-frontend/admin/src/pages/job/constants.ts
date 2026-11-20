// 定时任务管理页常量与类型：表单值类型、查询参数类型、下拉选项、文案/颜色映射

import type { MessageKey } from '../../i18n/messages';

/** 翻译函数签名（由 useI18n 注入） */
type Translate = (key: MessageKey, vars?: Record<string, string | number>) => string;

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

/** 错过执行策略选项 */
export function buildMisfireOptions(t: Translate) {
  return [
    { value: 'SMART', label: t('job.option.misfireSmart') },
    { value: 'FIRE_ONCE', label: t('job.option.misfireFireOnce') },
    { value: 'DO_NOTHING', label: t('job.option.misfireDoNothing') },
  ];
}

/** 任务状态选项 */
export function buildJobStatusOptions(t: Translate) {
  return [
    { value: 1, label: t('job.dag.status.running') },
    { value: 0, label: t('job.dag.status.stopped') },
  ];
}

/** 日志状态选项 */
export function buildLogStatusOptions(t: Translate) {
  return [
    { value: 1, label: t('job.log.status.success') },
    { value: 0, label: t('job.log.status.running') },
    { value: -1, label: t('job.log.status.failed') },
  ];
}

/** 触发方式文案映射 */
export function buildTriggerTypeLabels(t: Translate): Record<string, string> {
  return {
    CRON: t('job.trigger.cron'),
    MANUAL: t('job.trigger.manual'),
    CHAIN: t('job.trigger.chain'),
  };
}

export const ALERT_LEVEL_COLOR: Record<string, string> = {
  INFO: 'blue',
  WARN: 'warning',
  WARNING: 'warning',
  ERROR: 'error',
  CRITICAL: 'error',
};

/** 告警级别选项 */
export function buildAlertLevelOptions(t: Translate) {
  return [
    { value: 'INFO', label: t('job.option.levelInfo') },
    { value: 'WARN', label: 'WARN' },
    { value: 'ERROR', label: 'ERROR' },
  ];
}

/** 告警通道：channel 为逗号拼接值，查询按单通道模糊匹配 */
export function buildAlertChannelOptions(t: Translate) {
  return [
    { value: 'LOG', label: t('job.option.channelLog') },
    { value: 'WEBHOOK', label: 'WEBHOOK' },
    { value: 'DINGTALK', label: 'DINGTALK' },
    { value: 'FEISHU', label: 'FEISHU' },
  ];
}
