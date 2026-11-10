// 学习子应用布局：顶部导航（课程 / 培训 / 题库 / 试卷 / 考试 / 判分 / 证书 / 我的）+ 内容区。
//
// M1 提供课程域界面，M2 增培训计划，M3-1 增题库与试卷，M3-2 增考试与判分，
// M4-1 增证书模板、证书管理与我的证书，M4-2 增学分与我的学分，M5 增集成设置；
// 看板导航随后续波次增量注册。
// 导航项超过宽度时由 antd 的 horizontal Menu 自动收进「更多」，无需另行分组。

import { Layout, Menu, Typography } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const { Header, Content } = Layout;

/** 导航项（与基座菜单 /lms/* 路径对齐） */
const NAV_ITEMS = [
  { key: '/course', label: '课程管理' },
  { key: '/training', label: '培训计划' },
  { key: '/question', label: '题库' },
  { key: '/paper', label: '试卷' },
  { key: '/exam', label: '考试' },
  { key: '/grade', label: '判分' },
  { key: '/cert', label: '证书模板' },
  { key: '/certificate', label: '证书管理' },
  { key: '/credit', label: '学分' },
  { key: '/my-exam', label: '我的考试' },
  { key: '/my-cert', label: '我的证书' },
  { key: '/my-credit', label: '我的学分' },
  { key: '/my', label: '我的学习' },
  { key: '/integration', label: '集成设置' },
  { key: '/analytics', label: '学习看板' },
];

/** 答题页归属于「考试」导航（路径前缀与导航项不同，单独映射） */
const NAV_ALIAS: Record<string, string> = { '/attempt': '/exam' };

/**
 * 解析当前导航选中项。
 *
 * <p>先取精确命中，再取「最长前缀命中」——不能用列表顺序取第一个前缀命中：
 * {@code /cert} 是 {@code /certificate} 的前缀、{@code /my} 是 {@code /my-cert} 的前缀，
 * 按顺序判断会把后两个页面点亮成前一个。</p>
 *
 * @param pathname 当前路径
 * @return 导航 key
 */
function resolveSelected(pathname: string): string {
  const alias = Object.keys(NAV_ALIAS).find((prefix) => pathname.startsWith(prefix));
  if (alias) {
    return NAV_ALIAS[alias];
  }
  const exact = NAV_ITEMS.find((item) => pathname === item.key);
  if (exact) {
    return exact.key;
  }
  const hits = NAV_ITEMS.filter((item) => pathname.startsWith(item.key));
  hits.sort((left, right) => right.key.length - left.key.length);
  return hits[0]?.key ?? '/course';
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
