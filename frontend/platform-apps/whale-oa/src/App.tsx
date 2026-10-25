// OA 子应用根组件：Layout + 菜单，请假列表承载数据，表单由 Modal 承载
import { useState } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { Layout, Menu } from 'antd';
import type { MenuProps } from 'antd';
import List from './pages/List';
import FormModal from './pages/Form';
import type { OaLeave } from './api/oa';

const { Header, Content, Sider } = Layout;

/** 菜单项：请假管理 / 新增申请（打开表单弹窗） */
const menuItems: MenuProps['items'] = [
  { key: 'list', label: '请假管理' },
  { key: 'create', label: '新增申请' },
];

export default function App() {
  const [selectedKey, setSelectedKey] = useState('list');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<OaLeave | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const openCreate = () => {
    setEditing(null);
    setFormOpen(true);
  };

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    setSelectedKey(key);
    if (key === 'create') {
      openCreate();
    }
  };

  const handleEdit = (leave: OaLeave) => {
    setEditing(leave);
    setFormOpen(true);
  };

  const handleSaved = () => {
    setFormOpen(false);
    setEditing(null);
    setRefreshKey((key) => key + 1);
  };

  return (
    <BrowserRouter basename="/oa">
      <Layout style={{ minHeight: '100%' }}>
        <Header style={{ display: 'flex', alignItems: 'center', color: '#fff' }}>
          <span style={{ fontSize: 16, fontWeight: 600 }}>Whale OA - 请假管理</span>
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
            <List refreshKey={refreshKey} onCreate={openCreate} onEdit={handleEdit} />
          </Content>
        </Layout>
      </Layout>
      <FormModal
        open={formOpen}
        initial={editing}
        onCancel={() => setFormOpen(false)}
        onSaved={handleSaved}
      />
    </BrowserRouter>
  );
}
