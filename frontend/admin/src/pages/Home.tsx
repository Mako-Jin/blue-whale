// 后台首页 - 展示登录态，支持校验令牌有效性 + 登出（验证黑名单生效）

import { useState } from 'react';
import { App, Button, Card, Descriptions, Space, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { fetchMe, getCurrentUser, logout, type AuthMeResponse } from '../api/auth';

export default function HomePage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [me, setMe] = useState<AuthMeResponse | null>(null);
  const [checking, setChecking] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const user = getCurrentUser();

  const handleCheck = async () => {
    setChecking(true);
    try {
      const result = await fetchMe();
      setMe(result);
      message.success('令牌有效：GET /auth/me 调用成功');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '令牌校验失败');
    } finally {
      setChecking(false);
    }
  };

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await logout();
      message.success('已退出登录，令牌已加入黑名单');
      navigate('/login', { replace: true });
    } finally {
      setLoggingOut(false);
    }
  };

  return (
    <div style={{ padding: 24 }}>
      <Card title="BlueWhale Admin">
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="用户名">{user?.username ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="昵称">{user?.nickname ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="角色">
            <Space wrap>
              {(user?.roles ?? []).map((role) => (
                <Tag key={role} color="blue">
                  {role}
                </Tag>
              ))}
            </Space>
          </Descriptions.Item>
          {me && (
            <Descriptions.Item label="权限码">
              <Space wrap>
                {me.permissions.map((perm) => (
                  <Tag key={perm}>{perm}</Tag>
                ))}
              </Space>
            </Descriptions.Item>
          )}
        </Descriptions>
        <Space style={{ marginTop: 16 }}>
          <Button onClick={handleCheck} loading={checking}>
            校验当前令牌（GET /auth/me）
          </Button>
          <Button danger onClick={handleLogout} loading={loggingOut}>
            退出登录
          </Button>
        </Space>
      </Card>
    </div>
  );
}
