// 空间工作台：左侧页面树与新建入口，右侧页面编辑器

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  App,
  Button,
  Card,
  Empty,
  Form,
  Input,
  Layout,
  List,
  Modal,
  Space,
  Spin,
  Tag,
  TreeSelect,
  Typography,
} from 'antd';
import {
  ArrowLeftOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  FileAddOutlined,
  FolderAddOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { hasPermission } from '../api/client';
import { insightApi } from '../api/insight';
import { pageApi } from '../api/page';
import { spaceApi } from '../api/space';
import type { DocPageTree, DocPageType, DocRecent, DocSpace } from '../api/types';
import { formatTime } from '../utils/docContent';
import DocEditor from '../components/DocEditor';
import MemberPanel from '../components/MemberPanel';
import PageTree from '../components/PageTree';
import RecycleBin from '../components/RecycleBin';

const { Text } = Typography;
const { Sider, Content } = Layout;

/** 可见性文案 */
const VISIBILITY_LABEL: Record<string, string> = {
  PUBLIC: '公开',
  MEMBER: '成员可见',
};

/** 角色文案 */
const ROLE_LABEL: Record<string, string> = {
  OWNER: '所有者',
  ADMIN: '管理员',
  EDITOR: '编辑者',
  READER: '阅读者',
};

/** 新建页面表单项 */
interface CreateFormValues {
  title: string;
  parentId: number;
}

/** 树选择节点 */
interface ParentOption {
  title: string;
  value: number;
  children?: ParentOption[];
}

/** 递归查找首个页面节点（目录不作为默认打开的目标） */
function findFirstPage(nodes: DocPageTree[]): DocPageTree | undefined {
  for (const node of nodes) {
    if (node.pageType === 'PAGE') {
      return node;
    }
    const child = findFirstPage(node.children ?? []);
    if (child) {
      return child;
    }
  }
  return undefined;
}

/** 取首个节点（空间只有目录时用于默认选中） */
function findFirstNode(nodes: DocPageTree[]): DocPageTree | undefined {
  return nodes[0];
}

/** 转换父节点选项 */
function toParentOptions(nodes: DocPageTree[]): ParentOption[] {
  return nodes.map((node) => ({
    title: `${node.pageType === 'FOLDER' ? '📁' : '📄'} ${node.title}`,
    value: node.id,
    children: toParentOptions(node.children ?? []),
  }));
}

export default function Workspace() {
  const { message } = App.useApp();
  const { spaceId: spaceIdParam } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const spaceId = Number(spaceIdParam);

  const [form] = Form.useForm<CreateFormValues>();
  const [space, setSpace] = useState<DocSpace | null>(null);
  const [pages, setPages] = useState<DocPageTree[]>([]);
  const [loading, setLoading] = useState(true);
  const [treeLoading, setTreeLoading] = useState(false);
  const [memberOpen, setMemberOpen] = useState(false);
  const [recycleOpen, setRecycleOpen] = useState(false);
  const [createType, setCreateType] = useState<DocPageType | null>(null);
  const [creating, setCreating] = useState(false);
  const [recentOpen, setRecentOpen] = useState(false);
  const [recentItems, setRecentItems] = useState<DocRecent[]>([]);
  const [recentLoading, setRecentLoading] = useState(false);

  const selectedId = Number(searchParams.get('pageId')) || undefined;
  const selectedRef = useRef<number | undefined>(selectedId);

  useEffect(() => {
    selectedRef.current = selectedId;
  }, [selectedId]);

  const canContribute = useMemo(
    () =>
      Boolean(
        space?.memberRole && space.memberRole !== 'READER' && hasPermission('sys:doc:page:add'),
      ),
    [space?.memberRole],
  );
  const canManageMember = useMemo(
    () => Boolean(space?.memberRole && hasPermission('sys:doc:member:list')),
    [space?.memberRole],
  );
  const canManageRecycle = useMemo(
    () =>
      Boolean(
        space?.memberRole &&
        (space.memberRole === 'OWNER' || space.memberRole === 'ADMIN') &&
        hasPermission('sys:doc:page:delete'),
      ),
    [space?.memberRole],
  );

  /** 切换当前页面（写入 URL，便于分享与刷新恢复） */
  const selectPage = useCallback(
    (pageId: number) => {
      selectedRef.current = pageId;
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          next.set('pageId', String(pageId));
          return next;
        },
        { replace: true },
      );
    },
    [setSearchParams],
  );

  /** 清除当前选中页面（页面删除后回到空态） */
  const clearPage = useCallback(() => {
    selectedRef.current = undefined;
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.delete('pageId');
        return next;
      },
      { replace: true },
    );
  }, [setSearchParams]);

  /** 首次加载且 URL 未带 pageId 时自动打开首个节点 */
  const autoSelect = useCallback(
    (tree: DocPageTree[]) => {
      if (selectedRef.current || tree.length === 0) {
        return;
      }
      const target = findFirstPage(tree) ?? findFirstNode(tree);
      if (target) {
        selectPage(target.id);
      }
    },
    [selectPage],
  );

  const loadTree = useCallback(async () => {
    setTreeLoading(true);
    try {
      const tree = await pageApi.tree(spaceId);
      setPages(tree);
      autoSelect(tree);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setTreeLoading(false);
    }
  }, [spaceId, autoSelect, message]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setSpace(await spaceApi.detail(spaceId));
      await loadTree();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [spaceId, loadTree, message]);

  useEffect(() => {
    if (Number.isFinite(spaceId) && spaceId > 0) {
      void load();
    }
  }, [spaceId, load]);

  /** 打开最近访问弹窗（跨空间，点击跳转到对应页面） */
  const openRecent = async () => {
    setRecentOpen(true);
    setRecentLoading(true);
    try {
      setRecentItems(await insightApi.recent(10));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setRecentLoading(false);
    }
  };

  /** 打开新建弹窗：页面或目录 */
  const openCreate = (type: DocPageType) => {
    setCreateType(type);
    form.resetFields();
    form.setFieldsValue({ parentId: 0, title: '' });
  };

  const submitCreate = async () => {
    if (!createType) {
      return;
    }
    const values = await form.validateFields();
    setCreating(true);
    try {
      const created = await pageApi.create({
        spaceId,
        parentId: values.parentId ?? 0,
        title: values.title.trim(),
        pageType: createType,
      });
      message.success(createType === 'FOLDER' ? '目录已创建' : '页面已创建');
      setCreateType(null);
      await loadTree();
      selectPage(created.id);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setCreating(false);
    }
  };

  if (!Number.isFinite(spaceId) || spaceId <= 0) {
    return <Empty description="空间地址无效" style={{ padding: 48 }} />;
  }

  if (loading && !space) {
    return (
      <div style={{ padding: 64, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }

  if (!space) {
    return <Empty description="空间不存在或无权访问" style={{ padding: 48 }} />;
  }

  const parentOptions: ParentOption[] = [
    { title: '📚 根目录', value: 0, children: toParentOptions(pages) },
  ];

  return (
    <Layout className="doc-workspace" style={{ background: '#f5f6f8' }}>
      <Sider className="doc-workspace-sider" width={300} theme="light">
        <div className="doc-workspace-space">
          <Link to="/spaces" className="doc-workspace-back">
            <ArrowLeftOutlined /> 空间列表
          </Link>
          <Typography.Title level={5} style={{ margin: '8px 0 4px' }}>
            {space.icon || '📄'} {space.name}
          </Typography.Title>
          <Space size={4} wrap>
            <Tag color={space.visibility === 'PUBLIC' ? 'blue' : 'default'}>
              {VISIBILITY_LABEL[space.visibility] ?? space.visibility}
            </Tag>
            {space.memberRole && (
              <Tag color="green">{ROLE_LABEL[space.memberRole] ?? space.memberRole}</Tag>
            )}
          </Space>
          {space.description && (
            <Text
              type="secondary"
              className="doc-workspace-desc"
              ellipsis={{ tooltip: space.description }}
            >
              {space.description}
            </Text>
          )}
        </div>

        <div className="doc-workspace-actions">
          {canContribute && (
            <>
              <Button block icon={<FileAddOutlined />} onClick={() => openCreate('PAGE')}>
                新建页面
              </Button>
              <Button block icon={<FolderAddOutlined />} onClick={() => openCreate('FOLDER')}>
                新建目录
              </Button>
            </>
          )}
          {canManageMember && (
            <Button block icon={<TeamOutlined />} onClick={() => setMemberOpen(true)}>
              成员管理
            </Button>
          )}
          {canManageRecycle && (
            <Button block icon={<DeleteOutlined />} onClick={() => setRecycleOpen(true)}>
              回收站
            </Button>
          )}
          <Button block icon={<ClockCircleOutlined />} onClick={() => void openRecent()}>
            最近访问
          </Button>
        </div>

        <div className="doc-workspace-tree">
          <Spin spinning={treeLoading}>
            <PageTree
              pages={pages}
              selectedId={selectedId}
              canEdit={canContribute}
              onSelect={selectPage}
              onRefresh={() => void loadTree()}
            />
          </Spin>
        </div>
      </Sider>

      <Content className="doc-workspace-content">
        <Card style={{ minHeight: '100%' }} styles={{ body: { padding: 24 } }}>
          {selectedId ? (
            <DocEditor
              key={selectedId}
              pageId={selectedId}
              onTreeChange={() => void loadTree()}
              onDeleted={clearPage}
            />
          ) : (
            <Empty description="从左侧选择一个页面开始阅读或编辑" style={{ padding: 64 }} />
          )}
        </Card>
      </Content>

      <MemberPanel space={space} open={memberOpen} onClose={() => setMemberOpen(false)} />

      <RecycleBin
        spaceId={space.id}
        open={recycleOpen}
        onClose={() => setRecycleOpen(false)}
        onChanged={loadTree}
      />

      <Modal
        title="最近访问"
        open={recentOpen}
        onCancel={() => setRecentOpen(false)}
        footer={null}
        width={560}
        destroyOnHidden
      >
        <Spin spinning={recentLoading}>
          <List
            dataSource={recentItems}
            locale={{ emptyText: <Empty description="暂无访问记录" /> }}
            renderItem={(item) => (
              <List.Item>
                <List.Item.Meta
                  title={
                    <Link
                      to={`/space/${item.spaceId}?pageId=${item.pageId}`}
                      onClick={() => setRecentOpen(false)}
                    >
                      {item.title}
                    </Link>
                  }
                  description={`${item.spaceName} · /${item.slug} · 访问于 ${formatTime(item.visitedAt)}`}
                />
              </List.Item>
            )}
          />
        </Spin>
      </Modal>

      <Modal
        title={createType === 'FOLDER' ? '新建目录' : '新建页面'}
        open={Boolean(createType)}
        confirmLoading={creating}
        onOk={() => void submitCreate()}
        onCancel={() => setCreateType(null)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="title"
            label="标题"
            rules={[
              { required: true, message: '请输入标题' },
              { max: 200, message: '最多 200 字' },
            ]}
          >
            <Input
              placeholder={createType === 'FOLDER' ? '例如 产品需求' : '例如 需求说明'}
              autoFocus
            />
          </Form.Item>
          <Form.Item name="parentId" label="父节点" initialValue={0}>
            <TreeSelect
              treeData={parentOptions}
              treeDefaultExpandAll
              placeholder="选择父节点（默认根目录）"
              allowClear
            />
          </Form.Item>
        </Form>
      </Modal>
    </Layout>
  );
}
