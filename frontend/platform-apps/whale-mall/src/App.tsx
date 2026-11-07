// 商城子应用根组件：类目 / 商品 / 库存 / 渠道 / 购物车 / 订单，菜单按权限码过滤
import { Layout, Menu, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import CategoryPage from './pages/category';
import ProductPage from './pages/product';
import ProductDetailPage from './pages/product/detail';
import StockPage from './pages/stock';
import ChannelPage from './pages/channel';
import CartPage from './pages/cart';
import OrderPage from './pages/order';
import OrderDetailPage from './pages/order/detail';
import AfterSalePage from './pages/aftersale';
import { hasPermission } from './utils/permission';

const { Header, Content, Sider } = Layout;

/** 菜单项：permission 为空表示登录即可见 */
const MENU_ITEMS = [
  { key: '/category', label: '类目管理', permission: 'sys:mall:category:list' },
  { key: '/product', label: '商品管理', permission: 'sys:mall:product:list' },
  { key: '/stock', label: '库存管理', permission: 'sys:mall:stock:list' },
  { key: '/channel', label: '渠道管理', permission: 'sys:mall:channel:list' },
  { key: '/cart', label: '购物车', permission: 'sys:mall:cart:manage' },
  { key: '/order', label: '订单管理', permission: 'sys:mall:order:list' },
  { key: '/aftersale', label: '售后单', permission: 'sys:mall:aftersale:apply' },
];

/** 当前账号可见的菜单 */
const visibleItems = MENU_ITEMS.filter((item) => !item.permission || hasPermission(item.permission));

const menuItems: MenuProps['items'] = visibleItems.map(({ key, label }) => ({ key, label }));

/** 兜底首页：第一个有权限的菜单（避免无权限账号进来看到 403 空白） */
const HOME_PATH = visibleItems[0]?.key ?? '/category';

/**
 * 解析当前选中菜单：详情页归并到列表菜单。
 *
 * @param pathname 当前路径
 * @returns 选中的菜单 key
 */
function resolveSelectedKey(pathname: string): string {
  if (pathname.startsWith('/product')) {
    return '/product';
  }
  if (pathname.startsWith('/order')) {
    return '/order';
  }
  return pathname;
}

function MallLayout() {
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <Layout style={{ minHeight: '100%' }}>
      <Header style={{ display: 'flex', alignItems: 'center', color: '#fff' }}>
        <span style={{ fontSize: 16, fontWeight: 600 }}>Whale Mall - 商城</span>
        <Typography.Text style={{ color: 'rgba(255,255,255,0.65)', marginLeft: 12 }}>
          一套内核兼容零售 / 经销订货 / 员工福利
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
            <Route path="/category" element={<CategoryPage />} />
            <Route path="/product" element={<ProductPage />} />
            <Route path="/product/:id" element={<ProductDetailPage />} />
            <Route path="/stock" element={<StockPage />} />
            <Route path="/channel" element={<ChannelPage />} />
            <Route path="/cart" element={<CartPage />} />
            <Route path="/order" element={<OrderPage />} />
            <Route path="/order/:id" element={<OrderDetailPage />} />
            <Route path="/aftersale" element={<AfterSalePage />} />
            <Route path="*" element={<Navigate to={HOME_PATH} replace />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}

export default function App() {
  return (
    <BrowserRouter basename="/mall">
      <MallLayout />
    </BrowserRouter>
  );
}
