// OA 子应用根组件：人事模块多路由菜单，按权限码过滤菜单可见性
import { Layout, Menu } from 'antd';
import type { MenuProps } from 'antd';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import LeaveCenter from './pages/hr/leave';
import LeaveTypeCenter from './pages/hr/leave-type';
import LeaveBalanceCenter from './pages/hr/leave-balance';
import ProfileCenter from './pages/hr/profile';
import CalendarCenter from './pages/hr/calendar';
import { hasPermission } from './utils/permission';

const { Header, Content, Sider } = Layout;

/** 全部菜单项：key 为 basename 之后的路径，permission 为列表权限码 */
const MENU_ITEMS = [
  { key: '/hr/leave', label: '请假管理', permission: 'sys:oa:leave:list' },
  { key: '/hr/leave-type', label: '假期类型', permission: 'sys:oa:hr:leave-type:list' },
  { key: '/hr/leave-balance', label: '额度台账', permission: 'sys:oa:hr:balance:list' },
  { key: '/hr/profile', label: '员工档案', permission: 'sys:oa:hr:profile:list' },
  { key: '/hr/calendar', label: '工作日历', permission: 'sys:oa:hr:calendar:list' },
];

/** 按权限过滤后的菜单 */
const menuItems: MenuProps['items'] = MENU_ITEMS.filter(
  (item) => !item.permission || hasPermission(item.permission),
).map(({ key, label }) => ({ key, label }));

function OaLayout() {
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <Layout style={{ minHeight: '100%' }}>
      <Header style={{ display: 'flex', alignItems: 'center', color: '#fff' }}>
        <span style={{ fontSize: 16, fontWeight: 600 }}>Whale OA - 人事管理</span>
      </Header>
      <Layout>
        <Sider width={200} theme="light">
          <Menu
            mode="inline"
            selectedKeys={[location.pathname]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
          />
        </Sider>
        <Content style={{ padding: 16 }}>
          <Routes>
            <Route path="/hr/leave" element={<LeaveCenter />} />
            <Route path="/hr/leave-type" element={<LeaveTypeCenter />} />
            <Route path="/hr/leave-balance" element={<LeaveBalanceCenter />} />
            <Route path="/hr/profile" element={<ProfileCenter />} />
            <Route path="/hr/calendar" element={<CalendarCenter />} />
            <Route path="*" element={<Navigate to="/hr/leave" replace />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}

export default function App() {
  return (
    <BrowserRouter basename="/oa">
      <OaLayout />
    </BrowserRouter>
  );
}
