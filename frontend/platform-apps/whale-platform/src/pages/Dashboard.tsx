// 工作台 - 基座首页（路由 / 的 index 页面）

import { Card, Descriptions, Typography } from 'antd';
import { getCurrentUser } from '../api/auth';

export default function Dashboard() {
  const user = getCurrentUser();

  return (
    <Card>
      <Typography.Title level={3} style={{ marginTop: 0 }}>
        工作台
      </Typography.Title>
      <Typography.Paragraph>
        欢迎回来，{user?.nickname ?? '用户'}！这里是 BlueWhale 微服务平台基座首页。
      </Typography.Paragraph>
      <Typography.Paragraph type="secondary">
        通过左侧菜单访问平台功能；门户 / ERP / OA 子应用由 qiankun 按路由自动挂载。
      </Typography.Paragraph>
      <Descriptions size="small" column={2} bordered>
        <Descriptions.Item label="当前用户">{user?.username ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="角色">{user?.roles.join('、') ?? '-'}</Descriptions.Item>
      </Descriptions>
    </Card>
  );
}
