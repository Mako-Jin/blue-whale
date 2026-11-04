// MES 子应用根组件：左侧菜单 + 路由，工作台包含工单 / 派工 / 执行 / 看板 / 主数据 / 追溯 / 质量
import { useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import type { MenuProps } from 'antd';
import List from './pages/List';
import MasterDataPage from './pages/MasterDataPage';
import DispatchPage from './pages/DispatchPage';
import ExecutionPage from './pages/ExecutionPage';
import BoardPage from './pages/BoardPage';
import TracePage from './pages/TracePage';
import QualityPage from './pages/QualityPage';
import FormModal from './pages/Form';
import type { MesWorkOrder } from './api/mes';

const { Header, Content, Sider } = Layout;

/** 菜单项：生产工单 / 调度派工 / 生产执行 / 生产看板 / 基础主数据 / 物料追溯 / 线边仓 / 车间质量 */
const menuItems: MenuProps['items'] = [
  { key: '/work-order', label: '生产工单' },
  { key: '/dispatch', label: '调度派工' },
  { key: '/execution', label: '生产执行' },
  { key: '/board', label: '生产看板' },
  { key: '/master-data', label: '基础主数据' },
  { key: '/trace', label: '物料追溯' },
  { key: '/line-side', label: '线边仓' },
  { key: '/quality/plan', label: '检验计划' },
  { key: '/quality/first-article', label: '首件检验' },
  { key: '/quality/ipqc', label: 'IPQC 巡检' },
  { key: '/quality/ncr', label: '不良品管理' },
  { key: '/quality/disposition', label: '处置管理' },
  { key: '/quality/rework', label: '返工管理' },
  { key: '/quality/spc', label: 'SPC 控制' },
  { key: '/quality/board', label: '质量看板' },
];

/** 菜单路由前缀（用于定位当前菜单） */
const MENU_KEYS = [
  '/work-order',
  '/dispatch',
  '/execution',
  '/board',
  '/master-data',
  '/trace',
  '/line-side',
  '/quality/plan',
  '/quality/first-article',
  '/quality/ipqc',
  '/quality/ncr',
  '/quality/disposition',
  '/quality/rework',
  '/quality/spc',
  '/quality/board',
];

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
            <Route path="/trace" element={<TracePage initialTab="barcode" />} />
            <Route path="/trace/barcode" element={<TracePage initialTab="barcode" />} />
            <Route path="/trace/batch" element={<TracePage initialTab="batch" />} />
            <Route path="/trace/query" element={<TracePage initialTab="query" />} />
            <Route path="/trace/scan" element={<TracePage initialTab="scan" />} />
            <Route path="/trace/scan-rule" element={<TracePage initialTab="scan" />} />
            <Route path="/line-side" element={<TracePage initialTab="stock" />} />
            <Route path="/line-side/stock" element={<TracePage initialTab="stock" />} />
            <Route path="/line-side/replenishment" element={<TracePage initialTab="replenish" />} />
            <Route path="/quality" element={<QualityPage initialTab="plan" />} />
            <Route path="/quality/plan" element={<QualityPage initialTab="plan" />} />
            <Route path="/quality/first-article" element={<QualityPage initialTab="first-article" />} />
            <Route path="/quality/ipqc" element={<QualityPage initialTab="ipqc" />} />
            <Route path="/quality/ncr" element={<QualityPage initialTab="ncr" />} />
            <Route path="/quality/disposition" element={<QualityPage initialTab="disposition" />} />
            <Route path="/quality/rework" element={<QualityPage initialTab="rework" />} />
            <Route path="/quality/spc" element={<QualityPage initialTab="spc" />} />
            <Route path="/quality/board" element={<QualityPage initialTab="board" />} />
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
