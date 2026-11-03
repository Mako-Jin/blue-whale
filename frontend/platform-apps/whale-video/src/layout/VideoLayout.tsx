// 视频子应用整体布局：顶部导航（首页 / 短视频 / 视频 / 电影 / 上传 / 我的）+ 内容区

import { Layout, Menu, Typography } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const { Header, Content } = Layout;

/** 导航项 */
const NAV_ITEMS = [
  { key: '/home', label: '首页' },
  { key: '/shorts', label: '短视频' },
  { key: '/videos', label: '视频' },
  { key: '/movies', label: '电影' },
  { key: '/upload', label: '上传' },
  { key: '/mine', label: '我的' },
];

/** 当前路由对应的导航项（详情页归属其列表） */
function resolveActiveKey(pathname: string): string {
  if (pathname.startsWith('/shorts')) {
    return '/shorts';
  }
  if (pathname.startsWith('/videos') || pathname.startsWith('/series')) {
    return '/videos';
  }
  if (pathname.startsWith('/movies') || pathname.startsWith('/collections')) {
    return '/movies';
  }
  if (pathname.startsWith('/upload')) {
    return '/upload';
  }
  if (pathname.startsWith('/mine')) {
    return '/mine';
  }
  return '/home';
}

export default function VideoLayout() {
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
          视频中心
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
