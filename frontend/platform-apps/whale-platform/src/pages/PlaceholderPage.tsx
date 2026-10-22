// 通用占位页面 - 菜单功能未实现前的统一占位

import { Card, Typography } from 'antd';

interface PlaceholderPageProps {
  title: string;
  description?: string;
}

export default function PlaceholderPage({ title, description }: PlaceholderPageProps) {
  return (
    <Card>
      <Typography.Title level={3} style={{ marginTop: 0 }}>
        {title}
      </Typography.Title>
      <Typography.Text type="secondary">
        {description ?? '占位页面 · 功能规划中，敬请期待'}
      </Typography.Text>
    </Card>
  );
}
