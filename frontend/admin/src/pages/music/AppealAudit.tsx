// 音乐运营 - 版权申诉复核：按状态 / 歌曲过滤，通过（版权恢复仍保持下架）或驳回
// 权限码：sys:music:appeal:audit；复核仅一次，重复复核返回 40902

import { App, Input, InputNumber, Modal, Select, Tag, Typography, type TableProps } from 'antd';
import { useRef, useState } from 'react';
import { AuthButton, ProTable, SearchForm, type ProTableRef } from '@bluewhale/whale-ui';
import {
  approveAppeal,
  pageAppeals,
  rejectAppeal,
  type AppealQuery,
  type AppealStatus,
  type AppealView,
} from '../../api/music';

const STATUS_OPTIONS: { value: AppealStatus; label: string }[] = [
  { value: 'PENDING', label: '待复核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
];

/** 状态标签颜色 */
const STATUS_COLORS: Record<AppealStatus, string> = {
  PENDING: 'orange',
  APPROVED: 'green',
  REJECTED: 'red',
};

/** 待复核的复核动作（通过 / 驳回） */
type ReviewAction = 'approve' | 'reject';

export default function AppealAudit() {
  const { message } = App.useApp();
  const tableRef = useRef<ProTableRef>(null);

  const [status, setStatus] = useState<AppealStatus | undefined>(undefined);
  const [songId, setSongId] = useState<number | undefined>(undefined);
  const [reviewing, setReviewing] = useState<AppealView | undefined>(undefined);
  const [reviewAction, setReviewAction] = useState<ReviewAction>('approve');
  const [opinion, setOpinion] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const openReview = (row: AppealView, action: ReviewAction) => {
    setReviewing(row);
    setReviewAction(action);
    setOpinion('');
  };

  const closeReview = () => {
    setReviewing(undefined);
    setOpinion('');
  };

  const submitReview = async () => {
    if (!reviewing) {
      return;
    }
    setSubmitting(true);
    try {
      if (reviewAction === 'approve') {
        await approveAppeal(reviewing.id, opinion || undefined);
        message.success('申诉已通过：版权恢复为有效，作品仍保持下架，请按需重新上架');
      } else {
        await rejectAppeal(reviewing.id, opinion || undefined);
        message.success('申诉已驳回：作品保持版权下架');
      }
      closeReview();
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '复核失败');
    } finally {
      setSubmitting(false);
    }
  };

  const columns: TableProps<AppealView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 90 },
    {
      title: '歌曲',
      dataIndex: 'songTitle',
      width: 160,
      render: (value: string, row) => value ?? `歌曲 ${row.songId}`,
    },
    { title: '申诉人', dataIndex: 'applicantId', width: 100 },
    {
      title: '申诉理由',
      dataIndex: 'reason',
      render: (value: string) => (
        <Typography.Paragraph
          style={{ marginBottom: 0, maxWidth: 420 }}
          ellipsis={{ rows: 2, expandable: true, symbol: '展开' }}
        >
          {value}
        </Typography.Paragraph>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: AppealStatus) => (
        <Tag color={STATUS_COLORS[value] ?? 'default'}>
          {STATUS_OPTIONS.find((item) => item.value === value)?.label ?? value}
        </Tag>
      ),
    },
    {
      title: '复核意见',
      dataIndex: 'reviewOpinion',
      width: 200,
      render: (value: string) => value ?? '-',
    },
    { title: '申诉时间', dataIndex: 'createTime', width: 170 },
    { title: '复核时间', dataIndex: 'reviewedAt', width: 170, render: (value: string) => value ?? '-' },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      render: (_value, row) =>
        row.status === 'PENDING' ? (
          [
            <AuthButton
              key="approve"
              perm="sys:music:appeal:audit"
              type="link"
              size="small"
              onClick={() => openReview(row, 'approve')}
            >
              通过
            </AuthButton>,
            <AuthButton
              key="reject"
              perm="sys:music:appeal:audit"
              type="link"
              size="small"
              danger
              onClick={() => openReview(row, 'reject')}
            >
              驳回
            </AuthButton>,
          ]
        ) : (
          <Typography.Text type="secondary">已复核</Typography.Text>
        ),
    },
  ];

  return (
    <>
      <ProTable<AppealView, AppealQuery>
        ref={tableRef}
        rowKey="id"
        title="版权申诉"
        columns={columns}
        request={pageAppeals}
        params={{ status, songId }}
        onError={(error) => message.error(error instanceof Error ? error.message : '申诉加载失败')}
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
                name: 'songId',
                label: '歌曲 ID',
                element: <InputNumber min={1} placeholder="歌曲 ID" style={{ minWidth: 140 }} />,
              },
            ]}
            onSearch={(values) => {
              setStatus((values.status as AppealStatus) || undefined);
              setSongId((values.songId as number) || undefined);
            }}
            onReset={() => {
              setStatus(undefined);
              setSongId(undefined);
            }}
          />
        }
      />
      <Modal
        open={!!reviewing}
        title={reviewAction === 'approve' ? '通过申诉' : '驳回申诉'}
        okText={reviewAction === 'approve' ? '通过' : '驳回'}
        okButtonProps={{ danger: reviewAction === 'reject', loading: submitting }}
        cancelText="取消"
        onOk={submitReview}
        onCancel={closeReview}
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          {reviewAction === 'approve'
            ? '通过后：作品版权恢复为「有效」，但发布状态保持「已下架」且不可播放，需运营重新上架才会复播。'
            : '驳回后：作品保持「版权下架」与「已下架」状态，申诉记录标记为已驳回。'}
        </Typography.Paragraph>
        <Input.TextArea
          rows={3}
          maxLength={500}
          showCount
          value={opinion}
          placeholder="复核意见（可选，最多 500 字）"
          onChange={(event) => setOpinion(event.target.value)}
        />
      </Modal>
    </>
  );
}
