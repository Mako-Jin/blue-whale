// 登录失败锁定（业务码 42301）的前端处理：解析剩余秒数 + 倒计时状态
//
// 后端在锁定期间对每次尝试都返回 42301，若前端只弹一条 toast，用户会继续连点——
// 提示一闪而过，每次点击都重新计数（服务端窗口会被续期）。这里把锁定态落成
// 「按钮禁用 + 倒计时」，把剩余时间显式画在界面上。

import { useCallback, useEffect, useState } from 'react';

/** 账号锁定的业务码（与后端 AccountLockedException.CODE 保持一致） */
export const LOGIN_LOCKED_CODE = 42301;

/**
 * 后端未携带 retryAfterSeconds 时的兜底倒计时秒数。
 *
 * 取 60 秒而不是直接不解锁：宁可短一些早放行（用户再点一次仍会拿到 42301 重新校准），
 * 也不要把按钮永久钉死。兜底只发生在后端 version 落后 / 代理裁掉 data 的极端情况。
 */
export const LOCKOUT_FALLBACK_SECONDS = 60;

/**
 * 从任意错误对象中解析锁定剩余秒数。
 *
 * @param error 登录接口抛出的错误（通常是 ApiError：{ code, message, data }）
 * @returns 处于锁定时返回剩余秒数（&gt; 0），不是锁定错误时返回 null
 */
export function parseLockoutSeconds(error: unknown): number | null {
  if (!error || typeof error !== 'object') {
    return null;
  }
  const code = (error as { code?: unknown }).code;
  if (code !== LOGIN_LOCKED_CODE) {
    return null;
  }
  const data = (error as { data?: unknown }).data;
  const raw =
    data && typeof data === 'object'
      ? (data as { retryAfterSeconds?: unknown }).retryAfterSeconds
      : undefined;
  // 兼容 Jackson 3：Long 一律序列化为字符串（实测 {"retryAfterSeconds":"898"}），
  // 只认 number 会让每次锁定都退化成兜底 60 秒。
  const seconds = typeof raw === 'string' ? Number(raw) : raw;
  if (typeof seconds === 'number' && Number.isFinite(seconds) && seconds > 0) {
    // 后端给的是整秒，这里再向上取整一次，避免出现 "0 秒后可重试" 的闪烁
    return Math.ceil(seconds);
  }
  return LOCKOUT_FALLBACK_SECONDS;
}

/** 登录锁定状态的读取与登记接口 */
export interface LoginLockout {
  /** 当前是否处于锁定（剩余秒数 &gt; 0） */
  locked: boolean;
  /** 剩余锁定秒数（0 表示未锁定） */
  remainingSeconds: number;
  /**
   * 登记一次登录失败
   *
   * @param error 登录失败抛出的错误
   * @returns 该错误是否属于账号锁定（true 时页面应禁用提交并展示倒计时）
   */
  registerFailure: (error: unknown) => boolean;
  /** 登录成功后复位（清除倒计时） */
  clear: () => void;
}

/**
 * 登录锁定倒计时 hook：锁定期间每秒递减，归零自动解除。
 *
 * 倒计时是纯前端提示，服务端仍是唯一权威——页面刷新后倒计时丢失，此时再提交
 * 会立刻重新拿到 42301 与新的剩余秒数。
 *
 * @returns 锁定状态与登记/复位方法
 */
export function useLoginLockout(): LoginLockout {
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const locked = remainingSeconds > 0;

  useEffect(() => {
    if (!locked) {
      return undefined;
    }
    const timer = setInterval(() => {
      setRemainingSeconds((prev) => (prev <= 1 ? 0 : prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [locked]);

  const registerFailure = useCallback((error: unknown) => {
    const seconds = parseLockoutSeconds(error);
    if (seconds === null) {
      return false;
    }
    setRemainingSeconds(seconds);
    return true;
  }, []);

  const clear = useCallback(() => {
    setRemainingSeconds(0);
  }, []);

  return { locked, remainingSeconds, registerFailure, clear };
}

/**
 * 把剩余秒数格式化为 MM:SS
 *
 * @param seconds 剩余秒数
 * @returns 形如 "14:59" 的倒计时文案
 */
export function formatRemaining(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds));
  const minutes = Math.floor(safe / 60);
  const rest = safe % 60;
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`;
}
