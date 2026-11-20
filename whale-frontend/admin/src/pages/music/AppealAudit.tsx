// 音乐运营 - 版权申诉复核：按状态 / 歌曲过滤，通过（版权恢复仍保持下架）或驳回
// 权限码：sys:music:appeal:audit；复核仅一次，重复复核返回 40902

import { App, Input, Modal, Select, Tag, Typography, type TableProps } from 'antd';
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
import { useI18n } from '../../i18n';

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
  const { t } = useI18n();
  const tableRef = useRef<ProTableRef>(null);

  const [status, setStatus] = useState<AppealStatus | undefined>(undefined);
  const [songId, setSongId] = useState<string | undefined>(undefined);
  const [reviewing, setReviewing] = useState<AppealView | undefined>(undefined);
  const [reviewAction, setReviewAction] = useState<ReviewAction>('approve');
  const [opinion, setOpinion] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const statusOptions: { value: AppealStatus; label: string }[] = [
    { value: 'PENDING', label: t('music.appeal.status.pending') },
    { value: 'APPROVED', label: t('music.appeal.status.approved') },
    { value: 'REJECTED', label: t('music.appeal.status.rejected') },
  ];

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
        message.success(t('music.appeal.msg.approved'));
      } else {
        await rejectAppeal(reviewing.id, opinion || undefined);
        message.success(t('music.appeal.msg.rejected'));
      }
      closeReview();
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('music.appeal.msg.failed'));
    } finally {
      setSubmitting(false);
    }
  };

  const columns: TableProps<AppealView>['columns'] = [
    { title: 'ID', dataIndex: 'id', width: 90 },
    {
      title: t('music.appeal.col.song'),
      dataIndex: 'songTitle',
      width: 160,
      render: (value: string, row) =>
        value ?? t('music.appeal.col.songFallback', { id: row.songId }),
    },
    { title: t('music.appeal.col.applicant'), dataIndex: 'applicantId', width: 100 },
    {
      title: t('music.appeal.col.reason'),
      dataIndex: 'reason',
      render: (value: string) => (
        <Typography.Paragraph
          style={{ marginBottom: 0, maxWidth: 420 }}
          ellipsis={{ rows: 2, expandable: true, symbol: t('music.common.expand') }}
        >
          {value}
        </Typography.Paragraph>
      ),
    },
    {
      title: t('music.appeal.col.state'),
      dataIndex: 'status',
      width: 100,
      render: (value: AppealStatus) => (
        <Tag color={STATUS_COLORS[value] ?? 'default'}>
          {statusOptions.find((item) => item.value === value)?.label ?? value}
        </Tag>
      ),
    },
    {
      title: t('music.appeal.col.reviewOpinion'),
      dataIndex: 'reviewOpinion',
      width: 200,
      render: (value: string) => value ?? '-',
    },
    { title: t('music.appeal.col.createTime'), dataIndex: 'createTime', width: 170 },
    {
      title: t('music.appeal.col.reviewedAt'),
      dataIndex: 'reviewedAt',
      width: 170,
      render: (value: string) => value ?? '-',
    },
    {
      title: t('music.common.action'),
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
              {t('music.appeal.action.approve')}
            </AuthButton>,
            <AuthButton
              key="reject"
              perm="sys:music:appeal:audit"
              type="link"
              size="small"
              danger
              onClick={() => openReview(row, 'reject')}
            >
              {t('music.appeal.action.reject')}
            </AuthButton>,
          ]
        ) : (
          <Typography.Text type="secondary">{t('music.appeal.action.reviewed')}</Typography.Text>
        ),
    },
  ];

  return (
    <>
      <ProTable<AppealView, AppealQuery>
        ref={tableRef}
        rowKey="id"
        title={t('music.appeal.title')}
        columns={columns}
        request={pageAppeals}
        params={{ status, songId }}
        onError={(error) =>
          message.error(
            error instanceof Error ? error.message : t('music.appeal.msg.listLoadFailed'),
          )
        }
        search={
          <SearchForm
            items={[
              {
                name: 'status',
                label: t('music.appeal.search.state'),
                element: (
                  <Select
                    allowClear
                    placeholder={t('music.appeal.search.allStates')}
                    style={{ minWidth: 140 }}
                    options={statusOptions}
                  />
                ),
              },
              {
                name: 'songId',
                label: t('music.appeal.search.songId'),
                element: (
                  <Input
                    allowClear
                    placeholder={t('music.appeal.search.songIdPlaceholder')}
                    style={{ minWidth: 140 }}
                  />
                ),
              },
            ]}
            onSearch={(values) => {
              setStatus((values.status as AppealStatus) || undefined);
              setSongId((values.songId as string) || undefined);
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
        title={
          reviewAction === 'approve'
            ? t('music.appeal.modal.approveTitle')
            : t('music.appeal.modal.rejectTitle')
        }
        okText={
          reviewAction === 'approve'
            ? t('music.appeal.modal.approveOk')
            : t('music.appeal.modal.rejectOk')
        }
        okButtonProps={{ danger: reviewAction === 'reject', loading: submitting }}
        cancelText={t('music.common.cancel')}
        onOk={submitReview}
        onCancel={closeReview}
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary">
          {reviewAction === 'approve'
            ? t('music.appeal.modal.approveDesc')
            : t('music.appeal.modal.rejectDesc')}
        </Typography.Paragraph>
        <Input.TextArea
          rows={3}
          maxLength={500}
          showCount
          value={opinion}
          placeholder={t('music.appeal.modal.opinionPlaceholder')}
          onChange={(event) => setOpinion(event.target.value)}
        />
      </Modal>
    </>
  );
}
