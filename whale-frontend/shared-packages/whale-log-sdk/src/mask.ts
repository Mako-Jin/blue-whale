/**
 * 隐私掩码：结构敏感键递归掩码 + 自由文本掩码 + URL query 掩码。
 *
 * <p>掩码规则与后端 {@code SensitiveDataMasker} 内置规则对齐（AUTH-11 / REQ-SEC-006），
 * 保证前端上报体与后端落库前的脱敏口径一致。</p>
 */

import {
  BANK_CARD_KEYS,
  BANK_CARD_SUFFIX_LENGTH,
  EMAIL_KEYS,
  ID_CARD_KEYS,
  ID_CARD_PREFIX_LENGTH,
  ID_CARD_SUFFIX_LENGTH,
  MASK_BANK_PREFIX,
  MASK_CHAR,
  MASK_EMAIL_MIDDLE,
  MASK_FULL,
  MASK_TOKEN,
  MAX_MASK_DEPTH,
  MOBILE_KEYS,
  MOBILE_PREFIX_LENGTH,
  MOBILE_SUFFIX_LENGTH,
  SECRET_KEYS,
  TOKEN_KEYS,
} from './constants';

/** 掩码选项。 */
export interface MaskOptions {
  /** 附加敏感键名（并集匹配） */
  sensitiveKeys?: string[];
}

/** Bearer 令牌模式（先于凭据模式应用，避免 `Bearer <token>` 被误判为键值对）。 */
const BEARER_PATTERN = /(Bearer\s+)[A-Za-z0-9._-]+/g;
/** 凭据键值对文本模式（password / token / authorization 等），值部分排除 Bearer 形式。 */
const CREDENTIAL_PATTERN =
  /(password|pwd|passwd|token|accessToken|refreshToken|authorization|secret)(\s*[:=]\s*)("?(?!Bearer\b)[^"\s,;&]+"?)/gi;
/** 手机号模式（保留前 3 后 4）。 */
const MOBILE_PATTERN = /\b(1[3-9]\d)\d{4}(\d{4})\b/g;
/** 身份证模式（保留前 6 后 4，最后一位可为 X）。 */
const ID_CARD_PATTERN = /\b(\d{6})\d{8}(\d{3}[\dXx])\b/g;
/** 银行卡模式（保留后 4）。 */
const BANK_CARD_PATTERN = /\b(\d{12,15})(\d{4})\b/g;

/** 字段名归一化：小写并去掉 _ / -（与后端一致）。 */
function normalizeKey(key: string): string {
  return key.toLowerCase().replace(/_/g, '').replace(/-/g, '');
}

/** 手机号掩码：保留前 3 后 4，长度不足整体掩码。 */
function maskMobile(value: string): string {
  if (value.length < MOBILE_PREFIX_LENGTH + MOBILE_SUFFIX_LENGTH + 1) {
    return MASK_FULL;
  }
  return (
    value.slice(0, MOBILE_PREFIX_LENGTH) +
    MASK_CHAR.repeat(4) +
    value.slice(value.length - MOBILE_SUFFIX_LENGTH)
  );
}

/** 身份证掩码：保留前 6 后 4。 */
function maskIdCard(value: string): string {
  if (value.length < ID_CARD_PREFIX_LENGTH + ID_CARD_SUFFIX_LENGTH + 1) {
    return MASK_FULL;
  }
  return (
    value.slice(0, ID_CARD_PREFIX_LENGTH) +
    MASK_CHAR.repeat(value.length - ID_CARD_PREFIX_LENGTH - ID_CARD_SUFFIX_LENGTH) +
    value.slice(value.length - ID_CARD_SUFFIX_LENGTH)
  );
}

/** 银行卡掩码：仅保留后 4 位。 */
function maskBankCard(value: string): string {
  if (value.length < BANK_CARD_SUFFIX_LENGTH) {
    return MASK_FULL;
  }
  return MASK_BANK_PREFIX + value.slice(value.length - BANK_CARD_SUFFIX_LENGTH);
}

/** 邮箱掩码：保留首字符与域名。 */
function maskEmail(value: string): string {
  const atIndex = value.indexOf('@');
  if (atIndex <= 0) {
    return MASK_FULL;
  }
  return value.slice(0, 1) + MASK_EMAIL_MIDDLE + value.slice(atIndex);
}

/** 按字段名掩码字符串；未命中返回 null（表示无需掩码）。 */
function maskStringByKey(
  normalizedKey: string,
  value: string,
  extraKeys: Set<string>,
): string | null {
  if (SECRET_KEYS.includes(normalizedKey) || extraKeys.has(normalizedKey)) {
    return MASK_FULL;
  }
  if (TOKEN_KEYS.includes(normalizedKey)) {
    return MASK_TOKEN;
  }
  if (MOBILE_KEYS.includes(normalizedKey)) {
    return maskMobile(value);
  }
  if (ID_CARD_KEYS.includes(normalizedKey)) {
    return maskIdCard(value);
  }
  if (BANK_CARD_KEYS.includes(normalizedKey)) {
    return maskBankCard(value);
  }
  if (EMAIL_KEYS.includes(normalizedKey)) {
    return maskEmail(value);
  }
  return null;
}

/** 结构递归掩码（内部实现）。 */
function maskAny(value: unknown, extraKeys: Set<string>, depth: number): unknown {
  if (value === null || value === undefined) {
    return value;
  }
  if (depth > MAX_MASK_DEPTH) {
    return null;
  }
  if (Array.isArray(value)) {
    return value.map((item) => maskAny(item, extraKeys, depth + 1));
  }
  if (typeof value === 'object') {
    const source = value as Record<string, unknown>;
    const masked: Record<string, unknown> = {};
    for (const key of Object.keys(source)) {
      const normalized = normalizeKey(key);
      const raw = source[key];
      const text =
        typeof raw === 'string' ? raw : raw === null || raw === undefined ? '' : String(raw);
      const byKey = maskStringByKey(normalized, text, extraKeys);
      masked[key] = byKey !== null ? byKey : maskAny(raw, extraKeys, depth + 1);
    }
    return masked;
  }
  return value;
}

/** 自由文本掩码：凭据键值对 / Bearer / 手机号 / 身份证 / 银行卡。 */
export function maskText(text: string): string {
  if (!text) {
    return text;
  }
  return text
    .replace(BEARER_PATTERN, `$1${MASK_TOKEN}`)
    .replace(CREDENTIAL_PATTERN, `$1$2${MASK_FULL}`)
    .replace(ID_CARD_PATTERN, `$1${MASK_CHAR.repeat(8)}$2`)
    .replace(MOBILE_PATTERN, `$1${MASK_CHAR.repeat(4)}$2`)
    .replace(BANK_CARD_PATTERN, `${MASK_BANK_PREFIX}$2`);
}

/** 结构递归掩码：对对象/数组按字段名掩码，返回掩码后的副本。 */
export function maskSensitive(value: unknown, options: MaskOptions = {}): unknown {
  const extraKeys = new Set((options.sensitiveKeys ?? []).map(normalizeKey));
  return maskAny(value, extraKeys, 0);
}

/** URL query 掩码：按参数名命中敏感规则则掩码，其余保留。 */
export function maskUrl(url: string, options: MaskOptions = {}): string {
  if (!url) {
    return url;
  }
  const extraKeys = new Set((options.sensitiveKeys ?? []).map(normalizeKey));
  try {
    const parsed = new URL(url);
    for (const key of Array.from(parsed.searchParams.keys())) {
      const values = parsed.searchParams.getAll(key);
      const normalized = normalizeKey(key);
      const masked = values.map((value) => {
        const byKey = maskStringByKey(normalized, value, extraKeys);
        return byKey !== null ? byKey : value;
      });
      parsed.searchParams.delete(key);
      for (const value of masked) {
        parsed.searchParams.append(key, value);
      }
    }
    return parsed.toString();
  } catch {
    return url;
  }
}
