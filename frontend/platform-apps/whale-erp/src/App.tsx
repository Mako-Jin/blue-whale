// ERP 子应用根组件：Layout + 菜单，物料/供应商/主数据/凭证/应收应付/报表/供应链
import { useState } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import type { MenuProps } from 'antd';
import List from './pages/List';
import FormModal from './pages/Form';
import SupplierList from './pages/SupplierList';
import SupplierFormModal from './pages/SupplierForm';
import MasterDataPage from './pages/MasterDataPage';
import VoucherPage from './pages/VoucherPage';
import ArApPage from './pages/ArApPage';
import ReportPage from './pages/ReportPage';
import PurchaseOrderPage from './pages/PurchaseOrderPage';
import SalesOrderPage from './pages/SalesOrderPage';
import StockPage from './pages/StockPage';
import TransferPage from './pages/TransferPage';
import StocktakePage from './pages/StocktakePage';
import PurchaseReturnPage from './pages/PurchaseReturnPage';
import SalesReturnPage from './pages/SalesReturnPage';
import PurchaseRequisitionPage from './pages/PurchaseRequisitionPage';
import SalesQuotationPage from './pages/SalesQuotationPage';
import PriceListPage from './pages/PriceListPage';
import SafetyStockPage from './pages/SafetyStockPage';
import ForecastPage from './pages/ForecastPage';
import MpsPage from './pages/MpsPage';
import ProductionPage from './pages/ProductionPage';
import MrpRunPage from './pages/MrpRunPage';
import MrpOrderPage from './pages/MrpOrderPage';
import WorkCenterPage from './pages/WorkCenterPage';
import CrpPage from './pages/CrpPage';
import EmployeePage from './pages/EmployeePage';
import AttendancePage from './pages/AttendancePage';
import PayrollPage from './pages/PayrollPage';
import PerformancePage from './pages/PerformancePage';
import QcInspectionPage from './pages/QcInspectionPage';
import QcEquipmentPage from './pages/QcEquipmentPage';
import QcMetrologyPage from './pages/QcMetrologyPage';
import AnalyticsPage from './pages/AnalyticsPage';
import { type ErpItem, type ErpSupplier } from './api/erp';

const { Header, Content, Sider } = Layout;

/** 菜单项：存量物料/供应商 + Wave 5.78 主数据、财务与供应链 */
const menuItems: MenuProps['items'] = [
  { key: 'item-list', label: '物料管理' },
  { key: 'item-create', label: '新增物料' },
  { key: 'supplier-list', label: '供应商管理' },
  { key: 'supplier-create', label: '新增供应商' },
  { key: 'masterdata', label: 'ERP 主数据' },
  { key: 'voucher', label: '会计凭证' },
  { key: 'arap', label: '应收应付' },
  { key: 'report', label: '财务报表' },
  { key: 'purchase', label: '采购订单' },
  { key: 'sales', label: '销售订单' },
  { key: 'stock', label: '库存台账' },
  { key: 'transfer', label: '库存调拨' },
  { key: 'stocktake', label: '库存盘点' },
  { key: 'purchase-return', label: '采购退货' },
  { key: 'sales-return', label: '销售退货' },
  { key: 'purchase-requisition', label: '采购申请' },
  { key: 'sales-quotation', label: '销售报价' },
  { key: 'price-list', label: '价格表' },
  { key: 'safety-stock', label: '安全库存' },
  { key: 'forecast', label: '需求预测' },
  { key: 'mps', label: '主生产计划' },
  { key: 'production', label: '生产订单' },
  { key: 'work-center', label: '工作中心' },
  { key: 'crp', label: '产能负荷' },
  { key: 'mrp-run', label: 'MRP 运算' },
  { key: 'mrp-order', label: '计划订单' },
  { key: 'hr-employee', label: '组织与员工' },
  { key: 'hr-attendance', label: '考勤汇总' },
  { key: 'hr-payroll', label: '薪资批次' },
  { key: 'hr-performance', label: '绩效管理' },
  { key: 'qc-inspection', label: '检验与不合格品' },
  { key: 'qc-equipment', label: '设备与保养' },
  { key: 'qc-metrology', label: '量具与校准' },
  { key: 'bi-cockpit', label: '经营驾驶舱' },
  { key: 'bi-cube', label: '多维分析' },
  { key: 'bi-report', label: '报表中心' },
  { key: 'bi-alert', label: '预警中心' },
];

type PageKind =
  | 'item'
  | 'supplier'
  | 'masterdata'
  | 'voucher'
  | 'arap'
  | 'report'
  | 'purchase'
  | 'sales'
  | 'stock'
  | 'transfer'
  | 'stocktake'
  | 'purchase-return'
  | 'sales-return'
  | 'purchase-requisition'
  | 'sales-quotation'
  | 'price-list'
  | 'safety-stock'
  | 'forecast'
  | 'mps'
  | 'production'
  | 'work-center'
  | 'crp'
  | 'mrp-run'
  | 'mrp-order'
  | 'hr-employee'
  | 'hr-attendance'
  | 'hr-payroll'
  | 'hr-performance'
  | 'qc-inspection'
  | 'qc-equipment'
  | 'qc-metrology'
  | 'bi-cockpit'
  | 'bi-cube'
  | 'bi-report'
  | 'bi-alert';

/** 按宿主菜单路径决定初始页 */
function initialPage(): PageKind {
  const path = window.location.pathname;
  if (path.includes('/erp/master-data')) return 'masterdata';
  if (path.includes('/erp/finance/voucher')) return 'voucher';
  if (path.includes('/erp/finance/arap')) return 'arap';
  if (path.includes('/erp/finance/report')) return 'report';
  if (path.includes('/erp/scm/purchase-return')) return 'purchase-return';
  if (path.includes('/erp/scm/sales-return')) return 'sales-return';
  if (path.includes('/erp/scm/purchase-requisition')) return 'purchase-requisition';
  if (path.includes('/erp/scm/sales-quotation')) return 'sales-quotation';
  if (path.includes('/erp/scm/price-list')) return 'price-list';
  if (path.includes('/erp/scm/safety-stock')) return 'safety-stock';
  if (path.includes('/erp/scm/purchase')) return 'purchase';
  if (path.includes('/erp/scm/sales')) return 'sales';
  if (path.includes('/erp/scm/stocktake')) return 'stocktake';
  if (path.includes('/erp/scm/transfer')) return 'transfer';
  if (path.includes('/erp/scm/stock')) return 'stock';
  if (path.includes('/erp/mfg/mrp/planned-order')) return 'mrp-order';
  if (path.includes('/erp/mfg/mrp')) return 'mrp-run';
  if (path.includes('/erp/mfg/forecast')) return 'forecast';
  if (path.includes('/erp/mfg/mps')) return 'mps';
  if (path.includes('/erp/mfg/work-center')) return 'work-center';
  if (path.includes('/erp/mfg/crp')) return 'crp';
  if (path.includes('/erp/mfg/production')) return 'production';
  if (path.includes('/erp/hr/employee')) return 'hr-employee';
  if (path.includes('/erp/hr/attendance')) return 'hr-attendance';
  if (path.includes('/erp/hr/payroll')) return 'hr-payroll';
  if (path.includes('/erp/hr/performance')) return 'hr-performance';
  if (path.includes('/erp/qc/inspection')) return 'qc-inspection';
  if (path.includes('/erp/qc/equipment')) return 'qc-equipment';
  if (path.includes('/erp/qc/metrology')) return 'qc-metrology';
  if (path.includes('/erp/bi/cockpit')) return 'bi-cockpit';
  if (path.includes('/erp/bi/cube')) return 'bi-cube';
  if (path.includes('/erp/bi/report')) return 'bi-report';
  if (path.includes('/erp/bi/alert')) return 'bi-alert';
  if (path.includes('/erp/supplier')) return 'supplier';
  return 'item';
}

/** 初始菜单选中项（存量页面使用 list 后缀 key） */
function initialKey(): string {
  const page = initialPage();
  return page === 'item' || page === 'supplier' ? `${page}-list` : page;
}

/** 菜单 key 到页面 */
function pageOfKey(key: string): PageKind {
  if (key.startsWith('supplier')) return 'supplier';
  if (
    key === 'masterdata'
    || key === 'voucher'
    || key === 'arap'
    || key === 'report'
    || key === 'purchase'
    || key === 'sales'
    || key === 'stock'
    || key === 'transfer'
    || key === 'stocktake'
    || key === 'purchase-return'
    || key === 'sales-return'
    || key === 'purchase-requisition'
    || key === 'sales-quotation'
    || key === 'price-list'
    || key === 'safety-stock'
    || key === 'forecast'
    || key === 'mps'
    || key === 'production'
    || key === 'work-center'
    || key === 'crp'
    || key === 'mrp-run'
    || key === 'mrp-order'
    || key === 'hr-employee'
    || key === 'hr-attendance'
    || key === 'hr-payroll'
    || key === 'hr-performance'
    || key === 'qc-inspection'
    || key === 'qc-equipment'
    || key === 'qc-metrology'
    || key === 'bi-cockpit'
    || key === 'bi-cube'
    || key === 'bi-report'
    || key === 'bi-alert'
  ) {
    return key;
  }
  return 'item';
}

/** 页面标题 */
const PAGE_TITLE: Record<PageKind, string> = {
  item: '物料管理',
  supplier: '供应商管理',
  masterdata: 'ERP 主数据',
  voucher: '会计凭证',
  arap: '应收应付',
  report: '财务报表',
  purchase: '采购订单',
  sales: '销售订单',
  stock: '库存台账',
  transfer: '库存调拨',
  stocktake: '库存盘点',
  'purchase-return': '采购退货',
  'sales-return': '销售退货',
  'purchase-requisition': '采购申请',
  'sales-quotation': '销售报价',
  'price-list': '价格表',
  'safety-stock': '安全库存',
  forecast: '需求预测',
  mps: '主生产计划',
  production: '生产订单',
  'work-center': '工作中心',
  crp: '产能负荷',
  'mrp-run': 'MRP 运算',
  'mrp-order': '计划订单',
  'hr-employee': '组织与员工',
  'hr-attendance': '考勤汇总',
  'hr-payroll': '薪资批次',
  'hr-performance': '绩效管理',
  'qc-inspection': '检验与不合格品',
  'qc-equipment': '设备与保养',
  'qc-metrology': '量具与校准',
  'bi-cockpit': '经营驾驶舱',
  'bi-cube': '多维分析',
  'bi-report': '报表中心',
  'bi-alert': '预警中心',
};

export default function App() {
  const [page, setPage] = useState<PageKind>(initialPage);
  const [selectedKey, setSelectedKey] = useState(initialKey);
  const [itemFormOpen, setItemFormOpen] = useState(false);
  const [supplierFormOpen, setSupplierFormOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<ErpItem | null>(null);
  const [editingSupplier, setEditingSupplier] = useState<ErpSupplier | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    setSelectedKey(key);
    if (key === 'item-create') {
      setPage('item');
      setEditingItem(null);
      setItemFormOpen(true);
      return;
    }
    if (key === 'supplier-create') {
      setPage('supplier');
      setEditingSupplier(null);
      setSupplierFormOpen(true);
      return;
    }
    setPage(pageOfKey(key));
  };

  const handleEditItem = (item: ErpItem) => {
    setEditingItem(item);
    setItemFormOpen(true);
  };

  const handleEditSupplier = (supplier: ErpSupplier) => {
    setEditingSupplier(supplier);
    setSupplierFormOpen(true);
  };

  const handleSaved = () => {
    setItemFormOpen(false);
    setSupplierFormOpen(false);
    setEditingItem(null);
    setEditingSupplier(null);
    setRefreshKey((key) => key + 1);
  };

  return (
    <BrowserRouter basename="/erp">
      <Layout style={{ minHeight: '100%' }}>
        <Header style={{ display: 'flex', alignItems: 'center', color: '#fff' }}>
          <span style={{ fontSize: 16, fontWeight: 600 }}>
            Whale ERP - {PAGE_TITLE[page]}
          </span>
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
            {page === 'supplier' ? (
              <SupplierList
                refreshKey={refreshKey}
                onCreate={() => {
                  setEditingSupplier(null);
                  setSupplierFormOpen(true);
                }}
                onEdit={handleEditSupplier}
              />
            ) : null}
            {page === 'item' ? (
              <List
                refreshKey={refreshKey}
                onCreate={() => {
                  setEditingItem(null);
                  setItemFormOpen(true);
                }}
                onEdit={handleEditItem}
              />
            ) : null}
            {page === 'masterdata' ? <MasterDataPage /> : null}
            {page === 'voucher' ? <VoucherPage /> : null}
            {page === 'arap' ? <ArApPage /> : null}
            {page === 'report' ? <ReportPage /> : null}
            {page === 'purchase' ? <PurchaseOrderPage /> : null}
            {page === 'sales' ? <SalesOrderPage /> : null}
            {page === 'stock' ? <StockPage /> : null}
            {page === 'transfer' ? <TransferPage /> : null}
            {page === 'stocktake' ? <StocktakePage /> : null}
            {page === 'purchase-return' ? <PurchaseReturnPage /> : null}
            {page === 'sales-return' ? <SalesReturnPage /> : null}
            {page === 'purchase-requisition' ? <PurchaseRequisitionPage /> : null}
            {page === 'sales-quotation' ? <SalesQuotationPage /> : null}
            {page === 'price-list' ? <PriceListPage /> : null}
            {page === 'safety-stock' ? <SafetyStockPage /> : null}
            {page === 'forecast' ? <ForecastPage /> : null}
            {page === 'mps' ? <MpsPage /> : null}
            {page === 'production' ? <ProductionPage /> : null}
            {page === 'work-center' ? <WorkCenterPage /> : null}
            {page === 'crp' ? <CrpPage /> : null}
            {page === 'mrp-run' ? <MrpRunPage /> : null}
            {page === 'mrp-order' ? <MrpOrderPage /> : null}
            {page === 'hr-employee' ? <EmployeePage /> : null}
            {page === 'hr-attendance' ? <AttendancePage /> : null}
            {page === 'hr-payroll' ? <PayrollPage /> : null}
            {page === 'hr-performance' ? <PerformancePage /> : null}
            {page === 'qc-inspection' ? <QcInspectionPage /> : null}
            {page === 'qc-equipment' ? <QcEquipmentPage /> : null}
            {page === 'qc-metrology' ? <QcMetrologyPage /> : null}
            {page === 'bi-cockpit' ? <AnalyticsPage initialTab="cockpit" /> : null}
            {page === 'bi-cube' ? <AnalyticsPage initialTab="cube" /> : null}
            {page === 'bi-report' ? <AnalyticsPage initialTab="report" /> : null}
            {page === 'bi-alert' ? <AnalyticsPage initialTab="alert" /> : null}
          </Content>
        </Layout>
      </Layout>
      <FormModal
        open={itemFormOpen}
        initial={editingItem}
        onCancel={() => setItemFormOpen(false)}
        onSaved={handleSaved}
      />
      <SupplierFormModal
        open={supplierFormOpen}
        initial={editingSupplier}
        onCancel={() => setSupplierFormOpen(false)}
        onSaved={handleSaved}
      />
    </BrowserRouter>
  );
}
