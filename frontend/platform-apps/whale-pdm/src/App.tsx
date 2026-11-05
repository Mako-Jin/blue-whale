// PDM 子应用根组件：图文档/产品结构/零件库/BOM/变更/工艺/协同，受权限码控制菜单可见性
import { Layout, Menu } from 'antd';
import type { MenuProps } from 'antd';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import DocumentCenter from './pages/document';
import ProductCenter from './pages/ProductCenter';
import PartLibrary from './pages/part';
import BomLibrary from './pages/bom';
import ChangeCenter from './pages/change';
import ChangeOrderCenter from './pages/change/order';
import ChangeNoticeCenter from './pages/change/notice';
import ChangeTraceCenter from './pages/change/trace';
import ProcessRouteCenter from './pages/process';
import DeliverableCenter from './pages/collab/deliverable';
import ReviewCenter from './pages/collab/review';
import PreviewCenter from './pages/collab/preview';
import { hasPermission } from './utils/permission';

const { Header, Content, Sider } = Layout;

/** 全部菜单项：权限码为空表示登录即可见 */
const MENU_ITEMS = [
  { key: '/document', label: '图文档中心', permission: 'sys:pdm:doc:list' },
  { key: '/product', label: '产品结构', permission: 'sys:pdm:product:list' },
  { key: '/part', label: '零件库', permission: 'sys:pdm:part:list' },
  { key: '/bom', label: 'BOM 结构', permission: 'sys:pdm:bom:list' },
  { key: '/change', label: '变更管理', permission: 'sys:pdm:change:list' },
  { key: '/change/order', label: '变更单（ECO）', permission: 'sys:pdm:change:order:list' },
  { key: '/change/notice', label: '变更通知（ECN）', permission: 'sys:pdm:change:notice:list' },
  { key: '/change/trace', label: '变更追溯', permission: 'sys:pdm:change:trace:list' },
  { key: '/process/route', label: '工艺路线', permission: 'sys:pdm:process:list' },
  { key: '/collab/deliverable', label: '项目交付物', permission: 'sys:pdm:collab:deliverable:list' },
  { key: '/collab/review', label: '研发评审', permission: 'sys:pdm:collab:review:list' },
  { key: '/collab/preview', label: '在线预览', permission: 'sys:pdm:collab:preview:list' },
];

/** 按权限过滤后的菜单 */
const menuItems: MenuProps['items'] = MENU_ITEMS.filter(
  (item) => !item.permission || hasPermission(item.permission),
).map(({ key, label }) => ({ key, label }));

function PdmLayout() {
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <Layout style={{ minHeight: '100%' }}>
      <Header style={{ display: 'flex', alignItems: 'center', color: '#fff' }}>
        <span style={{ fontSize: 16, fontWeight: 600 }}>Whale PDM - 产品数据管理</span>
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
            <Route path="/document" element={<DocumentCenter />} />
            <Route path="/product" element={<ProductCenter />} />
            <Route path="/part" element={<PartLibrary />} />
            <Route path="/bom" element={<BomLibrary />} />
            <Route path="/change" element={<ChangeCenter />} />
            <Route path="/change/order" element={<ChangeOrderCenter />} />
            <Route path="/change/notice" element={<ChangeNoticeCenter />} />
            <Route path="/change/trace" element={<ChangeTraceCenter />} />
            <Route path="/process/route" element={<ProcessRouteCenter />} />
            <Route path="/collab/deliverable" element={<DeliverableCenter />} />
            <Route path="/collab/review" element={<ReviewCenter />} />
            <Route path="/collab/preview" element={<PreviewCenter />} />
            <Route path="*" element={<Navigate to="/document" replace />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}

export default function App() {
  return (
    <BrowserRouter basename="/pdm">
      <PdmLayout />
    </BrowserRouter>
  );
}
