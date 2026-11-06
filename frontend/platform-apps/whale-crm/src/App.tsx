// CRM 子应用根组件：客户列表 / 客户详情 / 公海客户，菜单按权限码过滤
import { Layout, Menu, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import CustomerList from './pages/customer';
import CustomerDetail from './pages/customer/detail';
import PoolList from './pages/pool';
import { hasPermission } from './utils/permission';

const { Header, Content, Sider } = Layout;

/** 全部菜单项：permission 为空表示登录即可见 */
const MENU_ITEMS = [
  { key: '/customer', label: '客户管理', permission: 'sys:crm:customer:list' },
  { key: '/pool', label: '公海客户', permission: 'sys:crm:pool:view' },
];

/** 按权限过滤后的菜单 */
const menuItems: MenuProps['items'] = MENU_ITEMS.filter(
  (item) => !item.permission || hasPermission(item.permission),
).map(({ key, label }) => ({ key, label }));

/**
 * 解析当前选中菜单：详情页归属于客户管理。
 *
 * @param pathname 当前路径
 * @returns 选中的菜单 key
 */
function resolveSelectedKey(pathname: string): string {
  if (pathname.startsWith('/customer')) {
    return '/customer';
  }
  if (pathname.startsWith('/pool')) {
    return '/pool';
  }
  return pathname;
}

function CrmLayout() {
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <Layout style={{ minHeight: '100%' }}>
      <Header style={{ display: 'flex', alignItems: 'center', color: '#fff' }}>
        <span style={{ fontSize: 16, fontWeight: 600 }}>Whale CRM - 客户关系管理</span>
        <Typography.Text style={{ color: 'rgba(255,255,255,0.65)', marginLeft: 12 }}>
          客户主数据 / 公海运营
        </Typography.Text>
      </Header>
      <Layout>
        <Sider width={200} theme="light">
          <Menu
            mode="inline"
            selectedKeys={[resolveSelectedKey(location.pathname)]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
          />
        </Sider>
        <Content style={{ padding: 16, background: '#f5f6f8' }}>
          <Routes>
            <Route path="/customer" element={<CustomerList />} />
            <Route path="/customer/:id" element={<CustomerDetail />} />
            <Route path="/pool" element={<PoolList />} />
            <Route path="*" element={<Navigate to="/customer" replace />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}

export default function App() {
  return (
    <BrowserRouter basename="/crm">
      <CrmLayout />
    </BrowserRouter>
  );
}
