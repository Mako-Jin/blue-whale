// 音乐子应用整体布局：顶部导航（曲库 / 上传作品 / 我的作品）+ 底部全局播放条

import { Layout, Menu, Typography } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import PlayerBar from '../player/PlayerBar';

const { Header, Content } = Layout;

/** 导航项 */
const NAV_ITEMS = [
  { key: '/library', label: '曲库' },
  { key: '/upload', label: '上传作品' },
  { key: '/mine', label: '我的作品' },
];

/** 当前路由对应的导航项（歌曲详情归入曲库） */
function resolveActiveKey(pathname: string): string {
  if (pathname.startsWith('/upload')) {
    return '/upload';
  }
  if (pathname.startsWith('/mine')) {
    return '/mine';
  }
  return '/library';
}

export default function MusicLayout() {
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
          音乐中心
        </Typography.Title>
        <Menu
          mode="horizontal"
          selectedKeys={[resolveActiveKey(location.pathname)]}
          items={NAV_ITEMS}
          onClick={({ key }) => navigate(key)}
          style={{ flex: 1, minWidth: 0, borderBottom: 'none' }}
        />
      </Header>
      <Content style={{ padding: 24, paddingBottom: 96 }}>
        <Outlet />
      </Content>
      <PlayerBar />
    </Layout>
  );
}
