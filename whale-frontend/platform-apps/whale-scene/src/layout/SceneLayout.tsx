// 场景子应用布局：顶部导航（城池导航 / 3D 视图 / 场景管理 / 入口绑定 / 可见性）+ 内容区
//
// 2D 为默认视图；3D 预览页按需加载 three.js，不影响 2D 首屏。

import { Layout, Menu, Tag, Typography } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const { Header, Content } = Layout;

/** 导航项（与基座菜单 /scene/manage、/scene/bindings、/scene/visibility 对齐） */
const NAV_ITEMS = [
  { key: '/navigation', label: '城池导航' },
  { key: '/3d', label: '3D 视图' },
  { key: '/manage', label: '场景管理' },
  { key: '/bindings', label: '入口绑定' },
  { key: '/visibility', label: '可见性' },
];

/** 当前路由对应的导航项（编辑页按 ?tab= 归属入口绑定 / 可见性） */
function resolveActiveKey(pathname: string, search: string): string {
  if (pathname.startsWith('/3d')) {
    return '/3d';
  }
  if (pathname.startsWith('/manage')) {
    const tab = new URLSearchParams(search).get('tab');
    if (tab === 'bindings') {
      return '/bindings';
    }
    if (tab === 'visibility') {
      return '/visibility';
    }
    return '/manage';
  }
  if (pathname.startsWith('/bindings')) {
    return '/bindings';
  }
  if (pathname.startsWith('/visibility')) {
    return '/visibility';
  }
  return '/navigation';
}

export default function SceneLayout() {
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
          城池导航
        </Typography.Title>
        <Menu
          mode="horizontal"
          selectedKeys={[resolveActiveKey(location.pathname, location.search)]}
          items={NAV_ITEMS}
          onClick={({ key }) => navigate(key)}
          style={{ flex: 1, minWidth: 0, borderBottom: 'none' }}
        />
        <Tag
          color={location.pathname.startsWith('/3d') ? 'green' : 'blue'}
          style={{ marginInlineEnd: 0 }}
        >
          {location.pathname.startsWith('/3d') ? '3D 模式' : '2D 模式'}
        </Tag>
      </Header>
      <Content style={{ padding: 24 }}>
        <Outlet />
      </Content>
    </Layout>
  );
}
