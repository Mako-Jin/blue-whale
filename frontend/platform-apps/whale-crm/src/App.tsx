// CRM 子应用根组件：客户、公海、线索、商机、报价、预测与目标，菜单按权限码过滤
import { Layout, Menu, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import CustomerList from './pages/customer';
import CustomerDetail from './pages/customer/detail';
import PoolList from './pages/pool';
import LeadList from './pages/lead';
import OpportunityPage from './pages/opportunity';
import QuotePage from './pages/quote';
import ForecastPage from './pages/forecast';
import ContractPage from './pages/contract';
import CollectionPage from './pages/collection';
import OrderPage from './pages/order';
import InvoicePage from './pages/invoice';
import ArPage from './pages/ar';
import TicketPage from './pages/ticket';
import ServiceSlaPage from './pages/service-sla';
import KbPage from './pages/kb';
import SatisfactionPage from './pages/satisfaction';
import { hasPermission } from './utils/permission';

const { Header, Content, Sider } = Layout;

/** 全部菜单项：permission 为空表示登录即可见 */
const MENU_ITEMS = [
  { key: '/customer', label: '客户管理', permission: 'sys:crm:customer:list' },
  { key: '/pool', label: '公海客户', permission: 'sys:crm:pool:view' },
  { key: '/lead', label: '线索管理', permission: 'sys:crm:lead:list' },
  { key: '/opportunity', label: '商机管理', permission: 'sys:crm:opportunity:list' },
  { key: '/quote', label: '销售报价', permission: 'sys:crm:quote:list' },
  { key: '/forecast', label: '销售预测', permission: 'sys:crm:forecast:list' },
  { key: '/contract', label: '合同台账', permission: 'sys:crm:contract:list' },
  { key: '/collection', label: '回款计划', permission: 'sys:crm:collection:list' },
  { key: '/order', label: '商务订单', permission: 'sys:crm:order:list' },
  { key: '/invoice', label: '发票与应收', permission: 'sys:crm:invoice:list' },
  { key: '/ar', label: '客户应收对账', permission: 'sys:crm:ar:reconcile' },
  { key: '/ticket', label: '售后工单', permission: 'sys:crm:ticket:list' },
  { key: '/service-sla', label: 'SLA 设置', permission: 'sys:crm:service:sla:manage' },
  { key: '/kb', label: '知识库', permission: 'sys:crm:kb:list' },
  { key: '/satisfaction', label: '满意度', permission: 'sys:crm:survey:manage' },
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
  if (pathname.startsWith('/lead')) {
    return '/lead';
  }
  if (pathname.startsWith('/opportunity')) {
    return '/opportunity';
  }
  if (pathname.startsWith('/quote')) {
    return '/quote';
  }
  if (pathname.startsWith('/forecast')) {
    return '/forecast';
  }
  if (pathname.startsWith('/contract')) {
    return '/contract';
  }
  if (pathname.startsWith('/collection')) {
    return '/collection';
  }
  if (pathname.startsWith('/order')) {
    return '/order';
  }
  if (pathname.startsWith('/invoice')) {
    return '/invoice';
  }
  if (pathname.startsWith('/ar')) {
    return '/ar';
  }
  if (pathname.startsWith('/ticket')) {
    return '/ticket';
  }
  if (pathname.startsWith('/service-sla')) {
    return '/service-sla';
  }
  if (pathname.startsWith('/kb')) {
    return '/kb';
  }
  if (pathname.startsWith('/satisfaction')) {
    return '/satisfaction';
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
          客户主数据 / 销售过程 / 报价预测 / 合同回款
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
            <Route path="/lead" element={<LeadList />} />
            <Route path="/opportunity" element={<OpportunityPage />} />
            <Route path="/opportunity/funnel" element={<OpportunityPage />} />
            <Route path="/quote" element={<QuotePage />} />
            <Route path="/forecast" element={<ForecastPage />} />
            <Route path="/contract" element={<ContractPage />} />
            <Route path="/collection" element={<CollectionPage />} />
            <Route path="/order" element={<OrderPage />} />
            <Route path="/invoice" element={<InvoicePage />} />
            <Route path="/ar" element={<ArPage />} />
            <Route path="/ticket" element={<TicketPage />} />
            <Route path="/service-sla" element={<ServiceSlaPage />} />
            <Route path="/kb" element={<KbPage />} />
            <Route path="/satisfaction" element={<SatisfactionPage />} />
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
