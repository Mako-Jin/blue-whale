// 项目工作区：加载项目与状态配置，提供子页面上下文与导航

import { useCallback, useEffect, useState } from 'react';
import { App, Layout, Menu, Space, Spin, Tag, Typography } from 'antd';
import { Link, Outlet, useLocation, useNavigate, useParams } from 'react-router-dom';
import { projectApi, workflowApi, type Project, type Status } from '../api/pm';
import { projectStatusLabel } from '../utils';
import { WorkspaceContext } from '../workspace-context';

const { Header, Content } = Layout;

const NAV_ITEMS = [
  { key: 'overview', label: '概览' },
  { key: 'backlog', label: 'Backlog' },
  { key: 'board', label: '看板' },
  { key: 'sprint', label: '迭代' },
  { key: 'report', label: '报表' },
  { key: 'setting', label: '设置' },
];

export default function Workspace() {
  const { projectId = '' } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [project, setProject] = useState<Project | null>(null);
  const [statuses, setStatuses] = useState<Status[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    if (!projectId) {
      return;
    }
    setLoading(true);
    try {
      const [detail, statusList] = await Promise.all([
        projectApi.detail(projectId),
        workflowApi.statuses(projectId),
      ]);
      setProject(detail);
      setStatuses(statusList);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [projectId, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const reloadProject = useCallback(async () => {
    if (projectId) {
      setProject(await projectApi.detail(projectId));
    }
  }, [projectId]);

  const activeKey =
    NAV_ITEMS.find((item) => location.pathname.endsWith(`/${item.key}`))?.key ?? 'overview';

  if (loading && !project) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }

  return (
    <WorkspaceContext.Provider value={{ project: project as Project, statuses, reloadProject }}>
      <Layout style={{ minHeight: '100%', background: '#fff' }}>
        <Header
          style={{ background: '#fff', borderBottom: '1px solid #f0f0f0', paddingInline: 24 }}
        >
          <Space size={12} wrap>
            <Link to="/">← 项目列表</Link>
            <Typography.Title level={5} style={{ margin: 0 }}>
              {project?.key} · {project?.name}
            </Typography.Title>
            <Tag color={project?.status === 'ARCHIVED' ? 'default' : 'green'}>
              {projectStatusLabel(project?.status)}
            </Tag>
            <Typography.Text type="secondary">{project?.projectType}</Typography.Text>
          </Space>
        </Header>
        <Menu
          mode="horizontal"
          selectedKeys={[activeKey]}
          items={NAV_ITEMS}
          onClick={({ key }) => navigate(`/project/${projectId}/${key}`)}
        />
        <Content style={{ padding: 16 }}>
          <Outlet />
        </Content>
      </Layout>
    </WorkspaceContext.Provider>
  );
}
