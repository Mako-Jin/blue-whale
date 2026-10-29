// 页面信息面板：详情 / 版本历史 / 附件 / 评论

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Badge,
  Button,
  Descriptions,
  Divider,
  Empty,
  Input,
  List,
  Modal,
  Pagination,
  Select,
  Space,
  Spin,
  Tabs,
  Tag,
  Typography,
  Upload,
} from 'antd';
import type { UploadProps } from 'antd';
import {
  DeleteOutlined,
  DownloadOutlined,
  HistoryOutlined,
  PaperClipOutlined,
  SendOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { attachmentApi } from '../api/attachment';
import { commentApi } from '../api/comment';
import { getCurrentUser, hasPermission } from '../api/client';
import { insightApi } from '../api/insight';
import { tagApi } from '../api/tag';
import { versionApi } from '../api/version';
import type {
  DocActivity,
  DocAttachment,
  DocBacklink,
  DocComment,
  DocPage,
  DocPageStatus,
  DocTag,
  DocVersion,
  DocVersionKind,
} from '../api/types';
import { formatTime } from '../utils/docContent';
import VersionPreview from './VersionPreview';

const { Text, Paragraph } = Typography;

/** 状态文案 */
const STATUS_LABEL: Record<DocPageStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档',
  PENDING_APPROVAL: '待审批',
};

/** 版本类型文案 */
const KIND_LABEL: Record<DocVersionKind, string> = {
  MANUAL: '手动',
  PUBLISH: '发布',
  AUTO: '自动',
  RESTORE: '恢复',
};

/** 附件大小格式化 */
function formatSize(size: number): string {
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

/** 信息面板属性 */
interface InfoPanelProps {
  open: boolean;
  page: DocPage;
  onClose: () => void;
  onReload: () => Promise<void>;
  onTreeChange: () => void;
}

export default function InfoPanel({ open, page, onClose, onReload, onTreeChange }: InfoPanelProps) {
  const [unread, setUnread] = useState(0);
  const handleRead = useCallback(() => setUnread(0), []);

  // 面板打开期间轮询评论未读数，用于页签角标（M2-7）
  useEffect(() => {
    if (!open) {
      return;
    }
    let cancelled = false;
    const load = async () => {
      try {
        const result = await commentApi.unread(page.id);
        if (!cancelled) {
          setUnread(result.unread);
        }
      } catch {
        // 无权读取或页面已删除时忽略角标
      }
    };
    void load();
    const timer = window.setInterval(() => void load(), 15000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [open, page.id]);

  return (
    <Modal title="页面信息" open={open} onCancel={onClose} footer={null} width={760}>
      <Tabs
        items={[
          { key: 'detail', label: '详情', children: <DetailTab page={page} /> },
          { key: 'tag', label: '标签', children: <TagTab page={page} /> },
          {
            key: 'backlink',
            label: '反向链接',
            children: <BacklinkTab page={page} />,
          },
          { key: 'activity', label: '动态', children: <ActivityTab page={page} /> },
          {
            key: 'version',
            label: '版本历史',
            children: <VersionTab page={page} onReload={onReload} onTreeChange={onTreeChange} />,
          },
          { key: 'attachment', label: '附件', children: <AttachmentTab page={page} /> },
          {
            key: 'comment',
            label: (
              <Badge count={unread} size="small" offset={[10, 0]}>
                评论
              </Badge>
            ),
            children: <CommentTab page={page} onRead={handleRead} />,
          },
        ]}
      />
    </Modal>
  );
}

/** 页面基础信息 */
function DetailTab({ page }: { page: DocPage }) {
  return (
    <Descriptions column={1} size="small" bordered>
      <Descriptions.Item label="标题">{page.title}</Descriptions.Item>
      <Descriptions.Item label="状态">
        <Space size={4}>
          <Tag color={page.status === 'PUBLISHED' ? 'green' : page.status === 'DRAFT' ? 'gold' : 'default'}>
            {STATUS_LABEL[page.status]}
          </Tag>
          {page.restricted && <Tag color="red">页面级权限</Tag>}
        </Space>
      </Descriptions.Item>
      <Descriptions.Item label="路径">{page.slug}</Descriptions.Item>
      <Descriptions.Item label="当前版本">r{page.revision}</Descriptions.Item>
      <Descriptions.Item label="已发布版本">
        {page.publishedRevision === null || page.publishedRevision === undefined
          ? '-'
          : `r${page.publishedRevision}`}
      </Descriptions.Item>
      <Descriptions.Item label="作者 / 最后编辑">
        {page.authorId} / {page.lastEditorId}
      </Descriptions.Item>
      <Descriptions.Item label="发布 / 归档时间">
        {`${formatTime(page.publishedAt)} / ${formatTime(page.archivedAt)}`}
      </Descriptions.Item>
      <Descriptions.Item label="创建 / 更新时间">
        {`${formatTime(page.createTime)} / ${formatTime(page.updateTime)}`}
      </Descriptions.Item>
      <Descriptions.Item label="面包屑">
        {(page.breadcrumb ?? []).map((item) => item.title).join(' / ') || page.title}
      </Descriptions.Item>
    </Descriptions>
  );
}

/** 标签页签：查看并可全量替换页面标签（M2-5） */
function TagTab({ page }: { page: DocPage }) {
  const { message } = App.useApp();
  const [tags, setTags] = useState<DocTag[]>([]);
  const [selected, setSelected] = useState<number[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const canManage = page.canEdit && hasPermission('sys:doc:tag:manage');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [spaceTags, pageTags] = await Promise.all([
        tagApi.list(page.spaceId),
        tagApi.pageTags(page.id),
      ]);
      setTags(spaceTags);
      setSelected(pageTags.map((tag) => tag.id));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [page.spaceId, page.id, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const save = async () => {
    setSaving(true);
    try {
      const result = await tagApi.updatePageTags(page.id, selected);
      setSelected(result.map((tag) => tag.id));
      message.success('页面标签已更新');
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Spin spinning={loading}>
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        {selected.length > 0 ? (
          <Space size={4} wrap>
            {selected.map((id) => {
              const tag = tags.find((item) => item.id === id);
              return tag ? (
                <Tag key={id} color={tag.color ?? 'blue'}>
                  {tag.name}
                </Tag>
              ) : null;
            })}
          </Space>
        ) : (
          <Text type="secondary">该页面暂无标签。</Text>
        )}
        {canManage ? (
          <Space.Compact style={{ width: '100%' }}>
            <Select
              mode="multiple"
              allowClear
              style={{ width: '100%' }}
              placeholder="选择空间标签（最多 20 个）"
              value={selected}
              maxCount={20}
              optionFilterProp="label"
              options={tags.map((tag) => ({
                value: tag.id,
                label: `${tag.name}${tag.pageCount ? `（${tag.pageCount}）` : ''}`,
              }))}
              onChange={(value) => setSelected(value)}
            />
            <Button type="primary" loading={saving} disabled={tags.length === 0} onClick={() => void save()}>
              保存
            </Button>
          </Space.Compact>
        ) : (
          <Text type="secondary">需要空间编辑者及以上并可管理标签才能修改。</Text>
        )}
      </Space>
    </Spin>
  );
}

/** 反向链接页签：列出仍可访问的引用方页面（M2-5） */
function BacklinkTab({ page }: { page: DocPage }) {
  const { message } = App.useApp();
  const [items, setItems] = useState<DocBacklink[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await insightApi.backlinks(page.id));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [page.id, message]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Spin spinning={loading}>
      <List
        dataSource={items}
        locale={{ emptyText: <Empty description="暂无其他页面引用本页" /> }}
        renderItem={(item) => (
          <List.Item>
            <List.Item.Meta
              title={
                <Link to={`/space/${item.spaceId}?pageId=${item.pageId}`}>
                  {item.title}
                </Link>
              }
              description={
                <Space direction="vertical" size={2}>
                  {item.linkText && <Text type="secondary">「{item.linkText}」</Text>}
                  <Text type="secondary">
                    {item.spaceName} · /{item.slug} · 更新于 {formatTime(item.updateTime)}
                  </Text>
                </Space>
              }
            />
          </List.Item>
        )}
      />
    </Spin>
  );
}

/** 动作文案 */
const ACTION_LABEL: Record<string, string> = {
  CREATE: '创建页面',
  SAVE: '保存页面',
  TITLE: '更新标题',
  PUBLISH: '发布页面',
  ARCHIVE: '归档页面',
  RESTORE: '取消归档',
  MOVE: '移动页面',
  TRASH: '移入回收站',
  UNTRASH: '恢复页面',
  COMMENT: '评论',
  TAG: '更新标签',
  ACL: '更新页面权限',
  IMPORT: '导入',
  COLLAB: '协作保存',
};

/** 页面动态页签（M2-5） */
function ActivityTab({ page }: { page: DocPage }) {
  const { message } = App.useApp();
  const [items, setItems] = useState<DocActivity[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await insightApi.activity(page.id, current, 20);
      setItems(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [page.id, current, message]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div>
      <Spin spinning={loading}>
        <List
          dataSource={items}
          locale={{ emptyText: <Empty description="暂无页面动态" /> }}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta
                title={`${ACTION_LABEL[item.action] ?? item.action}${item.detail ? ` · ${item.detail}` : ''}`}
                description={`用户 ${item.actorId ?? '-'} · ${formatTime(item.createTime)}`}
              />
            </List.Item>
          )}
        />
      </Spin>
      {total > 20 && (
        <div style={{ textAlign: 'right', marginTop: 12 }}>
          <Pagination current={current} pageSize={20} total={total} showSizeChanger={false}
            onChange={setCurrent} size="small" />
        </div>
      )}
    </div>
  );
}

/** 版本历史页签 */
function VersionTab({
  page,
  onReload,
  onTreeChange,
}: {
  page: DocPage;
  onReload: () => Promise<void>;
  onTreeChange: () => void;
}) {
  const { message, modal } = App.useApp();
  const [versions, setVersions] = useState<DocVersion[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(false);
  const [preview, setPreview] = useState<DocVersion | null>(null);
  const [manualOpen, setManualOpen] = useState(false);
  const [summary, setSummary] = useState('');
  const [working, setWorking] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await versionApi.page(page.id, current, 10);
      setVersions(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [page.id, current, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openPreview = async (version: DocVersion) => {
    try {
      setPreview(await versionApi.detail(page.id, version.revisionNo));
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const saveManual = async () => {
    setWorking(true);
    try {
      await versionApi.manual(page.id, summary.trim() || undefined);
      message.success('版本快照已保存');
      setManualOpen(false);
      setSummary('');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setWorking(false);
    }
  };

  const restore = (version: DocVersion) => {
    modal.confirm({
      title: `恢复到 r${version.revisionNo}？`,
      content: '将以该版本正文生成新的 revision，当前正文会被覆盖。',
      okText: '恢复',
      onOk: async () => {
        await versionApi.restore(page.id, version.revisionNo);
        message.success('已恢复历史版本');
        await onReload();
        onTreeChange();
        await load();
      },
    });
  };

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Button icon={<HistoryOutlined />} onClick={() => setManualOpen(true)} disabled={!page.canEdit}>
          保存当前版本
        </Button>
        <Text type="secondary">共 {total} 个快照</Text>
      </Space>
      <Spin spinning={loading}>
        <List
          dataSource={versions}
          locale={{ emptyText: <Empty description="暂无版本快照" /> }}
          renderItem={(version) => (
            <List.Item
              actions={[
                <Button key="preview" type="link" size="small" onClick={() => void openPreview(version)}>
                  预览
                </Button>,
                <Button
                  key="restore"
                  type="link"
                  size="small"
                  disabled={!page.canEdit || !hasPermission('sys:doc:version:restore')}
                  onClick={() => restore(version)}
                >
                  恢复
                </Button>,
              ]}
            >
              <List.Item.Meta
                title={
                  <Space>
                    <Text strong>r{version.revisionNo}</Text>
                    <Tag>{KIND_LABEL[version.versionKind]}</Tag>
                    <Text>{version.title}</Text>
                  </Space>
                }
                description={
                  <Space direction="vertical" size={2}>
                    <Text type="secondary">编辑人 {version.editorId}</Text>
                    <Text type="secondary">{formatTime(version.createTime)}</Text>
                    {version.changeSummary && <Text>{version.changeSummary}</Text>}
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      </Spin>
      {total > 10 && (
        <div style={{ textAlign: 'right', marginTop: 12 }}>
          <Pagination current={current} pageSize={10} total={total} showSizeChanger={false}
            onChange={setCurrent} size="small" />
        </div>
      )}

      <Modal
        title={`版本 r${preview?.revisionNo ?? ''} 预览`}
        open={Boolean(preview)}
        onCancel={() => setPreview(null)}
        footer={null}
        width={760}
        destroyOnHidden
      >
        {preview && <VersionPreview content={preview.contentJson} />}
      </Modal>

      <Modal
        title="保存版本快照"
        open={manualOpen}
        confirmLoading={working}
        onOk={() => void saveManual()}
        onCancel={() => setManualOpen(false)}
      >
        <Input.TextArea
          rows={3}
          maxLength={200}
          showCount
          value={summary}
          onChange={(event) => setSummary(event.target.value)}
          placeholder="本次快照的变更说明（可选）"
        />
      </Modal>
    </div>
  );
}

/** 附件页签 */
function AttachmentTab({ page }: { page: DocPage }) {
  const { message, modal } = App.useApp();
  const [attachments, setAttachments] = useState<DocAttachment[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setAttachments(await attachmentApi.list(page.id));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [page.id, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const customRequest: UploadProps['customRequest'] = async (options) => {
    try {
      await attachmentApi.upload(page.id, options.file as File);
      options.onSuccess?.({});
      message.success('附件已上传');
      await load();
    } catch (error) {
      options.onError?.(error as Error);
      message.error((error as Error).message);
    }
  };

  const download = async (attachment: DocAttachment) => {
    try {
      const blob = await attachmentApi.download(attachment.contentUrl);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = attachment.originalName;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const remove = (attachment: DocAttachment) => {
    modal.confirm({
      title: `移除附件「${attachment.originalName}」？`,
      content: '仅解除页面引用，文件实体保留在文件服务中。',
      okText: '移除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await attachmentApi.remove(page.id, attachment.id);
        message.success('附件已移除');
        await load();
      },
    });
  };

  return (
    <div>
      <Space style={{ marginBottom: 12 }}>
        <Upload
          showUploadList={false}
          customRequest={customRequest}
          beforeUpload={(file) => {
            const limit = 50 * 1024 * 1024;
            if (file.size > limit) {
              message.error('附件不能超过 50MB');
              return Upload.LIST_IGNORE;
            }
            return true;
          }}
        >
          <Button
            icon={<UploadOutlined />}
            disabled={!page.canEdit || !hasPermission('sys:doc:attachment:manage')}
          >
            上传附件
          </Button>
        </Upload>
        <Text type="secondary">附件经文件服务内网通道代理读取</Text>
      </Space>
      <Spin spinning={loading}>
        <List
          dataSource={attachments}
          locale={{ emptyText: <Empty description="暂无附件" /> }}
          renderItem={(attachment) => (
            <List.Item
              actions={[
                <Button key="download" type="link" size="small" icon={<DownloadOutlined />}
                  onClick={() => void download(attachment)}>
                  下载
                </Button>,
                <Button key="remove" type="link" size="small" danger icon={<DeleteOutlined />}
                  disabled={!page.canEdit || !hasPermission('sys:doc:attachment:manage')}
                  onClick={() => remove(attachment)}>
                  移除
                </Button>,
              ]}
            >
              <List.Item.Meta
                avatar={<PaperClipOutlined />}
                title={attachment.originalName}
                description={`${formatSize(attachment.fileSize)} · ${attachment.contentType}`}
              />
            </List.Item>
          )}
        />
      </Spin>
    </div>
  );
}

/** 评论页签 */
function CommentTab({ page, onRead }: { page: DocPage; onRead: () => void }) {
  const { message, modal } = App.useApp();
  const [comments, setComments] = useState<DocComment[]>([]);
  const [loading, setLoading] = useState(false);
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [replyTo, setReplyTo] = useState<DocComment | null>(null);
  const currentUserId = getCurrentUser()?.userId;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setComments(await commentApi.list(page.id));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [page.id, message]);

  useEffect(() => {
    void load();
    // 打开评论页签视为已读，清除角标（M2-7）
    commentApi.markRead(page.id).then(() => onRead()).catch(() => undefined);
  }, [load, page.id, onRead]);

  /** 便捷插入 @ 用户ID（后端从正文解析提及） */
  const insertMention = () => {
    const raw = window.prompt('输入要 @ 的用户ID');
    if (!raw || !/^\d+$/.test(raw.trim())) {
      return;
    }
    setContent((value) => `${value}@${raw.trim()} `);
  };

  const submit = async () => {
    if (!content.trim()) {
      return;
    }
    setSubmitting(true);
    try {
      await commentApi.create(page.id, {
        content: content.trim(),
        parentId: replyTo?.id,
      });
      setContent('');
      message.success(replyTo ? '回复已发布' : '评论已发布');
      setReplyTo(null);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  const canModify = (comment: DocComment) =>
    String(comment.authorId) === String(currentUserId ?? '')
    || (page.canManage && hasPermission('sys:doc:comment:manage'));
  const canComment = page.canComment && hasPermission('sys:doc:comment:add');

  const toggleResolve = async (comment: DocComment, resolved: boolean) => {
    try {
      await commentApi.resolve(comment.id, resolved);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const remove = (comment: DocComment) => {
    modal.confirm({
      title: '删除该评论？',
      content: '主评论下的回复会一并删除。',
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await commentApi.remove(comment.id);
        message.success('评论已删除');
        await load();
      },
    });
  };

  const renderComment = (comment: DocComment, nested = false) => (
    <List.Item
      key={comment.id}
      actions={[
        !nested && canComment && (
          <Button key="reply" type="link" size="small" onClick={() => setReplyTo(comment)}>
            回复
          </Button>
        ),
        ...(canModify(comment)
          ? [
              <Button key="resolve" type="link" size="small"
                onClick={() => void toggleResolve(comment, !comment.resolved)}>
                {comment.resolved ? '重新打开' : '标记解决'}
              </Button>,
              <Button key="delete" type="link" size="small" danger icon={<DeleteOutlined />}
                onClick={() => remove(comment)} />,
            ]
          : []),
      ].filter(Boolean)}
    >
      <List.Item.Meta
        title={
          <Space>
            <Text strong>用户 {comment.authorId}</Text>
            {comment.resolved && <Tag color="green">已解决</Tag>}
            <Text type="secondary">{formatTime(comment.createTime)}</Text>
          </Space>
        }
        description={
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Paragraph style={{ margin: 0 }}>{comment.content}</Paragraph>
            {!nested && comment.replies.length > 0 && (
              <div style={{ paddingInlineStart: 16, borderInlineStart: '2px solid #f0f0f0' }}>
                {comment.replies.map((reply) => renderComment(reply, true))}
              </div>
            )}
          </Space>
        }
      />
    </List.Item>
  );

  return (
    <div>
      {!page.canComment && <Text type="secondary">加入空间后可参与评论。</Text>}
      <Spin spinning={loading}>
        <List
          dataSource={comments}
          locale={{ emptyText: <Empty description="暂无评论" /> }}
          renderItem={(comment) => renderComment(comment)}
        />
      </Spin>
      <Divider />
      {replyTo && (
        <Space style={{ marginBottom: 8 }}>
          <Text type="secondary">回复用户 {replyTo.authorId}</Text>
          <Button size="small" type="link" onClick={() => setReplyTo(null)}>
            取消回复
          </Button>
        </Space>
      )}
      <Space.Compact style={{ width: '100%' }}>
        <Input.TextArea
          rows={2}
          maxLength={2000}
          value={content}
          disabled={!canComment}
          onChange={(event) => setContent(event.target.value)}
          placeholder={replyTo ? '写下回复…' : '写下评论，支持 @用户ID 提及'}
        />
        <Button type="text" disabled={!canComment} onClick={insertMention}>
          @提及
        </Button>
        <Button type="primary" icon={<SendOutlined />} loading={submitting} disabled={!canComment}
          onClick={() => void submit()}>
          发送
        </Button>
      </Space.Compact>
    </div>
  );
}
