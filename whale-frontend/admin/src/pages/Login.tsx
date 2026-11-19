// 登录页 - 对接 whale-service-auth 真实接口（POST /auth/login，经网关转发）
// 首登强制改密：mustChangePassword=true 时弹出不可关闭对话框（T-03）

import { useState } from 'react';
import { App, Alert, Button, Card, Form, Input } from 'antd';
import { useNavigate } from 'react-router-dom';
import { MustChangePasswordDialog, formatRemaining, useLoginLockout } from '@bluewhale/whale-auth';
import { useI18n } from '../i18n';
import { clearAuth, getCurrentUser, getToken, login, type LoginResponse } from '../api/auth';

interface LoginFormValues {
  tenantCode?: string;
  username: string;
  password: string;
}

export default function LoginPage() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  /** 登录锁定倒计时（连续错口令触达上限时禁用提交，避免无效连点） */
  const lockout = useLoginLockout();
  /** 待强制改密的登录响应（非 null 时渲染不可关闭对话框） */
  const [pendingChange, setPendingChange] = useState<LoginResponse | null>(null);

  const handleFinish = async (values: LoginFormValues) => {
    setLoading(true);
    try {
      const data = await login(values.username, values.password, values.tenantCode);
      lockout.clear();
      if (data.mustChangePassword) {
        // 首登/密码被重置：不进入系统，先完成强制改密（成功后回登录页用新口令重登）
        setPendingChange(data);
        return;
      }
      message.success(t('login.success'));
      navigate('/', { replace: true });
    } catch (error) {
      // 42301 账号锁定：提示由倒计时横幅承接，不再叠加一条会被连续点击刷掉的 toast
      if (!lockout.registerFailure(error)) {
        message.error(error instanceof Error ? error.message : t('login.failed'));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <Card className="login-card">
        <div className="login-header">
          <div className="login-logo-mark">B</div>
          <h1>BlueWhale Admin</h1>
          <p>{t('app.loginTitle')}</p>
        </div>
        {lockout.locked ? (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message={t('login.locked', { remaining: formatRemaining(lockout.remainingSeconds) })}
          />
        ) : null}
        <Form<LoginFormValues>
          onFinish={handleFinish}
          initialValues={{ tenantCode: 'default', username: 'admin', password: '' }}
          size="large"
        >
          <Form.Item name="tenantCode">
            <Input placeholder={t('login.tenantPlaceholder')} autoComplete="organization" />
          </Form.Item>
          <Form.Item name="username">
            <Input placeholder={t('login.usernamePlaceholder')} autoComplete="username" />
          </Form.Item>
          <Form.Item name="password">
            <Input.Password placeholder={t('login.passwordPlaceholder')} autoComplete="current-password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button
              type="primary"
              htmlType="submit"
              block
              loading={loading}
              disabled={lockout.locked}
            >
              {lockout.locked
                ? t('login.submitLocked', { remaining: formatRemaining(lockout.remainingSeconds) })
                : t('login.submit')}
            </Button>
          </Form.Item>
        </Form>
      </Card>
      {pendingChange ? (
        <MustChangePasswordDialog
          userId={pendingChange.userId ?? getCurrentUser()?.userId}
          baseUrl={import.meta.env.VITE_API_BASE_URL ?? ''}
          getToken={getToken}
          onSuccess={() => {
            // 清登录态（内存令牌 + 用户缓存），提示用新口令重新登录
            clearAuth();
            setPendingChange(null);
            message.success(t('login.passwordChanged'));
          }}
        />
      ) : null}
    </div>
  );
}
