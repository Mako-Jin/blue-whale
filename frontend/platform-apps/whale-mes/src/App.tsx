// MES 子应用根组件：左侧菜单 + 路由，工作台包含工单 / 派工 / 执行 / 看板 / 主数据
import { useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import type { MenuProps } from 'antd';
import List from './pages/List';
import MasterDataPage from './pages/MasterDataPage';
import DispatchPage from './pages/DispatchPage';
import ExecutionPage from './pages/ExecutionPage';
import BoardPage from './pages/BoardPage';
import FormModal from './pages/Form';
import type { MesWorkOrder } from './api/mes';

const { Header, Content, Sider } = Layout;

/** 菜单项：生产工单 / 调度派工 / 生产执行 / 生产看板 / 基础主数据 */
const menuItems: MenuProps['items'] = [
  { key: '/work-order', label: '生产工单' },
  { key: '/dispatch', label: '调度派工' },
  { key: '/execution', label: '生产执行' },
  { key: '/board', label: '生产看板' },
  { key: '/master-data', label: '基础主数据' },
];

/** 菜单路由前缀（用于定位当前菜单） */
const MENU_KEYS = ['/work-order', '/dispatch', '/execution', '/board', '/master-data'];

/** 应用外壳：菜单、内容区与工单表单弹窗 */
function MesShell() {
  const location = useLocation();
  const navigate = useNavigate();
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<MesWorkOrder | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const selectedKey = MENU_KEYS.find((key) => location.pathname.startsWith(key)) ?? '/work-order';

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    navigate(key);
  };

  const handleEdit = (item: MesWorkOrder) => {
    setEditing(item);
    setFormOpen(true);
  };

  const handleCreate = () => {
    setEditing(null);
    setFormOpen(true);
  };

  const handleSaved = () => {
    setFormOpen(false);
    setEditing(null);
    setRefreshKey((key) => key + 1);
  };

  return (
    <Layout style={{ minHeight: '100%' }}>
      <Header style={{ display: 'flex', alignItems: 'center', color: '#fff' }}>
        <span style={{ fontSize: 16, fontWeight: 600 }}>Whale MES - MES 子应用</span>
      </Header>
      <Layout>
        <Sider width={200} theme="light">
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            items={menuItems}
            onClick={handleMenuClick}
          />
        </Sider>
        <Content style={{ padding: 16 }}>
          <Routes>
            <Route
              path="/work-order"
              element={
                <List refreshKey={refreshKey} onCreate={handleCreate} onEdit={handleEdit} />
              }
            />
            <Route path="/master-data" element={<MasterDataPage />} />
            <Route path="/dispatch" element={<DispatchPage />} />
            <Route path="/execution" element={<ExecutionPage />} />
            <Route path="/board" element={<BoardPage />} />
            <Route path="*" element={<Navigate to="/work-order" replace />} />
          </Routes>
        </Content>
      </Layout>
      <FormModal
        open={formOpen}
        initial={editing}
        onCancel={() => setFormOpen(false)}
        onSaved={handleSaved}
      />
    </Layout>
  );
}

export default function App() {
  return (
    <BrowserRouter basename="/mes">
      <MesShell />
    </BrowserRouter>
  );
}
