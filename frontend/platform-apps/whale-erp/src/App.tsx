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
  | 'stocktake';

/** 按宿主菜单路径决定初始页 */
function initialPage(): PageKind {
  const path = window.location.pathname;
  if (path.includes('/erp/master-data')) return 'masterdata';
  if (path.includes('/erp/finance/voucher')) return 'voucher';
  if (path.includes('/erp/finance/arap')) return 'arap';
  if (path.includes('/erp/finance/report')) return 'report';
  if (path.includes('/erp/scm/purchase')) return 'purchase';
  if (path.includes('/erp/scm/sales')) return 'sales';
  if (path.includes('/erp/scm/stocktake')) return 'stocktake';
  if (path.includes('/erp/scm/transfer')) return 'transfer';
  if (path.includes('/erp/scm/stock')) return 'stock';
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
