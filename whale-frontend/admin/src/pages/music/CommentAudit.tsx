// 音乐运营 - 评论审核：状态 / 关键词过滤 + 通过 / 隐藏（幂等）
// 权限码：sys:music:comment:audit；隐藏后评论立即对用户不可见

import { App, Input, Popconfirm, Select, Space, Tag, Typography, type TableProps } from 'antd';
import { useRef, useState } from 'react';
import { AuthButton, ProTable, SearchForm, type ProTableRef } from '@bluewhale/whale-ui';
import {
  approveComment,
  hideComment,
  pageCommentAudit,
  type CommentAuditQuery,
  type CommentStatus,
  type CommentView,
} from '../../api/music';
import { useI18n } from '../../i18n';

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

  const [status, setStatus] = useState<CommentStatus | undefined>(undefined);
  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [actingId, setActingId] = useState<string | undefined>(undefined);

  const statusOptions: { value: CommentStatus; label: string }[] = [
    { value: 'PENDING', label: t('music.comment.status.pending') },
    { value: 'VISIBLE', label: t('music.comment.status.visible') },
    { value: 'HIDDEN', label: t('music.comment.status.hidden') },
  ];

  const handleApprove = async (row: CommentView) => {
    setActingId(row.id);
    try {
      await approveComment(row.id);
      message.success(t('music.comment.msg.approved'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.comment.msg.opFailed'));
    } finally {
      setActingId(undefined);
    }
  };

  const handleHide = async (row: CommentView) => {
    setActingId(row.id);
    try {
      await hideComment(row.id);
      message.success(t('music.comment.msg.hidden'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.comment.msg.opFailed'));
    } finally {
      setActingId(undefined);
    }
  };

  const columns: TableProps<CommentView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 90 },
    {
      title: t('music.comment.col.song'),
      dataIndex: 'songTitle',
      width: 160,
      render: (value: string, row) =>
        value ?? t('music.comment.col.songFallback', { id: row.songId }),
    },
    { title: t('music.comment.col.user'), dataIndex: 'userId', width: 90 },
    {
      title: t('music.comment.col.content'),
      dataIndex: 'content',
      render: (value: string, row) => (
        <Typography.Paragraph
          style={{ marginBottom: 0, maxWidth: 420 }}
          ellipsis={{ rows: 2, expandable: true, symbol: t('music.common.expand') }}
        >
          {/* parentId 运行时为字符串，根评论为 '0'（非空串，需显式判断） */}
          {row.parentId && row.parentId !== '0'
            ? t('music.comment.reply', { id: row.parentId, content: value })
            : value}
        </Typography.Paragraph>
      ),
    },
    {
      title: t('music.comment.col.state'),
      dataIndex: 'status',
      width: 100,
      render: (value: CommentStatus) => (
        <Tag color={STATUS_COLORS[value] ?? 'default'}>
          {statusOptions.find((item) => item.value === value)?.label ?? value}
        </Tag>
      ),
    },
    { title: t('music.comment.col.createTime'), dataIndex: 'createTime', width: 170 },
    {
      title: t('music.common.action'),
      key: 'actions',
      width: 180,
      render: (_value, row) => (
        <Space size={8}>
          {row.status !== 'VISIBLE' && (
            <AuthButton
              perm="sys:music:comment:audit"
              type="link"
              size="small"
              loading={actingId === row.id}
              onClick={() => handleApprove(row)}
            >
              {t('music.comment.action.approve')}
            </AuthButton>
          )}
          {row.status !== 'HIDDEN' && (
            <Popconfirm
              title={t('music.comment.confirm.hide')}
              okText={t('music.comment.action.hide')}
              cancelText={t('music.common.cancel')}
              onConfirm={() => handleHide(row)}
            >
              <AuthButton
                perm="sys:music:comment:audit"
                type="link"
                size="small"
                danger
                loading={actingId === row.id}
              >
                {t('music.comment.action.hide')}
              </AuthButton>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <ProTable<CommentView, CommentAuditQuery>
      ref={tableRef}
      rowKey="id"
      title={t('music.comment.title')}
      columns={columns}
      request={pageCommentAudit}
      params={{ status, keyword }}
      onError={(error) =>
        message.error(
          error instanceof Error ? error.message : t('music.comment.msg.listLoadFailed'),
        )
      }
      search={
        <SearchForm
          items={[
            {
              name: 'status',
              label: t('music.comment.search.state'),
              element: (
                <Select
                  allowClear
                  placeholder={t('music.comment.search.allStates')}
                  style={{ minWidth: 140 }}
                  options={statusOptions}
                />
              ),
            },
            {
              name: 'keyword',
              label: t('music.comment.search.keyword'),
              element: (
                <Input allowClear placeholder={t('music.comment.search.keywordPlaceholder')} />
              ),
            },
          ]}
          onSearch={(values) => {
            setStatus((values.status as CommentStatus) || undefined);
            setKeyword((values.keyword as string) || undefined);
          }}
          onReset={() => {
            setStatus(undefined);
            setKeyword(undefined);
          }}
        />
      }
    />
  );
}
