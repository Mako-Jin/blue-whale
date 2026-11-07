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
import ReturnPage from './pages/return';
import MarketingPage from './pages/marketing';
import AnalyticsPage from './pages/analytics';
import ActionPlanPage from './pages/action-plan';
import CustomerToolsPage from './pages/customer/tools';
import { hasPermission } from './utils/permission';

const { Header, Content, Sider } = Layout;

/** 全部菜单项：permission 为空表示登录即可见 */
const MENU_ITEMS = [
  { key: '/customer', label: '客户管理', permission: 'sys:crm:customer:list' },
  { key: '/customer/tools', label: '客户工具', permission: 'sys:crm:customer:import' },
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
  { key: '/return', label: '退换货', permission: 'sys:crm:return:list' },
  { key: '/campaign', label: '营销活动', permission: 'sys:crm:campaign:list' },
  { key: '/nurture', label: '培育任务', permission: 'sys:crm:nurture:manage' },
  { key: '/marketing-send', label: '批量触达', permission: 'sys:crm:marketing:send' },
  { key: '/partner', label: '渠道伙伴', permission: 'sys:crm:partner:manage' },
  { key: '/dashboard', label: '销售看板', permission: 'sys:crm:analytics:dashboard' },
  { key: '/analytics/funnel', label: '漏斗分析', permission: 'sys:crm:analytics:funnel' },
  { key: '/analytics/customer', label: '客户分析', permission: 'sys:crm:analytics:customer' },
  { key: '/analytics/ranking', label: '销售排行', permission: 'sys:crm:analytics:ranking' },
  { key: '/analytics/collection', label: '回款分析', permission: 'sys:crm:analytics:collection' },
  { key: '/action-plan', label: '行动计划', permission: 'sys:crm:action:plan' },
  { key: '/analytics/snapshot', label: '指标快照', permission: 'sys:crm:analytics:dashboard' },
  { key: '/analytics/export', label: '数据导出', permission: 'sys:crm:export' },
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
  if (pathname.startsWith('/return')) {
    return '/return';
  }
  if (pathname.startsWith('/campaign')) {
    return '/campaign';
  }
  if (pathname.startsWith('/nurture')) {
    return '/nurture';
  }
  if (pathname.startsWith('/marketing-send')) {
    return '/marketing-send';
  }
  if (pathname.startsWith('/partner')) {
    return '/partner';
  }
  if (pathname.startsWith('/dashboard')) {
    return '/dashboard';
  }
  if (pathname.startsWith('/analytics/funnel')) {
    return '/analytics/funnel';
  }
  if (pathname.startsWith('/analytics/customer')) {
    return '/analytics/customer';
  }
  if (pathname.startsWith('/analytics/ranking')) {
    return '/analytics/ranking';
  }
  if (pathname.startsWith('/analytics/collection')) {
    return '/analytics/collection';
  }
  if (pathname.startsWith('/analytics/snapshot')) {
    return '/analytics/snapshot';
  }
  if (pathname.startsWith('/analytics/export')) {
    return '/analytics/export';
  }
  if (pathname.startsWith('/action-plan')) {
    return '/action-plan';
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
            <Route path="/customer/tools" element={<CustomerToolsPage />} />
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
            <Route path="/return" element={<ReturnPage />} />
            <Route path="/campaign" element={<MarketingPage tab="campaign" />} />
            <Route path="/nurture" element={<MarketingPage tab="nurture" />} />
            <Route path="/marketing-send" element={<MarketingPage tab="send" />} />
            <Route path="/partner" element={<MarketingPage tab="partner" />} />
            <Route path="/dashboard" element={<AnalyticsPage tab="dashboard" />} />
            <Route path="/analytics/funnel" element={<AnalyticsPage tab="funnel" />} />
            <Route path="/analytics/customer" element={<AnalyticsPage tab="customer" />} />
            <Route path="/analytics/ranking" element={<AnalyticsPage tab="ranking" />} />
            <Route path="/analytics/collection" element={<AnalyticsPage tab="collection" />} />
            <Route path="/analytics/snapshot" element={<AnalyticsPage tab="snapshot" />} />
            <Route path="/analytics/export" element={<AnalyticsPage tab="export" />} />
            <Route path="/action-plan" element={<ActionPlanPage />} />
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
