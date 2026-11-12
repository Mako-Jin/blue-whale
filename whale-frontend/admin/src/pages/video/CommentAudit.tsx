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
  const [actingId, setActingId] = useState<string | undefined>(undefined);

  const handleApprove = async (row: CommentAuditView) => {
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

  const handleHide = async (row: CommentAuditView) => {
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

  const columns: TableProps<CommentAuditView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 90 },
    {
      title: '内容 ID',
      dataIndex: 'contentId',
      width: 110,
      render: (value?: string) => value ?? '-',
    },
    { title: '用户', dataIndex: 'userId', width: 100, render: (value?: string) => value ?? '-' },
    {
      title: '评论正文',
      dataIndex: 'content',
      render: (value: string, row) => (
        <Typography.Paragraph
          style={{ marginBottom: 0, maxWidth: 460 }}
          ellipsis={{ rows: 2, expandable: true, symbol: '展开' }}
        >
          {/* parentId 运行时为字符串，根评论为 '0'（非空串，需显式判断） */}
          {row.parentId && row.parentId !== '0' ? `回复 #${row.parentId}：${value}` : value}
        </Typography.Paragraph>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value?: CommentStatus) => (
        <Tag color={value ? STATUS_COLORS[value] : 'default'}>
          {STATUS_OPTIONS.find((item) => item.value === value)?.label ?? '未知'}
        </Tag>
      ),
    },
    {
      title: '点赞',
      dataIndex: 'likeCount',
      width: 80,
      render: (value?: number) => value ?? 0,
    },
    { title: '评论时间', dataIndex: 'createTime', width: 180, render: (value?: string) => value ?? '-' },
    {
      title: '操作',
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
              通过
            </AuthButton>
          ) : null}
          {row.status !== 'HIDDEN' ? (
            <Popconfirm
              title="隐藏后用户将立即看不到该评论"
              okText="隐藏"
              cancelText="取消"
              onConfirm={() => handleHide(row)}
            >
              <AuthButton
                perm="sys:video:comment:audit"
                type="link"
                size="small"
                danger
                loading={actingId === row.id}
              >
                隐藏
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
      title="评论审核"
      columns={columns}
      request={pageAuditComments}
      params={{ status }}
      onError={(error) => message.error(error instanceof Error ? error.message : '评论加载失败')}
      search={
        <SearchForm
          items={[
            {
              name: 'status',
              label: '状态',
              element: <Select allowClear placeholder="全部状态" style={{ minWidth: 140 }} options={STATUS_OPTIONS} />,
            },
          ]}
          onSearch={(values) => setStatus((values.status as CommentStatus) || undefined)}
          onReset={() => setStatus(undefined)}
        />
      }
    />
  );
}
