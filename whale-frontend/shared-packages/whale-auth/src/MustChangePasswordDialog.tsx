/**
 * 强制改密弹窗（消费 mustChangePassword / passwordExpired，REQ-SEC-023）。
 *
 * 登录成功且 mustChangePassword=true（首次登录、管理员重置口令或口令到期）时由各登录页渲染：
 * 不可关闭，需输入原口令 + 新口令（≥12 位且含大小写字母、数字与特殊字符）+ 确认一致后提交
 * PUT /auth/password（带内存 accessToken，凭据体不进审计日志）。成功后尽力登出旧会话
 * （清刷新 Cookie）并回调 onSuccess——各应用清登录态并回登录页用新口令重新登录。
 *
 * 纯 React 实现（不依赖 antd），供所有带登录页的应用复用。
 */

import { useState, type CSSProperties, type FormEvent } from 'react';
import { detectUiLang, type UiLang } from './language';
import { changePasswordMessages, type ChangePasswordMessages } from './mustChangePasswordMessages';
import { getCsrfToken } from './refresh';

/** 密码强度要求：≥12 位且同时包含大写字母、小写字母、数字与特殊字符（与后端默认策略一致） */
function validatePasswordStrength(password: string, msg: ChangePasswordMessages): string | null {
  if (password.length < 12) {
    return msg.errorTooShort;
  }
  if (!/[a-z]/.test(password)) {
    return msg.errorNeedLower;
  }
  if (!/[A-Z]/.test(password)) {
    return msg.errorNeedUpper;
  }
  if (!/[0-9]/.test(password)) {
    return msg.errorNeedDigit;
  }
  if (!/[^A-Za-z0-9]/.test(password)) {
    return msg.errorNeedSpecial;
  }
  return null;
}

export interface MustChangePasswordDialogProps {
  /**
   * 当前用户 ID
   *
   * @deprecated 自助改密接口已改为 PUT /auth/password（身份取自令牌），保留该属性仅为兼容既有调用方
   */
  userId?: number | string;
  /** 后端基地址；生产同源反代、开发 vite proxy，通常传空串 */
  baseUrl?: string;
  /** 读取内存 accessToken（注入 Authorization） */
  getToken: () => string | null;
  /** 改密成功后的回调（提示用新口令重新登录并清登录态跳登录页） */
  onSuccess: () => void;
  /**
   * 界面语言。接入 I18nProvider 的应用（admin）应显式传入以实时跟随切换；
   * 未传时回落到共享语言工具（读 whale-admin-lang，无记录固定中文），兼容无 i18n 的调用方。
   */
  language?: UiLang;
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
  baseUrl = '',
  getToken,
  onSuccess,
  language,
}: MustChangePasswordDialogProps) {
  const lang = language ?? detectUiLang();
  const msg = changePasswordMessages[lang];
  const [oldPassword, setOldPassword] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting) {
      return;
    }
    if (!oldPassword) {
      setError(msg.errorOldPasswordRequired);
      return;
    }
    const weakness = validatePasswordStrength(password, msg);
    if (weakness) {
      setError(weakness);
      return;
    }
    if (password !== confirm) {
      setError(msg.errorConfirmMismatch);
      return;
    }
    if (password === oldPassword) {
      setError(msg.errorSameAsOld);
      return;
    }
    const token = getToken();
    if (!token) {
      setError(msg.errorTokenInvalid);
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const response = await fetch(`${baseUrl}/auth/v1/password`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        credentials: 'include',
        body: JSON.stringify({ oldPassword, newPassword: password }),
      });
      const payload = (await response.json().catch(() => null)) as {
        code?: number;
        message?: string;
      } | null;
      if (!response.ok || !payload || payload.code !== 0) {
        // 42801：服务端要求 step-up 凭证。待改密用户调 /auth/v1/reauth 会被 403「请先修改口令」
        // 拒绝，再弹一次验密只会更绕——直接给出可执行的提示（后端已取消该端点的二次认证，
        // 此处是历史配置或网关策略变动时的兜底）。
        if (response.status === 428 || payload?.code === 42801) {
          setError(msg.errorReauth);
          return;
        }
        setError(
          payload?.message ?? msg.errorHttp.replace('{status}', String(response.status)),
        );
        return;
      }
      // 尽力登出旧会话（服务端撤销存量令牌并清刷新 Cookie）；失败不阻断本地清态
      try {
        const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
        const csrf = getCsrfToken();
        if (csrf) {
          headers['X-CSRF-Token'] = csrf;
        }
        await fetch(`${baseUrl}/auth/v1/logout`, {
          method: 'POST',
          headers,
          credentials: 'include',
        });
      } catch {
        // 忽略登出网络异常
      }
      onSuccess();
    } catch {
      setError(msg.errorNetwork);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={overlayStyle}>
      <form style={cardStyle} onSubmit={handleSubmit}>
        <h3 style={{ margin: 0, fontSize: 16 }}>{msg.title}</h3>
        <p style={{ margin: '8px 0 0', fontSize: 13, color: 'rgba(0, 0, 0, 0.65)' }}>
          {msg.description}
        </p>
        <label style={labelStyle}>{msg.oldPassword}</label>
        <input
          style={inputStyle}
          type="password"
          value={oldPassword}
          autoComplete="current-password"
          autoFocus
          onChange={(e) => setOldPassword(e.target.value)}
        />
        <label style={labelStyle}>{msg.newPassword}</label>
        <input
          style={inputStyle}
          type="password"
          value={password}
          autoComplete="new-password"
          onChange={(e) => setPassword(e.target.value)}
        />
        <label style={labelStyle}>{msg.confirmPassword}</label>
        <input
          style={inputStyle}
          type="password"
          value={confirm}
          autoComplete="new-password"
          onChange={(e) => setConfirm(e.target.value)}
        />
        <div style={hintStyle}>{msg.strengthHint}</div>
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
          {submitting ? msg.submitting : msg.submit}
        </button>
      </form>
    </div>
  );
}
