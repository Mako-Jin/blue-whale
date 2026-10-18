/**
 * 前端日志 SDK 常量（Wave 5.14）。
 *
 * <p>端点、上限、默认参数、掩码键名一律集中于此，避免散落魔法值（清单 D6）。</p>
 */

import type { LogLevel } from './types';

/** 通道③：前端开发者日志上报端点（与后端 LogIngestController 冻结契约一致）。 */
export const FRONTEND_ENDPOINT = '/audit/log/frontend/batch';

/** 通道④：前端用户行为上报端点。 */
export const BEHAVIOR_ENDPOINT = '/audit/log/behavior/batch';

/** 单次上报条目上限（与后端 MAX_BATCH_SIZE=50 对齐，超出即分片）。 */
export const MAX_BATCH_SIZE = 50;

/** 默认触发 flush 的队列长度阈值。 */
export const DEFAULT_BATCH_SIZE = 20;

/** 默认触发 flush 的时间间隔（毫秒）。 */
export const DEFAULT_BATCH_INTERVAL_MS = 5000;

/** 默认最大额外重试次数。 */
export const DEFAULT_MAX_RETRIES = 2;

/** 默认指数退避基数（毫秒）。 */
export const DEFAULT_BACKOFF_BASE_MS = 1000;

/** 默认采样率（1 表示全量采集）。 */
export const DEFAULT_SAMPLE_RATE = 1;

/** 默认点击埋点属性名。 */
export const DEFAULT_TRACK_ATTR = 'data-track';

/** 默认劫持的 console 级别。 */
export const DEFAULT_CONSOLE_LEVELS: LogLevel[] = ['DEBUG', 'INFO', 'WARN', 'ERROR'];

/** 追踪ID请求头（与网关/后端 X-Trace-Id 对齐）。 */
export const HEADER_TRACE_ID = 'X-Trace-Id';

/** 追踪ID会话存储键。 */
export const STORAGE_KEY_TRACE_ID = 'whale:traceId';

/** 浏览器会话ID存储键。 */
export const STORAGE_KEY_CLIENT_ID = 'whale:clientId';

/** 本地登录态用户存储键（与 whale-platform api/auth 约定一致）。 */
export const STORAGE_KEY_USER = 'whale_user';

/** 整体掩码占位符（密码等凭据）。 */
export const MASK_FULL = '******';

/** 令牌掩码占位符。 */
export const MASK_TOKEN = '***';

/** 银行卡掩码前缀。 */
export const MASK_BANK_PREFIX = '**** **** **** ';

/** 邮箱掩码中段。 */
export const MASK_EMAIL_MIDDLE = '***';

/** 手机号保留前 3 位。 */
export const MOBILE_PREFIX_LENGTH = 3;

/** 手机号保留后 4 位。 */
export const MOBILE_SUFFIX_LENGTH = 4;

/** 身份证保留前 6 位。 */
export const ID_CARD_PREFIX_LENGTH = 6;

/** 身份证保留后 4 位。 */
export const ID_CARD_SUFFIX_LENGTH = 4;

/** 银行卡保留后 4 位。 */
export const BANK_CARD_SUFFIX_LENGTH = 4;

/** 递归掩码深度上限。 */
export const MAX_MASK_DEPTH = 8;

/** 掩码字符。 */
export const MASK_CHAR = '*';

/** 密码类字段（与后端敏感键内置规则对齐，归一化后匹配）。 */
export const SECRET_KEYS: string[] = ['password', 'oldpassword', 'newpassword', 'confirmpassword'];

/** 令牌类字段。 */
export const TOKEN_KEYS: string[] = ['token', 'authorization', 'accesstoken', 'refreshtoken'];

/** 手机号类字段。 */
export const MOBILE_KEYS: string[] = ['phone', 'mobile', 'telephone'];

/** 身份证类字段。 */
export const ID_CARD_KEYS: string[] = ['idcard', 'idno', 'identitycard'];

/** 银行卡类字段。 */
export const BANK_CARD_KEYS: string[] = ['bankcard', 'cardno', 'bankcardno'];

/** 邮箱类字段。 */
export const EMAIL_KEYS: string[] = ['email', 'mail'];

/** 默认上报端点（供文档与默认配置引用）。 */
export const DEFAULT_ENDPOINTS = {
  frontend: FRONTEND_ENDPOINT,
  behavior: BEHAVIOR_ENDPOINT,
} as const;
