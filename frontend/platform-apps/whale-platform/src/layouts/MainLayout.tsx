// 主布局 - Header（logo/面包屑/主题切换/用户下拉）+ Sider（可折叠菜单）+ 多标签页 + Content

import { useEffect, useState, type ReactNode } from 'react';
import {
  AppstoreOutlined,
  CloudOutlined,
  FileTextOutlined,
  HomeOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ProfileOutlined,
  SafetyOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { App, Avatar, Breadcrumb, Button, Dropdown, Layout, Menu, Space, Tabs } from 'antd';
import type { MenuProps } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { logout, useCurrentUser } from '../api/auth';
import { getBreadcrumbItems, getRouteTitle, menuConfig } from '../router/menu';
import { ThemeSwitcher } from '@bluewhale/whale-common-theme';

const { Header, Sider, Content } = Layout;

interface TabItem {
  key: string;
  label: string;
  closable: boolean;
}

const HOME_TAB: TabItem = { key: '/', label: '工作台', closable: false };

const MENU_ICONS: Record<string, ReactNode> = {
  '/': <HomeOutlined />,
  '/system': <SettingOutlined />,
  '/system/users': <TeamOutlined />,
  '/system/roles': <SafetyOutlined />,
  '/system/menus': <AppstoreOutlined />,
  '/portal': <CloudOutlined />,
  '/erp': <ProfileOutlined />,
  '/oa': <FileTextOutlined />,
};

export default function MainLayout() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const [tabs, setTabs] = useState<TabItem[]>([HOME_TAB]);
  const user = useCurrentUser();

  const activeKey = location.pathname;

  // 访问过的路由自动记录为标签页
  useEffect(() => {
    setTabs((prev) => {
      if (prev.some((tab) => tab.key === activeKey)) return prev;
      return [
        ...prev,
        { key: activeKey, label: getRouteTitle(activeKey), closable: activeKey !== '/' },
      ];
    });
  }, [activeKey]);

  const handleTabChange = (key: string) => {
    if (key !== activeKey) navigate(key);
  };

  // 关闭标签；关闭的是当前标签时激活相邻标签
  const handleTabClose = (targetKey: string) => {
    const index = tabs.findIndex((tab) => tab.key === targetKey);
    const next = tabs.filter((tab) => tab.key !== targetKey);
    setTabs(next);
    if (targetKey === activeKey && next.length > 0) {
      const fallback = next[Math.min(index, next.length - 1)];
      navigate(fallback.key);
    }
  };

  const handleLogout = async () => {
    await logout();
    message.success('已退出登录');
    navigate('/login', { replace: true });
  };

  const siderMenuItems: MenuProps['items'] = menuConfig.map((item) => ({
    key: item.key,
    icon: MENU_ICONS[item.key],
    label: item.label,
    children: item.children?.map((child) => ({
      key: child.key,
      icon: MENU_ICONS[child.key],
      label: child.label,
    })),
  }));

  const userMenuItems: MenuProps['items'] = [
    { key: 'username', icon: <UserOutlined />, label: user?.username ?? '未登录', disabled: true },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider trigger={null} collapsible collapsed={collapsed} width={220} theme="dark">
        <Menu
          theme="dark"
          mode="inline"
          items={siderMenuItems}
          selectedKeys={[activeKey]}
          defaultOpenKeys={['/system']}
          onClick={({ key }) => navigate(key)}
          style={{ borderRight: 0 }}
        />
      </Sider>
      <Layout>
        <Header className="platform-header">
          <Button
            type="text"
            aria-label="折叠菜单"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
          />
          <div className="platform-logo">
            <span className="platform-logo-mark">B</span>
            <span className="platform-logo-title">BlueWhale 微服务平台</span>
          </div>
          <Breadcrumb
            className="platform-breadcrumb"
            items={getBreadcrumbItems(activeKey).map((title) => ({ title }))}
          />
          <Space className="platform-header-actions">
            <ThemeSwitcher compact />
            <Dropdown
              menu={{
                items: userMenuItems,
                onClick: ({ key }) => {
                  if (key === 'logout') handleLogout();
                },
              }}
            >
              <Space className="platform-user">
                <Avatar size="small" icon={<UserOutlined />} />
                <span>{user?.nickname ?? '用户'}</span>
              </Space>
            </Dropdown>
          </Space>
        </Header>
        <div className="platform-tabs">
          <Tabs
            type="editable-card"
            hideAdd
            size="small"
            activeKey={activeKey}
            items={tabs}
            onChange={handleTabChange}
            onEdit={(targetKey, action) => {
              if (action === 'remove') handleTabClose(String(targetKey));
            }}
          />
        </div>
        <Content className="platform-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
