// 仪表盘 - 展示登录态、权限码数量与令牌校验入口

import { useState } from 'react';
import { App, Button, Card, Descriptions, Space, Tag } from 'antd';
import { usePermission } from '@bluewhale/whale-ui';
import { fetchMe, getCurrentUser, type AuthMeResponse } from '../api/auth';

export default function HomePage() {
  const { message } = App.useApp();
  const { permissions } = usePermission();
  const [me, setMe] = useState<AuthMeResponse | null>(null);
  const [checking, setChecking] = useState(false);
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

  const shownPermissions = me?.permissions ?? permissions;

  return (
    <Card title="仪表盘">
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
        <Descriptions.Item label={`权限码（${shownPermissions.length}）`}>
          <Space wrap>
            {shownPermissions.map((perm) => (
              <Tag key={perm}>{perm}</Tag>
            ))}
          </Space>
        </Descriptions.Item>
      </Descriptions>
      <Space style={{ marginTop: 16 }}>
        <Button onClick={() => void handleCheck()} loading={checking}>
          校验当前令牌（GET /auth/me）
        </Button>
      </Space>
      <p className="admin-hint">
        提示：进入「用户 / 角色 / 菜单管理」页可验证按钮级权限——未授权的操作按钮会被自动隐藏。
      </p>
    </Card>
  );
}
