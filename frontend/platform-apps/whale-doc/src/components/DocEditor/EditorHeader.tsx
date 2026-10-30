// 编辑器头部视图：面包屑、标题行（标题输入与操作按钮）、协作状态栏

import { Avatar, Button, Dropdown, Input, Space, Tag, Tooltip, Typography } from 'antd';
import {
  CheckCircleOutlined,
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  HistoryOutlined,
  ImportOutlined,
  LockOutlined,
  MenuOutlined,
  RobotOutlined,
  ShareAltOutlined,
} from '@ant-design/icons';
import { hasPermission } from '../../api/client';
import type { DocPage } from '../../api/types';
import type { ExportFormat } from '../../api/transfer';
import type { CollabStatus, CollabUser } from '../../collab/DocCollabProvider';
import { formatTime } from '../../utils/docContent';
import { STATUS_COLOR, STATUS_LABEL, avatarText } from './editorShared';

const { Text } = Typography;

/** 头部视图属性 */
interface EditorHeaderProps {
  page: DocPage;
  title: string;
  editable: boolean;
  readOnly: boolean;
  saving: boolean;
  dirty: boolean;
  savedAt: string | null;
  approvalEnabled: boolean;
  collabActive: boolean;
  collabStatus: CollabStatus;
  collabLeader: boolean;
  collabUsers: CollabUser[];
  onTitleChange: (value: string) => void;
  onTitleBlur: () => void;
  onSave: () => void;
  onPublish: () => void;
  onShare: () => void;
  onAcl: () => void;
  onExport: (format: ExportFormat) => void;
  onImport: () => void;
  onDuplicate: () => void;
  onSaveTemplate: () => void;
  onAi: () => void;
  onArchive: () => void;
  onRestore: () => void;
  onDelete: () => void;
  onToggleStar: () => void;
  onInfo: () => void;
}

/** 编辑器头部：面包屑 + 标题操作行 + 状态栏 */
export default function EditorHeader({
  page,
  title,
  editable,
  readOnly,
  saving,
  dirty,
  savedAt,
  approvalEnabled,
  collabActive,
  collabStatus,
  collabLeader,
  collabUsers,
  onTitleChange,
  onTitleBlur,
  onSave,
  onPublish,
  onShare,
  onAcl,
  onExport,
  onImport,
  onDuplicate,
  onSaveTemplate,
  onAi,
  onArchive,
  onRestore,
  onDelete,
  onToggleStar,
  onInfo,
}: EditorHeaderProps) {
  const isFolder = page.pageType === 'FOLDER';
  const canPublish = editable && hasPermission('sys:doc:page:publish');
  const canArchive = editable && hasPermission('sys:doc:page:archive');
  const canDelete = page.canManage && hasPermission('sys:doc:page:delete');
  const canShare = hasPermission('sys:doc:share:manage') && page.status !== 'ARCHIVED';
  const canAcl = page.canManage && hasPermission('sys:doc:page:acl');
  const canExport = hasPermission('sys:doc:export');
  const canImport = editable && !isFolder && hasPermission('sys:doc:import');
  const canDuplicate = editable && hasPermission('sys:doc:page:add');
  const canSaveTemplate = editable && hasPermission('sys:doc:template:manage');
  const canUseAi = !isFolder && hasPermission('sys:agent:chat');
  const exportItems = [
    { key: 'md', label: '导出 Markdown' },
    { key: 'html', label: '导出 HTML' },
  ];
  const collabLabel =
    collabStatus === 'reconnecting' ? '协作重连中' : collabLeader ? '协作中 · 主编辑' : '协作中';
  const saveText = saving
    ? collabActive
      ? '协作保存中…'
      : '保存中…'
    : dirty
      ? '有未保存更改'
      : `${collabActive ? '已同步' : '已保存'}${savedAt ? ` · ${formatTime(savedAt)}` : ''}`;

  return (
    <>
      <div className="doc-editor-breadcrumb">
        <Text type="secondary">
          {(page.breadcrumb ?? []).map((item) => item.title).join(' / ') || page.title}
        </Text>
      </div>

      <div className="doc-editor-title-row">
        <Input
          variant="borderless"
          value={title}
          readOnly={readOnly}
          onChange={(event) => onTitleChange(event.target.value)}
          onBlur={onTitleBlur}
          onPressEnter={(event) => event.currentTarget.blur()}
          placeholder="未命名页面"
          style={{ fontSize: 26, fontWeight: 600, paddingInline: 0 }}
        />
        <Space wrap size={8}>
          <Tag color={STATUS_COLOR[page.status]}>{STATUS_LABEL[page.status]}</Tag>
          {page.restricted && <Tag color="red">受限页面</Tag>}
          {page.approvalStatus === 'REJECTED' && <Tag color="red">审批驳回</Tag>}
          {editable && (
            <Button type="primary" icon={<CheckCircleOutlined />} loading={saving} onClick={onSave}>
              保存
            </Button>
          )}
          {canPublish && (
            <Button onClick={onPublish}>
              {approvalEnabled ? '提交审批' : page.status === 'PUBLISHED' ? '重新发布' : '发布'}
            </Button>
          )}
          {canShare && (
            <Button icon={<ShareAltOutlined />} onClick={onShare}>
              分享
            </Button>
          )}
          {canAcl && (
            <Button icon={<LockOutlined />} onClick={onAcl}>
              页面权限
            </Button>
          )}
          {canExport && (
            <Dropdown
              menu={{
                items: exportItems,
                onClick: ({ key }) => onExport(key as ExportFormat),
              }}
            >
              <Button icon={<DownloadOutlined />}>导出</Button>
            </Dropdown>
          )}
          {canImport && (
            <Button icon={<ImportOutlined />} onClick={onImport}>
              导入
            </Button>
          )}
          {canDuplicate && (
            <Button icon={<CopyOutlined />} onClick={onDuplicate}>
              复制
            </Button>
          )}
          {canSaveTemplate && (
            <Button type="text" onClick={onSaveTemplate}>
              存为模板
            </Button>
          )}
          {canUseAi && (
            <Button type="text" icon={<RobotOutlined />} onClick={onAi}>
              AI 问答
            </Button>
          )}
          {canArchive && (
            <Button danger onClick={onArchive}>
              归档
            </Button>
          )}
          {page.canEdit && page.status === 'ARCHIVED' && hasPermission('sys:doc:page:archive') && (
            <Button icon={<HistoryOutlined />} onClick={onRestore}>
              取消归档
            </Button>
          )}
          {canDelete && (
            <Button danger icon={<DeleteOutlined />} onClick={onDelete}>
              删除
            </Button>
          )}
          <Tooltip title={page.starred ? '取消收藏' : '收藏'}>
            <Button type="text" onClick={onToggleStar}>
              {page.starred ? '★' : '☆'}
            </Button>
          </Tooltip>
          <Button type="text" icon={<MenuOutlined />} onClick={onInfo}>
            信息
          </Button>
        </Space>
      </div>

      <div className="doc-editor-status">
        <Space size={8} wrap align="center">
          <Text type="secondary">
            r{page.revision} · {saveText}
          </Text>
          {collabActive && <Tag color={collabStatus === 'reconnecting' ? 'gold' : 'blue'}>{collabLabel}</Tag>}
          {readOnly && <Tag>只读</Tag>}
          {collabActive && collabUsers.length > 0 && (
            <Avatar.Group size={24} max={{ count: 4 }}>
              {collabUsers.map((user) => (
                <Tooltip key={user.clientId} title={user.name}>
                  <Avatar style={{ backgroundColor: user.color, fontSize: 12 }}>{avatarText(user.name)}</Avatar>
                </Tooltip>
              ))}
            </Avatar.Group>
          )}
        </Space>
      </div>
    </>
  );
}
