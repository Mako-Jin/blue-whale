// 登录页 - 对接 whale-service-auth 接口（POST /auth/login，经网关转发）

import { useState } from 'react';
import { App, Button, Card, Form, Input } from 'antd';
import { useNavigate } from 'react-router-dom';
import { login } from '../api/auth';

interface LoginFormValues {
  tenantCode?: string;
  username: string;
  password: string;
}

export default function LoginPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const handleFinish = async (values: LoginFormValues) => {
    setLoading(true);
    try {
      await login(values);
      message.success('登录成功');
      navigate('/monitor', { replace: true });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '登录失败，请稍后重试');
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
            <Button type="primary" htmlType="submit" block loading={loading}>
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
