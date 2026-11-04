// MES 子应用根组件：左侧菜单 + 路由，工作台包含「生产工单」与「基础主数据」两页
import { useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import type { MenuProps } from 'antd';
import List from './pages/List';
import MasterDataPage from './pages/MasterDataPage';
import FormModal from './pages/Form';
import type { MesWorkOrder } from './api/mes';

const { Header, Content, Sider } = Layout;

/** 菜单项：生产工单 / 基础主数据 */
const menuItems: MenuProps['items'] = [
  { key: '/work-order', label: '生产工单' },
  { key: '/master-data', label: '基础主数据' },
];

/** 应用外壳：菜单、内容区与工单表单弹窗 */
function MesShell() {
  const location = useLocation();
  const navigate = useNavigate();
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<MesWorkOrder | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const selectedKey = location.pathname.startsWith('/master-data') ? '/master-data' : '/work-order';

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
