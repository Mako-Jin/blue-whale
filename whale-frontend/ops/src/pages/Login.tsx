// 登录页 - 对接 whale-service-auth 接口（POST /auth/login，经网关转发）
// 首登强制改密：mustChangePassword=true 时弹出不可关闭对话框（T-03）

import { useState } from 'react';
import { App, Alert, Button, Card, Form, Input } from 'antd';
import { useNavigate } from 'react-router-dom';
import { MustChangePasswordDialog, formatRemaining, useLoginLockout } from '@bluewhale/whale-auth';
import { clearAuth, getCurrentUser, getToken, login, type LoginResponse } from '../api/auth';

interface LoginFormValues {
  tenantCode?: string;
  username: string;
  password: string;
}

export default function LoginPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  /** 登录锁定倒计时（连续错口令触达上限时禁用提交，避免无效连点） */
  const lockout = useLoginLockout();
  /** 待强制改密的登录响应（非 null 时渲染不可关闭对话框） */
  const [pendingChange, setPendingChange] = useState<LoginResponse | null>(null);

  const handleFinish = async (values: LoginFormValues) => {
    setLoading(true);
    try {
      const data = await login(values);
      lockout.clear();
      if (data.mustChangePassword) {
        // 首登/密码被重置：不进入系统，先完成强制改密（成功后回登录页用新口令重登）
        setPendingChange(data);
        return;
      }
      message.success('登录成功');
      navigate('/monitor', { replace: true });
    } catch (error) {
      // 42301 账号锁定：提示由倒计时横幅承接，不再叠加一条会被连续点击刷掉的 toast
      if (!lockout.registerFailure(error)) {
        message.error(error instanceof Error ? error.message : '登录失败，请稍后重试');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="ops-login-page">
      <Card className="ops-login-card">
        <div className="ops-login-header">
          <div className="ops-login-logo">B</div>
          <h1>BlueWhale Ops</h1>
          <p>运维平台 · 统一身份认证</p>
        </div>
        {lockout.locked ? (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message={`账号已锁定，${formatRemaining(lockout.remainingSeconds)} 后可重试`}
          />
        ) : null}
        <Form<LoginFormValues>
          onFinish={handleFinish}
          initialValues={{ tenantCode: 'default', username: 'admin', password: '' }}
          size="large"
        >
          <Form.Item name="tenantCode">
            <Input placeholder="租户编码（默认 default）" autoComplete="organization" />
          </Form.Item>
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input placeholder="用户名（admin）" autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password placeholder="密码" autoComplete="current-password" />
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
                ? `已锁定，${formatRemaining(lockout.remainingSeconds)} 后可重试`
                : '登录'}
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
            message.success('密码修改成功，请使用新密码重新登录');
          }}
        />
      ) : null}
    </div>
  );
}
