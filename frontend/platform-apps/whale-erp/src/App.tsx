// ERP 子应用根组件：Layout + 菜单，物料/供应商列表页，表单由 Modal 承载
import { useState } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import type { MenuProps } from 'antd';
import List from './pages/List';
import FormModal from './pages/Form';
import SupplierList from './pages/SupplierList';
import SupplierFormModal from './pages/SupplierForm';
import { type ErpItem, type ErpSupplier } from './api/erp';

const { Header, Content, Sider } = Layout;

/** 菜单项：物料 / 供应商两组，新增项直接打开对应表单弹窗 */
const menuItems: MenuProps['items'] = [
  { key: 'item-list', label: '物料管理' },
  { key: 'item-create', label: '新增物料' },
  { key: 'supplier-list', label: '供应商管理' },
  { key: 'supplier-create', label: '新增供应商' },
];

type PageKind = 'item' | 'supplier';

/** 根据宿主菜单路径决定初始页：/erp/supplier 直接打开供应商列表 */
function initialPage(): PageKind {
  return window.location.pathname.includes('/erp/supplier') ? 'supplier' : 'item';
}

export default function App() {
  const [page, setPage] = useState<PageKind>(initialPage);
  const [selectedKey, setSelectedKey] = useState(`${initialPage()}-list`);
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
    setPage(key.startsWith('supplier') ? 'supplier' : 'item');
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
          <span style={{ fontSize: 16, fontWeight: 600 }}>Whale ERP - ERP 子应用</span>
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
            ) : (
              <List
                refreshKey={refreshKey}
                onCreate={() => {
                  setEditingItem(null);
                  setItemFormOpen(true);
                }}
                onEdit={handleEditItem}
              />
            )}
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
