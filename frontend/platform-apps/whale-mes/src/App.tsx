// MES 子应用根组件：左侧菜单 + 路由，工作台包含工单 / 派工 / 执行 / 看板 / 主数据 / 追溯 / 质量 / 设备 / 采集
import { useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import type { MenuProps } from 'antd';
import List from './pages/List';
import MasterDataPage from './pages/MasterDataPage';
import DispatchPage from './pages/DispatchPage';
import SchedulingPage from './pages/SchedulingPage';
import ExecutionPage from './pages/ExecutionPage';
import BoardPage from './pages/BoardPage';
import TracePage from './pages/TracePage';
import QualityPage from './pages/QualityPage';
import EquipmentPage from './pages/EquipmentPage';
import CollectPage from './pages/CollectPage';
import WorkforcePage from './pages/WorkforcePage';
import DashboardPage from './pages/DashboardPage';
import FormModal from './pages/Form';
import type { MesWorkOrder } from './api/mes';

const { Header, Content, Sider } = Layout;

/** 菜单项：生产工单 / 调度派工 / 生产执行 / 生产看板 / 基础主数据 / 物料追溯 / 线边仓 / 车间质量 / 设备 / 采集 */
const menuItems: MenuProps['items'] = [
  { key: '/dashboard', label: '综合看板' },
  { key: '/work-order', label: '生产工单' },
  { key: '/dispatch', label: '调度派工' },
  { key: '/dispatch/schedule', label: '生产排程' },
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
  { key: '/equipment/ledger', label: '设备台账' },
  { key: '/equipment/status', label: '状态与停机' },
  { key: '/equipment/maintenance', label: '保养管理' },
  { key: '/equipment/repair', label: '维修管理' },
  { key: '/equipment/spare', label: '备件管理' },
  { key: '/equipment/oee', label: 'OEE 分析' },
  { key: '/collect/device', label: '采集设备' },
  { key: '/collect/point', label: '采集点' },
  { key: '/collect/sample', label: '样本明细' },
  { key: '/collect/board', label: '采集看板' },
  { key: '/collect/archive', label: '归档策略' },
  { key: '/workforce/team', label: '班组成员' },
  { key: '/workforce/shift', label: '排班管理' },
  { key: '/workforce/handover', label: '交接班' },
  { key: '/workforce/work-hour', label: '工时管理' },
  { key: '/workforce/performance', label: '绩效统计' },
  { key: '/workforce/skill', label: '技能矩阵' },
];

/** 菜单路由前缀（用于定位当前菜单） */
const MENU_KEYS = [
  '/dashboard',
  '/work-order',
  '/dispatch/schedule',
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
  '/equipment/ledger',
  '/equipment/status',
  '/equipment/maintenance',
  '/equipment/repair',
  '/equipment/spare',
  '/equipment/oee',
  '/collect/device',
  '/collect/point',
  '/collect/sample',
  '/collect/board',
  '/collect/archive',
  '/workforce/team',
  '/workforce/shift',
  '/workforce/handover',
  '/workforce/work-hour',
  '/workforce/performance',
  '/workforce/skill',
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
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route
              path="/work-order"
              element={
                <List refreshKey={refreshKey} onCreate={handleCreate} onEdit={handleEdit} />
              }
            />
            <Route path="/master-data" element={<MasterDataPage />} />
            <Route path="/dispatch" element={<DispatchPage />} />
            <Route path="/dispatch/schedule" element={<SchedulingPage />} />
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
            <Route path="/equipment" element={<EquipmentPage initialTab="ledger" />} />
            <Route path="/equipment/ledger" element={<EquipmentPage initialTab="ledger" />} />
            <Route path="/equipment/status" element={<EquipmentPage initialTab="status" />} />
            <Route path="/equipment/maintenance" element={<EquipmentPage initialTab="maintenance" />} />
            <Route path="/equipment/repair" element={<EquipmentPage initialTab="repair" />} />
            <Route path="/equipment/spare" element={<EquipmentPage initialTab="spare" />} />
            <Route path="/equipment/oee" element={<EquipmentPage initialTab="oee" />} />
            <Route path="/collect" element={<CollectPage initialTab="device" />} />
            <Route path="/collect/device" element={<CollectPage initialTab="device" />} />
            <Route path="/collect/point" element={<CollectPage initialTab="point" />} />
            <Route path="/collect/sample" element={<CollectPage initialTab="sample" />} />
            <Route path="/collect/board" element={<CollectPage initialTab="board" />} />
            <Route path="/collect/archive" element={<CollectPage initialTab="archive" />} />
            <Route path="/workforce" element={<WorkforcePage initialTab="team" />} />
            <Route path="/workforce/team" element={<WorkforcePage initialTab="team" />} />
            <Route path="/workforce/shift" element={<WorkforcePage initialTab="shift" />} />
            <Route path="/workforce/handover" element={<WorkforcePage initialTab="handover" />} />
            <Route path="/workforce/work-hour" element={<WorkforcePage initialTab="work-hour" />} />
            <Route path="/workforce/performance" element={<WorkforcePage initialTab="performance" />} />
            <Route path="/workforce/skill" element={<WorkforcePage initialTab="skill" />} />
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
