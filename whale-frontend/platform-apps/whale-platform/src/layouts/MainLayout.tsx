// 主布局 - Header（logo/面包屑/主题切换/用户下拉）+ Sider（可折叠菜单）+ 多标签页 + Content

import { useEffect, useState, type ReactNode } from 'react';
import {
  AppstoreOutlined,
  BankOutlined,
  CloudOutlined,
  CustomerServiceOutlined,
  DeploymentUnitOutlined,
  EyeOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  FlagOutlined,
  HomeOutlined,
  LinkOutlined,
  LogoutOutlined,
  MailOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MessageOutlined,
  ProfileOutlined,
  ProjectOutlined,
  ReadOutlined,
  SafetyOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { App, Avatar, Badge, Breadcrumb, Button, Dropdown, Layout, Menu, Select, Space, Tabs } from 'antd';
import type { MenuProps } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  saveUiLang,
  type UiLang,
} from '@bluewhale/whale-auth';
import { logout, useCurrentUser } from '../api/auth';
import { useImUnread } from '../hooks/useImUnread';
import { useUiLang } from '../hooks/useUiLang';
import {
  getBreadcrumbItems,
  getRouteTitle,
  hasMenuEntry,
  filterMenuByPermissions,
  menuConfig,
  localizedMenuLabel,
} from '../router/menu';
import { ThemeSwitcher } from '@bluewhale/whale-common-theme';

const { Header, Sider, Content } = Layout;

interface TabItem {
  key: string;
  label: string;
  closable: boolean;
}

/** 首页标签与少量 Header 文案的中英对照（菜单主体走 menuLabelEn） */
const BASE_TEXT = {
  homeTab: { 'zh-CN': '工作台', 'en-US': 'Workspace' },
  loggedOut: { 'zh-CN': '已退出登录', 'en-US': 'Signed out' },
  notLoggedIn: { 'zh-CN': '未登录', 'en-US': 'Not signed in' },
  logout: { 'zh-CN': '退出登录', 'en-US': 'Sign out' },
  collapseMenu: { 'zh-CN': '折叠菜单', 'en-US': 'Toggle menu' },
  logoTitle: { 'zh-CN': 'BlueWhale 微服务平台', 'en-US': 'BlueWhale Microservices' },
  user: { 'zh-CN': '用户', 'en-US': 'User' },
  languageLabel: { 'zh-CN': '语言', 'en-US': 'Language' },
} as const;

const MENU_ICONS: Record<string, ReactNode> = {
  '/': <HomeOutlined />,
  '/system': <SettingOutlined />,
  '/system/users': <TeamOutlined />,
  '/system/roles': <SafetyOutlined />,
  '/system/menus': <AppstoreOutlined />,
  '/portal': <CloudOutlined />,
  '/erp': <ProfileOutlined />,
  '/oa': <FileTextOutlined />,
  '/mes': <DeploymentUnitOutlined />,
  '/pdm': <ProfileOutlined />,
  '/im': <MessageOutlined />,
  '/pm': <ProjectOutlined />,
  '/contract': <FileTextOutlined />,
  '/doc': <FileSearchOutlined />,
  '/music': <CustomerServiceOutlined />,
  '/mail': <MailOutlined />,
  '/lms': <ReadOutlined />,
  '/agent': <ReadOutlined />,
  '/party': <FlagOutlined />,
  '/scene': <BankOutlined />,
  '/scene/navigation': <BankOutlined />,
  '/scene/manage': <AppstoreOutlined />,
  '/scene/bindings': <LinkOutlined />,
  '/scene/visibility': <EyeOutlined />,
};

export default function MainLayout() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const language = useUiLang();
  const tx = (key: keyof typeof BASE_TEXT) => BASE_TEXT[key][language];
  const [collapsed, setCollapsed] = useState(false);
  const [tabs, setTabs] = useState<TabItem[]>([
    { key: '/', label: BASE_TEXT.homeTab['zh-CN'], closable: false },
  ]);
  const user = useCurrentUser();
  const imUnread = useImUnread(user?.permissions?.includes('sys:im:chat') ?? false);

  const activeKey = location.pathname;

  // 访问过的路由自动记录为标签页
  useEffect(() => {
    setTabs((prev) => {
      if (prev.some((tab) => tab.key === activeKey)) return prev;
      // 子应用内部跳转（如 /agent/chat）不新开标签：入口标签（/agent）已存在时直接复用，
      // 否则会出现两个同名标签（Wave 6.18 实机验证发现）
      if (!hasMenuEntry(activeKey)) {
        const segment = `/${activeKey.split('/')[1] ?? ''}`;
        if (prev.some((tab) => tab.key === segment)) return prev;
      }
      return [
        ...prev,
        { key: activeKey, label: getRouteTitle(activeKey, language), closable: activeKey !== '/' },
      ];
    });
    // language 仅用于新增标签时的标题；已有标签在渲染时按当前语言重算，无需重复入 state
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
    message.success(tx('loggedOut'));
    navigate('/login', { replace: true });
  };

  const siderMenuItems: MenuProps['items'] = filterMenuByPermissions(
    menuConfig,
    user?.permissions ?? [],
  ).map((item) => ({
    key: item.key,
    icon: MENU_ICONS[item.key],
    label:
      item.key === '/im' ? (
        <Badge count={imUnread} size="small" offset={[10, 0]}>
          {localizedMenuLabel(item, language)}
        </Badge>
      ) : (
        localizedMenuLabel(item, language)
      ),
    children: item.children?.map((child) => ({
      key: child.key,
      icon: MENU_ICONS[child.key],
      label: localizedMenuLabel(child, language),
    })),
  }));

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'username',
      icon: <UserOutlined />,
      label: user?.username ?? tx('notLoggedIn'),
      disabled: true,
    },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: tx('logout'), danger: true },
  ];

  // 已打开标签在语言切换后按当前语言重算标题（首页标题也随语言变）
  const localizedTabs: TabItem[] = tabs.map((tab) => ({
    ...tab,
    label:
      tab.key === '/'
        ? tx('homeTab')
        : getRouteTitle(tab.key, language) === tab.key
          ? tab.label
          : getRouteTitle(tab.key, language),
  }));

  return (
    // 固定视口高度（而非 minHeight）：子应用容器用 height:100% 撑满，父级必须高度确定，
    // 否则 qiankun 子应用（如知识助手）只按自身内容高度渲染，下方留大片空白。
    // 内容区自身 overflow:auto，长页面仍在 .platform-content 内部滚动。
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={220}
        theme="dark"
        className="platform-sider"
      >
        <Menu
          theme="dark"
          mode="inline"
          items={siderMenuItems}
          selectedKeys={[activeKey]}
          defaultOpenKeys={['/system', '/scene']}
          onClick={({ key }) => navigate(key)}
          style={{ borderRight: 0 }}
        />
      </Sider>
      <Layout>
        <Header className="platform-header">
          <Button
            type="text"
            aria-label={tx('collapseMenu')}
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
          />
          <div className="platform-logo">
            <span className="platform-logo-mark">B</span>
            <span className="platform-logo-title">{tx('logoTitle')}</span>
          </div>
          <Breadcrumb
            className="platform-breadcrumb"
            items={getBreadcrumbItems(activeKey, language).map((title) => ({ title }))}
          />
          <Space className="platform-header-actions">
            <ThemeSwitcher compact />
            <Select<UiLang>
              aria-label={tx('languageLabel')}
              size="small"
              value={language}
              style={{ width: 96 }}
              onChange={(value) => saveUiLang(value)}
              options={[
                { value: 'zh-CN', label: '中文' },
                { value: 'en-US', label: 'English' },
              ]}
            />
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
                <span>{user?.nickname ?? tx('user')}</span>
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
            items={localizedTabs}
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
