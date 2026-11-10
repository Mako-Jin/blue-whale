// 登录页 - 对接 whale-service-auth 真实接口（POST /auth/login，经网关转发）
// 首登强制改密：mustChangePassword=true 时弹出不可关闭对话框（T-03）

import { useState } from 'react';
import { App, Button, Card, Form, Input } from 'antd';
import { useNavigate } from 'react-router-dom';
import { MustChangePasswordDialog } from '@bluewhale/whale-auth';
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
  /** 待强制改密的登录响应（非 null 时渲染不可关闭对话框） */
  const [pendingChange, setPendingChange] = useState<LoginResponse | null>(null);

  const handleFinish = async (values: LoginFormValues) => {
    setLoading(true);
    try {
      const data = await login(values.username, values.password, values.tenantCode);
      if (data.mustChangePassword) {
        // 首登/密码被重置：不进入系统，先完成强制改密（成功后回登录页用新口令重登）
        setPendingChange(data);
        return;
      }
      message.success('登录成功');
      navigate('/', { replace: true });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '登录失败，请稍后重试');
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
          <p>后台管理平台 · 统一身份认证</p>
        </div>
        <Form<LoginFormValues>
          onFinish={handleFinish}
          initialValues={{ tenantCode: 'default', username: 'admin', password: '' }}
          size="large"
        >
          <Form.Item name="tenantCode">
            <Input placeholder="租户编码（默认 default）" autoComplete="organization" />
          </Form.Item>
          <Form.Item name="username">
            <Input placeholder="用户名（admin）" autoComplete="username" />
          </Form.Item>
          <Form.Item name="password">
            <Input.Password placeholder="密码（123456）" autoComplete="current-password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" block loading={loading}>
              登录
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
