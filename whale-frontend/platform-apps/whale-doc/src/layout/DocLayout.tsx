// 文档子应用整体布局：顶部导航（空间 / 搜索 / 收藏）

import { Layout, Menu, Typography } from 'antd';
import { BookOutlined, FileTextOutlined, SearchOutlined, StarOutlined } from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const { Header, Content } = Layout;

/** 导航项 */
const NAV_ITEMS = [
  { key: '/spaces', label: '空间', icon: <BookOutlined /> },
  { key: '/templates', label: '模板', icon: <FileTextOutlined /> },
  { key: '/search', label: '搜索', icon: <SearchOutlined /> },
  { key: '/favorites', label: '收藏', icon: <StarOutlined /> },
];

/** 当前路由对应的导航项 */
function resolveActiveKey(pathname: string): string {
  if (pathname.startsWith('/search')) {
    return '/search';
  }
  if (pathname.startsWith('/favorites')) {
    return '/favorites';
  }
  if (pathname.startsWith('/templates')) {
    return '/templates';
  }
  return '/spaces';
}

export default function DocLayout() {
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <Layout style={{ minHeight: '100%', background: '#f5f6f8' }}>
      <Header
        style={{
          background: '#fff',
          borderBottom: '1px solid #f0f0f0',
          paddingInline: 24,
          display: 'flex',
          alignItems: 'center',
          gap: 24,
          height: 56,
        }}
      >
        <Typography.Title level={4} style={{ margin: 0, whiteSpace: 'nowrap' }}>
          文档中心
        </Typography.Title>
        <Menu
          mode="horizontal"
          selectedKeys={[resolveActiveKey(location.pathname)]}
          items={NAV_ITEMS}
          onClick={({ key }) => navigate(key)}
          style={{ flex: 1, minWidth: 0, borderBottom: 'none' }}
        />
      </Header>
      <Content style={{ padding: 24 }}>
        <Outlet />
      </Content>
    </Layout>
  );
}
