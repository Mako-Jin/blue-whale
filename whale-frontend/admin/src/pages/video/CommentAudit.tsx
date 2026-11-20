// 视频运营 - 评论审核（M4）：状态过滤 + 通过 / 隐藏（幂等，隐藏后立即不可见）
// 权限码：sys:video:comment:audit

import { App, Popconfirm, Select, Space, Tag, Typography, type TableProps } from 'antd';
import { useRef, useState } from 'react';
import { AuthButton, ProTable, SearchForm, type ProTableRef } from '@bluewhale/whale-ui';
import {
  approveComment,
  hideComment,
  pageAuditComments,
  type CommentAuditQuery,
  type CommentAuditView,
  type CommentStatus,
} from '../../api/video';
import { useI18n } from '../../i18n';

type Translate = ReturnType<typeof useI18n>['t'];

const buildStatusOptions = (t: Translate): { value: CommentStatus; label: string }[] => [
  { value: 'PENDING', label: t('video.comment.status.pending') },
  { value: 'VISIBLE', label: t('video.comment.status.visible') },
  { value: 'HIDDEN', label: t('video.comment.status.hidden') },
];

/** 状态标签颜色 */
const STATUS_COLORS: Record<CommentStatus, string> = {
  PENDING: 'orange',
  VISIBLE: 'green',
  HIDDEN: 'red',
};

export default function CommentAudit() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const tableRef = useRef<ProTableRef>(null);

  const statusOptions = buildStatusOptions(t);

  const [status, setStatus] = useState<CommentStatus | undefined>(undefined);
  const [actingId, setActingId] = useState<string | undefined>(undefined);

  const handleApprove = async (row: CommentAuditView) => {
    setActingId(row.id);
    try {
      await approveComment(row.id);
      message.success(t('video.comment.msg.approved'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('video.comment.msg.failed'));
    } finally {
      setActingId(undefined);
    }
  };

  const handleHide = async (row: CommentAuditView) => {
    setActingId(row.id);
    try {
      await hideComment(row.id);
      message.success(t('video.comment.msg.hidden'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('video.comment.msg.failed'));
    } finally {
      setActingId(undefined);
    }
  };

  const columns: TableProps<CommentAuditView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 90 },
    {
      title: t('video.comment.col.contentId'),
      dataIndex: 'contentId',
      width: 110,
      render: (value?: string) => value ?? '-',
    },
    {
      title: t('video.comment.col.user'),
      dataIndex: 'userId',
      width: 100,
      render: (value?: string) => value ?? '-',
    },
    {
      title: t('video.comment.col.content'),
      dataIndex: 'content',
      render: (value: string, row) => (
        <Typography.Paragraph
          style={{ marginBottom: 0, maxWidth: 460 }}
          ellipsis={{ rows: 2, expandable: true, symbol: t('video.comment.expand') }}
        >
          {/* parentId 运行时为字符串，根评论为 '0'（非空串，需显式判断） */}
          {row.parentId && row.parentId !== '0'
            ? t('video.comment.reply', { id: row.parentId, content: value })
            : value}
        </Typography.Paragraph>
      ),
    },
    {
      title: t('video.comment.col.status'),
      dataIndex: 'status',
      width: 100,
      render: (value?: CommentStatus) => (
        <Tag color={value ? STATUS_COLORS[value] : 'default'}>
          {statusOptions.find((item) => item.value === value)?.label ??
            t('video.comment.status.unknown')}
        </Tag>
      ),
    },
    {
      title: t('video.comment.col.likeCount'),
      dataIndex: 'likeCount',
      width: 80,
      render: (value?: number) => value ?? 0,
    },
    {
      title: t('video.comment.col.createTime'),
      dataIndex: 'createTime',
      width: 180,
      render: (value?: string) => value ?? '-',
    },
    {
      title: t('video.comment.col.action'),
      key: 'actions',
      width: 180,
      render: (_, row) => (
        <Space size={8}>
          {row.status !== 'VISIBLE' ? (
            <AuthButton
              perm="sys:video:comment:audit"
              type="link"
              size="small"
              loading={actingId === row.id}
              onClick={() => handleApprove(row)}
            >
              {t('video.comment.action.approve')}
            </AuthButton>
          ) : null}
          {row.status !== 'HIDDEN' ? (
            <Popconfirm
              title={t('video.comment.confirm.hide')}
              okText={t('video.comment.action.hide')}
              cancelText={t('video.comment.action.cancel')}
              onConfirm={() => handleHide(row)}
            >
              <AuthButton
                perm="sys:video:comment:audit"
                type="link"
                size="small"
                danger
                loading={actingId === row.id}
              >
                {t('video.comment.action.hide')}
              </AuthButton>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <ProTable<CommentAuditView, CommentAuditQuery>
      ref={tableRef}
      rowKey="id"
      title={t('video.comment.list.title')}
      columns={columns}
      request={pageAuditComments}
      params={{ status }}
      onError={(error) =>
        message.error(error instanceof Error ? error.message : t('video.comment.msg.loadFailed'))
      }
      search={
        <SearchForm
          items={[
            {
              name: 'status',
              label: t('video.comment.search.status'),
              element: (
                <Select
                  allowClear
                  placeholder={t('video.comment.search.allStatus')}
                  style={{ minWidth: 140 }}
                  options={statusOptions}
                />
              ),
            },
          ]}
          onSearch={(values) => setStatus((values.status as CommentStatus) || undefined)}
          onReset={() => setStatus(undefined)}
        />
      }
    />
  );
}
