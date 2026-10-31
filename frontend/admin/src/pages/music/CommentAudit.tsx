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

const STATUS_OPTIONS: { value: CommentStatus; label: string }[] = [
  { value: 'PENDING', label: '待审核' },
  { value: 'VISIBLE', label: '可见' },
  { value: 'HIDDEN', label: '已隐藏' },
];

/** 状态标签颜色 */
const STATUS_COLORS: Record<CommentStatus, string> = {
  PENDING: 'orange',
  VISIBLE: 'green',
  HIDDEN: 'red',
};

export default function CommentAudit() {
  const { message } = App.useApp();
  const tableRef = useRef<ProTableRef>(null);

  const [status, setStatus] = useState<CommentStatus | undefined>(undefined);
  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [actingId, setActingId] = useState<number | undefined>(undefined);

  const handleApprove = async (row: CommentView) => {
    setActingId(row.id);
    try {
      await approveComment(row.id);
      message.success('评论已通过审核');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    } finally {
      setActingId(undefined);
    }
  };

  const handleHide = async (row: CommentView) => {
    setActingId(row.id);
    try {
      await hideComment(row.id);
      message.success('评论已隐藏');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    } finally {
      setActingId(undefined);
    }
  };

  const columns: TableProps<CommentView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 90 },
    {
      title: '歌曲',
      dataIndex: 'songTitle',
      width: 160,
      render: (value: string, row) => value ?? `歌曲 ${row.songId}`,
    },
    { title: '用户', dataIndex: 'userId', width: 90 },
    {
      title: '评论正文',
      dataIndex: 'content',
      render: (value: string, row) => (
        <Typography.Paragraph
          style={{ marginBottom: 0, maxWidth: 420 }}
          ellipsis={{ rows: 2, expandable: true, symbol: '展开' }}
        >
          {row.parentId ? `回复 #${row.parentId}：${value}` : value}
        </Typography.Paragraph>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: CommentStatus) => (
        <Tag color={STATUS_COLORS[value] ?? 'default'}>
          {STATUS_OPTIONS.find((item) => item.value === value)?.label ?? value}
        </Tag>
      ),
    },
    { title: '评论时间', dataIndex: 'createTime', width: 170 },
    {
      title: '操作',
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
              通过
            </AuthButton>
          )}
          {row.status !== 'HIDDEN' && (
            <Popconfirm
              title="隐藏后用户将立即看不到该评论"
              okText="隐藏"
              cancelText="取消"
              onConfirm={() => handleHide(row)}
            >
              <AuthButton
                perm="sys:music:comment:audit"
                type="link"
                size="small"
                danger
                loading={actingId === row.id}
              >
                隐藏
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
      title="评论审核"
      columns={columns}
      request={pageCommentAudit}
      params={{ status, keyword }}
      onError={(error) => message.error(error instanceof Error ? error.message : '评论加载失败')}
      search={
        <SearchForm
          items={[
            {
              name: 'status',
              label: '状态',
              element: (
                <Select
                  allowClear
                  placeholder="全部状态"
                  style={{ minWidth: 140 }}
                  options={STATUS_OPTIONS}
                />
              ),
            },
            {
              name: 'keyword',
              label: '关键词',
              element: <Input allowClear placeholder="评论正文" />,
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
