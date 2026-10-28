// 门户子应用根组件：antd Layout（Header + Sider 菜单）+ 路由
// 「消息中心」菜单带未读角标：30s 轮询 + 标记已读事件即时刷新；未登录/接口异常时降级为不显示角标
import { useCallback, useEffect, useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { Badge, Layout, Menu } from 'antd';
import type { MenuProps } from 'antd';
import Dashboard from './pages/Dashboard';
import Todo from './pages/Todo';
import Notice from './pages/Notice';
import Inbox, { INAPP_READ_EVENT } from './pages/Inbox';
import { getInAppUnreadCount } from './api/inapp';

const { Header, Content, Sider } = Layout;

/** 导航路径（同时用作菜单 key） */
const NAV_KEYS = ['/', '/todo', '/notice', '/inbox'] as const;

/** 未读数轮询间隔（毫秒） */
const UNREAD_POLL_INTERVAL = 30_000;

/** 布局壳：Sider 菜单随路由高亮，点击即导航 */
function Shell() {
  const navigate = useNavigate();
  const location = useLocation();
  const [unread, setUnread] = useState(0);
  const selectedKey = (NAV_KEYS as readonly string[]).includes(location.pathname)
    ? location.pathname
    : '/';

  const refreshUnread = useCallback(() => {
    getInAppUnreadCount()
      .then(setUnread)
      .catch(() => setUnread(0));
  }, []);

  useEffect(() => {
    refreshUnread();
    const timer = window.setInterval(refreshUnread, UNREAD_POLL_INTERVAL);
    const onRead = () => refreshUnread();
    window.addEventListener(INAPP_READ_EVENT, onRead);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener(INAPP_READ_EVENT, onRead);
    };
  }, [refreshUnread]);

  /** 菜单项：工作台 / 我的待办 / 消息通知 / 消息中心（未读角标） */
  const menuItems: MenuProps['items'] = [
    { key: '/', label: '工作台' },
    { key: '/todo', label: '我的待办' },
    { key: '/notice', label: '消息通知' },
    {
      key: '/inbox',
      label: (
        <Badge count={unread} size="small" offset={[8, 0]}>
          消息中心
        </Badge>
      ),
    },
  ];

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
            <Route path="/inbox" element={<Inbox />} />
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
