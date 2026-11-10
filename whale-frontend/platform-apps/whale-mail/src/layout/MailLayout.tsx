// 邮箱子应用布局：顶部导航 + 内容区。
//
// 三栏阅读界面需要占满视口高度（列表与阅读区各自滚动），因此内容区
// 用 flex:1 + minHeight:0 撑开——少了 minHeight:0，子元素会被内容顶高，
// 底部就会被裁掉且滚动条不出现。

import { Layout, Menu, Typography } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const { Header, Content } = Layout;

/** 导航项（与基座菜单 /mail/* 路径对齐） */
const NAV_ITEMS = [
  { key: '/', label: '邮箱' },
  { key: '/account', label: '账号设置' },
];

/**
 * 解析当前导航选中项。
 *
 * @param pathname 当前路径
 * @returns 导航 key
 */
function resolveSelected(pathname: string): string {
  const exact = NAV_ITEMS.find((item) => pathname === item.key);
  if (exact) {
    return exact.key;
  }
  const hits = NAV_ITEMS.filter((item) => item.key !== '/' && pathname.startsWith(item.key));
  hits.sort((left, right) => right.key.length - left.key.length);
  return hits[0]?.key ?? '/';
}

export default function MailLayout() {
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <Layout style={{ height: '100%', background: '#f5f6f8', display: 'flex', flexDirection: 'column' }}>
      <Header
        style={{
          background: '#fff',
          borderBottom: '1px solid #f0f0f0',
          paddingInline: 24,
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          height: 56,
          lineHeight: '56px',
          flex: '0 0 auto',
        }}
      >
        <Typography.Title level={5} style={{ margin: 0, whiteSpace: 'nowrap' }}>
          邮箱
        </Typography.Title>
        <Menu
          mode="horizontal"
          selectedKeys={[resolveSelected(location.pathname)]}
          items={NAV_ITEMS}
          onClick={(info) => navigate(info.key)}
          style={{ flex: 1, minWidth: 0, borderBottom: 'none' }}
        />
      </Header>
      <Content style={{ padding: 16, flex: 1, minHeight: 0, display: 'flex' }}>
        <Outlet />
      </Content>
    </Layout>
  );
}
