// 学习子应用布局：顶部导航（课程管理 / 培训计划 / 题库 / 试卷 / 我的学习）+ 内容区。
//
// M1 提供课程域界面，M2 增培训计划，M3-1 增题库与试卷；考试 / 证书导航项随后续波次增量注册。

import { Layout, Menu, Typography } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const { Header, Content } = Layout;

/** 导航项（与基座菜单 /lms/course、/lms/training、/lms/question、/lms/paper、/lms/my 对齐） */
const NAV_ITEMS = [
  { key: '/course', label: '课程管理' },
  { key: '/training', label: '培训计划' },
  { key: '/question', label: '题库' },
  { key: '/paper', label: '试卷' },
  { key: '/my', label: '我的学习' },
];

/**
 * 解析当前导航选中项：取第一个路径前缀命中的导航项。
 *
 * @param pathname 当前路径
 * @return 导航 key
 */
function resolveSelected(pathname: string): string {
  const hit = NAV_ITEMS.find((item) => pathname.startsWith(item.key));
  return hit?.key ?? '/course';
}

export default function LmsLayout() {
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
          学习培训考试
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
