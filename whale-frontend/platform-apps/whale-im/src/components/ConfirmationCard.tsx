// 平台写操作确认卡片：按卡片 permission + sys:im:chat 双判定，过期后禁用

import { useState } from 'react';
import { App, Button, Space, Tag, Typography } from 'antd';
import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { usePermission } from '@bluewhale/whale-ui';
import { imApi, type ConfirmationCardData } from '../api/im';
import { formatTime } from '../utils/format';

const { Text } = Typography;

type SubmitState = 'pending' | 'approved' | 'rejected';

interface ConfirmationCardProps {
  card: ConfirmationCardData;
  runId?: string;
}

/** 判断过期时间是否已过；无法解析时不拦截 */
function isExpired(expiresAt?: string): boolean {
  if (!expiresAt) {
    return false;
  }
  const time = new Date(expiresAt).getTime();
  return Number.isNaN(time) ? false : time < Date.now();
}

export default function ConfirmationCard({ card, runId }: ConfirmationCardProps) {
  const { message } = App.useApp();
  const { has } = usePermission();
  const [state, setState] = useState<SubmitState>('pending');
  const [submitting, setSubmitting] = useState(false);

  const expired = isExpired(card.expiresAt);
  const allowed = has('sys:im:chat') && (card.permission ? has(card.permission) : true);
  const effectiveRunId = runId ?? card.runId;
  const finished = state !== 'pending';
  const disabled = submitting || finished || expired || !allowed || !effectiveRunId;

  const submit = async (approved: boolean) => {
    if (!effectiveRunId) {
      return;
    }
    setSubmitting(true);
    try {
      await imApi.confirmRun(effectiveRunId, approved);
      setState(approved ? 'approved' : 'rejected');
      message.success(approved ? '已提交执行' : '已拒绝执行');
    } catch (error) {
      message.error('确认失败，令牌可能已过期，请重新发起');
      void error;
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="im-card">
      <Space size={6} wrap>
        <Tag color="orange">待确认</Tag>
        <Text strong>{card.operation || '平台操作'}</Text>
      </Space>
      <div className="im-card-summary">{card.summary || '（无参数摘要）'}</div>
      <Space size={8} wrap>
        <Text type="secondary" style={{ fontSize: 12 }}>
          所需权限：{card.permission || '—'}
        </Text>
        <Text type="secondary" style={{ fontSize: 12 }}>
          过期时间：{formatTime(card.expiresAt) || '—'}
        </Text>
      </Space>
      {!allowed && (
        <Text type="secondary" style={{ fontSize: 12 }}>
          你没有执行该操作的权限，按钮已禁用。
        </Text>
      )}
      {!effectiveRunId && (
        <Text type="secondary" style={{ fontSize: 12 }}>
          该确认已失效，请在会话中重新发起操作。
        </Text>
      )}
      {expired && (
        <Text type="secondary" style={{ fontSize: 12 }}>
          确认已过期，请重新发起操作。
        </Text>
      )}
      <Space>
        <Button
          type="primary"
          size="small"
          icon={<CheckOutlined />}
          loading={submitting}
          disabled={disabled}
          onClick={() => {
            void submit(true);
          }}
        >
          确认执行
        </Button>
        <Button
          danger
          size="small"
          icon={<CloseOutlined />}
          disabled={disabled}
          onClick={() => {
            void submit(false);
          }}
        >
          拒绝
        </Button>
        {finished && (
          <Tag color={state === 'approved' ? 'green' : 'default'}>
            {state === 'approved' ? '已提交' : '已拒绝'}
          </Tag>
        )}
      </Space>
    </div>
  );
}
