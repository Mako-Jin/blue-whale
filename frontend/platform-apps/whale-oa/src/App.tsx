// OA 子应用根组件：审批中心 + 人事模块多路由菜单，按权限码过滤菜单可见性
import { Layout, Menu } from 'antd';
import type { MenuProps } from 'antd';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import ApprovalCenter from './pages/approval';
import ApprovalDelegate from './pages/approval/delegate';
import ApprovalMonitor from './pages/approval/monitor';
import LeaveCenter from './pages/hr/leave';
import LeaveTypeCenter from './pages/hr/leave-type';
import LeaveBalanceCenter from './pages/hr/leave-balance';
import ProfileCenter from './pages/hr/profile';
import CalendarCenter from './pages/hr/calendar';
import { hasPermission } from './utils/permission';

const { Header, Content, Sider } = Layout;

/** 全部菜单项：key 为 basename 之后的路径，permission 为列表权限码 */
const MENU_ITEMS = [
  { key: '/approval', label: '审批中心', permission: 'sys:oa:approval:list' },
  { key: '/approval/delegate', label: '代理设置', permission: 'sys:oa:approval:delegate' },
  { key: '/approval/monitor', label: '流程监控', permission: 'sys:oa:approval:monitor' },
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

/** 默认落地页：首个可见菜单，避免无审批权限的用户落到 403 页面 */
const DEFAULT_PATH = MENU_ITEMS.find(
  (item) => !item.permission || hasPermission(item.permission),
)?.key ?? '/approval';

function OaLayout() {
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <Layout style={{ minHeight: '100%' }}>
      <Header style={{ display: 'flex', alignItems: 'center', color: '#fff' }}>
        <span style={{ fontSize: 16, fontWeight: 600 }}>Whale OA - 办公协同</span>
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
            <Route path="/approval" element={<ApprovalCenter />} />
            <Route path="/approval/delegate" element={<ApprovalDelegate />} />
            <Route path="/approval/monitor" element={<ApprovalMonitor />} />
            <Route path="/hr/leave" element={<LeaveCenter />} />
            <Route path="/hr/leave-type" element={<LeaveTypeCenter />} />
            <Route path="/hr/leave-balance" element={<LeaveBalanceCenter />} />
            <Route path="/hr/profile" element={<ProfileCenter />} />
            <Route path="/hr/calendar" element={<CalendarCenter />} />
            <Route path="*" element={<Navigate to={DEFAULT_PATH} replace />} />
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
