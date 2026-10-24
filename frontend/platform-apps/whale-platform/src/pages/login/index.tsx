// 登录页 - 对接 whale-auth 真实接口（POST /auth/login，经网关转发）

import { useState } from 'react';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { App, Button, Card, Form, Input } from 'antd';
import { useNavigate } from 'react-router-dom';
import { fetchMe, login } from '../../api/auth';

interface LoginFormValues {
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
      await login(values.username, values.password);
      try {
        // 拉取权限码与菜单，供子应用共享；失败不阻断登录，进入页面后仍可按需重取
        await fetchMe();
      } catch {
        // 权限拉取失败不阻断登录
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
          <div className="platform-logo-mark login-logo-mark">B</div>
          <h1>BlueWhale 微服务平台</h1>
          <p>统一身份认证</p>
        </div>
        <Form<LoginFormValues>
          onFinish={handleFinish}
          initialValues={{ username: 'admin', password: '' }}
          size="large"
        >
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input
              prefix={<UserOutlined />}
              placeholder="请输入用户名"
              autoComplete="username"
            />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="请输入密码"
              autoComplete="current-password"
            />
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
