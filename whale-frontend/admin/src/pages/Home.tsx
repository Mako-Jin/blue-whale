// 仪表盘 - 展示登录态、权限码数量与令牌校验入口

import { useState } from 'react';
import { App, Button, Card, Descriptions, Space, Tag } from 'antd';
import { usePermission } from '@bluewhale/whale-ui';
import { fetchMe, getCurrentUser, type AuthMeResponse } from '../api/auth';
import { useI18n } from '../i18n';

export default function HomePage() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const { permissions } = usePermission();
  const [me, setMe] = useState<AuthMeResponse | null>(null);
  const [checking, setChecking] = useState(false);
  const user = getCurrentUser();

  const handleCheck = async () => {
    setChecking(true);
    try {
      const result = await fetchMe();
      setMe(result);
      message.success(t('home.tokenValid'));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('home.tokenInvalid'));
    } finally {
      setChecking(false);
    }
  };

  const shownPermissions = me?.permissions ?? permissions;

  return (
    <Card title={t('home.title')}>
      <Descriptions column={1} bordered size="small">
        <Descriptions.Item label={t('home.username')}>{user?.username ?? '-'}</Descriptions.Item>
        <Descriptions.Item label={t('home.nickname')}>{user?.nickname ?? '-'}</Descriptions.Item>
        <Descriptions.Item label={t('home.roles')}>
          <Space wrap>
            {(user?.roles ?? []).map((role) => (
              <Tag key={role} color="blue">
                {role}
              </Tag>
            ))}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label={t('home.permissions', { count: shownPermissions.length })}>
          <Space wrap>
            {shownPermissions.map((perm) => (
              <Tag key={perm}>{perm}</Tag>
            ))}
          </Space>
        </Descriptions.Item>
      </Descriptions>
      <Space style={{ marginTop: 16 }}>
        <Button onClick={() => void handleCheck()} loading={checking}>
          {t('home.checkToken')}
        </Button>
      </Space>
      <p className="admin-hint">{t('home.hint')}</p>
    </Card>
  );
}
