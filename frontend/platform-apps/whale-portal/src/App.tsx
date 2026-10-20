// 门户子应用根组件：antd Layout（Header + Sider 菜单）+ 路由
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import type { MenuProps } from 'antd';
import Dashboard from './pages/Dashboard';
import Todo from './pages/Todo';
import Notice from './pages/Notice';

const { Header, Content, Sider } = Layout;

/** 导航路径（同时用作菜单 key） */
const NAV_KEYS = ['/', '/todo', '/notice'] as const;

/** 菜单项：工作台 / 我的待办 / 消息通知 */
const menuItems: MenuProps['items'] = [
  { key: '/', label: '工作台' },
  { key: '/todo', label: '我的待办' },
  { key: '/notice', label: '消息通知' },
];

/** 布局壳：Sider 菜单随路由高亮，点击即导航 */
function Shell() {
  const navigate = useNavigate();
  const location = useLocation();
  const selectedKey = (NAV_KEYS as readonly string[]).includes(location.pathname)
    ? location.pathname
    : '/';

  return (
    <Layout style={{ minHeight: '100%' }}>
      <Header style={{ display: 'flex', alignItems: 'center', color: '#fff' }}>
        <span style={{ fontSize: 16, fontWeight: 600 }}>Whale Portal 企业门户</span>
      </Header>
      <Layout>
        <Sider width={200} theme="light">
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
          />
        </Sider>
        <Content style={{ padding: 16 }}>
          <Routes>
            {/* 工作台：basename 下 `/` 即工作台首页 */}
            <Route path="/" element={<Dashboard />} />
            <Route path="/todo" element={<Todo />} />
            <Route path="/notice" element={<Notice />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}

export default function App() {
  return (
    <BrowserRouter basename="/portal">
      <Shell />
    </BrowserRouter>
  );
}
