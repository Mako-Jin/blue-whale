// 党政工会子应用布局：顶部导航 + 内容区。
//
// 导航项跟随已落地页面增量注册，不预先放空菜单——点进去 404 比看不到入口更糟。
// M-P0 工作台；M-P1 党建台账四项（支部 / 党员 / 组织生活 / 党费）；
// 后续波次追加党建内容（M-P2）、工会（M-P3）、看板（M-P4）。

import { Layout, Menu, Typography } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const { Header, Content } = Layout;

/** 导航项（与基座菜单 /party/* 路径对齐） */
const NAV_ITEMS = [
  { key: '/', label: '工作台' },
  { key: '/branch', label: '支部台账' },
  { key: '/member', label: '党员档案' },
  { key: '/activity', label: '组织生活' },
  { key: '/fee', label: '党费台账' },
];

/**
 * 解析当前导航选中项。
 *
 * <p>先取精确命中，再取最长前缀命中；M-P0 只有根路径，保持与 LMS 相同的判定顺序，
 * 避免后续加子路径时把 `/branch` 与 `/branch-member` 点亮成同一项。</p>
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

export default function PartyLayout() {
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <Layout style={{ minHeight: '100%', background: '#f5f6f8' }}>
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
        }}
      >
        <Typography.Title level={5} style={{ margin: 0, whiteSpace: 'nowrap' }}>
          党政工会
        </Typography.Title>
        <Menu
          mode="horizontal"
          selectedKeys={[resolveSelected(location.pathname)]}
          items={NAV_ITEMS}
          onClick={(info) => navigate(info.key)}
          style={{ flex: 1, minWidth: 0, borderBottom: 'none' }}
        />
      </Header>
      <Content style={{ padding: 16 }}>
        <Outlet />
      </Content>
    </Layout>
  );
}
