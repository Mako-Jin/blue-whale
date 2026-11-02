/**
 * 首登强制改密弹窗（消费 T-03 的 mustChangePassword）。
 *
 * 登录成功且 mustChangePassword=true 时由各登录页渲染：不可关闭，要求
 * 新密码（≥12 位且含大小写字母与数字）+ 确认一致后提交
 * PUT /user/{id}/password（带内存 accessToken），成功后尽力登出旧会话
 * （清刷新 Cookie）并回调 onSuccess——各应用清登录态并回登录页重新登录。
 *
 * 纯 React 实现（不依赖 antd），供所有带登录页的应用复用。
 */

import { useState, type CSSProperties, type FormEvent } from 'react';
import { getCsrfToken } from './refresh';

/** 密码强度要求：≥12 位且同时包含大写字母、小写字母与数字 */
function validatePasswordStrength(password: string): string | null {
  if (password.length < 12) {
    return '密码长度至少 12 位';
  }
  if (!/[a-z]/.test(password)) {
    return '密码需包含小写字母';
  }
  if (!/[A-Z]/.test(password)) {
    return '密码需包含大写字母';
  }
  if (!/[0-9]/.test(password)) {
    return '密码需包含数字';
  }
  return null;
}

export interface MustChangePasswordDialogProps {
  /** 当前用户 ID（PUT /user/{id}/password 路径参数；取登录响应或缓存用户） */
  userId?: number | string;
  /** 后端基地址；生产同源反代、开发 vite proxy，通常传空串 */
  baseUrl?: string;
  /** 读取内存 accessToken（注入 Authorization） */
  getToken: () => string | null;
  /** 改密成功后的回调（提示用新口令重新登录并清登录态跳登录页） */
  onSuccess: () => void;
}

const overlayStyle: CSSProperties = {
  position: 'fixed',
  inset: 0,
  zIndex: 3000,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  background: 'rgba(0, 0, 0, 0.55)',
};

const cardStyle: CSSProperties = {
  width: 400,
  maxWidth: 'calc(100vw - 48px)',
  padding: 24,
  borderRadius: 8,
  background: '#fff',
  boxShadow: '0 6px 24px rgba(0, 0, 0, 0.2)',
};

const labelStyle: CSSProperties = {
  display: 'block',
  margin: '12px 0 4px',
  fontSize: 13,
  color: 'rgba(0, 0, 0, 0.65)',
};

const inputStyle: CSSProperties = {
  width: '100%',
  boxSizing: 'border-box',
  padding: '7px 11px',
  fontSize: 14,
  border: '1px solid #d9d9d9',
  borderRadius: 6,
  outline: 'none',
};

const dangerStyle: CSSProperties = { color: '#ff4d4f', fontSize: 12, marginTop: 8 };
const hintStyle: CSSProperties = { color: 'rgba(0, 0, 0, 0.45)', fontSize: 12, marginTop: 8 };

export function MustChangePasswordDialog({
  userId,
  baseUrl = '',
  getToken,
  onSuccess,
}: MustChangePasswordDialogProps) {
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting) {
      return;
    }
    const weakness = validatePasswordStrength(password);
    if (weakness) {
      setError(weakness);
      return;
    }
    if (password !== confirm) {
      setError('两次输入的新密码不一致');
      return;
    }
    if (userId === undefined || userId === null || userId === '') {
      setError('登录响应缺少用户 ID，无法修改密码，请重新登录');
      return;
    }
    const token = getToken();
    if (!token) {
      setError('登录态已失效，请重新登录');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const response = await fetch(`${baseUrl}/user/${userId}/password`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        credentials: 'include',
        body: JSON.stringify({ password }),
      });
      const payload = (await response.json().catch(() => null)) as {
        code?: number;
        message?: string;
      } | null;
      if (!response.ok || !payload || payload.code !== 0) {
        setError(payload?.message ?? `密码修改失败（HTTP ${response.status}）`);
        return;
      }
      // 尽力登出旧会话（服务端清刷新 Cookie）；失败不阻断本地清态
      try {
        const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
        const csrf = getCsrfToken();
        if (csrf) {
          headers['X-CSRF-Token'] = csrf;
        }
        await fetch(`${baseUrl}/auth/logout`, {
          method: 'POST',
          headers,
          credentials: 'include',
        });
      } catch {
        // 忽略登出网络异常
      }
      onSuccess();
    } catch {
      setError('网络异常，请稍后重试');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={overlayStyle}>
      <form style={cardStyle} onSubmit={handleSubmit}>
        <h3 style={{ margin: 0, fontSize: 16 }}>首次登录，请修改密码</h3>
        <p style={{ margin: '8px 0 0', fontSize: 13, color: 'rgba(0, 0, 0, 0.65)' }}>
          安全策略要求首次登录（或密码被重置）后必须设置新密码。
        </p>
        <label style={labelStyle}>新密码</label>
        <input
          style={inputStyle}
          type="password"
          value={password}
          autoComplete="new-password"
          autoFocus
          onChange={(e) => setPassword(e.target.value)}
        />
        <label style={labelStyle}>确认新密码</label>
        <input
          style={inputStyle}
          type="password"
          value={confirm}
          autoComplete="new-password"
          onChange={(e) => setConfirm(e.target.value)}
        />
        <div style={hintStyle}>至少 12 位，且包含大写字母、小写字母和数字</div>
        {error ? <div style={dangerStyle}>{error}</div> : null}
        <button
          type="submit"
          disabled={submitting}
          style={{
            marginTop: 16,
            width: '100%',
            padding: '8px 0',
            fontSize: 14,
            color: '#fff',
            background: submitting ? '#4096ff' : '#1677ff',
            border: 'none',
            borderRadius: 6,
            cursor: submitting ? 'default' : 'pointer',
          }}
        >
          {submitting ? '提交中…' : '修改密码并重新登录'}
        </button>
      </form>
    </div>
  );
}
