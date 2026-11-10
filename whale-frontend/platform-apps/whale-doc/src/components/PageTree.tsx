// 页面树：展示空间内的页面/目录层级，支持拖拽移动

import { App, Tag, Tree, Typography } from 'antd';
import type { TreeDataNode, TreeProps } from 'antd';
import { FileTextOutlined, FolderOutlined, LockOutlined } from '@ant-design/icons';
import { pageApi } from '../api/page';
import type { DocPageStatus, DocPageTree } from '../api/types';

const { Text } = Typography;

/** 状态标签文案 */
const STATUS_LABEL: Record<DocPageStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档',
  PENDING_APPROVAL: '待审批',
};

/** 状态标签颜色 */
const STATUS_COLOR: Record<DocPageStatus, string> = {
  DRAFT: 'gold',
  PUBLISHED: 'green',
  ARCHIVED: 'default',
  PENDING_APPROVAL: 'blue',
};

/** 转换为 antd 树节点 */
function toTreeData(pages: DocPageTree[]): TreeDataNode[] {
  return pages.map((page) => ({
    key: String(page.id),
    title: (
      <span className="doc-tree-title">
        <span className="doc-tree-title-text">{page.title}</span>
        {page.restricted && (
          <Tag color="red" style={{ marginInlineStart: 6 }} icon={<LockOutlined />}>
            受限
          </Tag>
        )}
        {page.status !== 'PUBLISHED' && (
          <Tag color={STATUS_COLOR[page.status]} style={{ marginInlineStart: 6 }}>
            {STATUS_LABEL[page.status]}
          </Tag>
        )}
      </span>
    ),
    icon: page.pageType === 'FOLDER' ? <FolderOutlined /> : <FileTextOutlined />,
    children: toTreeData(page.children ?? []),
  }));
}

/** 按 ID 查找节点 */
function findNode(pages: DocPageTree[], id: number): DocPageTree | undefined {
  for (const page of pages) {
    if (page.id === id) {
      return page;
    }
    const child = findNode(page.children ?? [], id);
    if (child) {
      return child;
    }
  }
  return undefined;
}

/** 页面树属性 */
interface PageTreeProps {
  pages: DocPageTree[];
  selectedId?: number;
  canEdit: boolean;
  onSelect: (pageId: number) => void;
  onRefresh: () => void;
}

export default function PageTree({
  pages,
  selectedId,
  canEdit,
  onSelect,
  onRefresh,
}: PageTreeProps) {
  const { message } = App.useApp();

  const handleDrop: TreeProps['onDrop'] = async (info) => {
    if (!canEdit) {
      return;
    }
    const dragId = Number(info.dragNode.key);
    const targetId = Number(info.node.key);
    const target = findNode(pages, targetId);
    const parentId = info.dropPosition === 0 ? targetId : (target?.parentId ?? 0);
    if (dragId === parentId) {
      return;
    }
    try {
      await pageApi.move(dragId, parentId);
      message.success('页面已移动');
      onRefresh();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  if (pages.length === 0) {
    return <Text type="secondary">暂无页面，点击上方「新建页面」开始创作。</Text>;
  }

  return (
    <Tree
      blockNode
      showIcon
      draggable={canEdit}
      treeData={toTreeData(pages)}
      selectedKeys={selectedId ? [String(selectedId)] : []}
      onSelect={(keys) => {
        const key = keys[0];
        if (key) {
          onSelect(Number(key));
        }
      }}
      onDrop={handleDrop}
    />
  );
}
