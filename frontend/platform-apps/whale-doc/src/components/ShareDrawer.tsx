// 页面登录内分享抽屉（M3）：创建、复制链接、撤销

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Drawer,
  Empty,
  Input,
  List,
  Popconfirm,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { CopyOutlined, LinkOutlined, StopOutlined } from '@ant-design/icons';
import { shareApi, shareUrl } from '../api/share';
import type { DocPageShare } from '../api/types';
import { formatTime } from '../utils/docContent';

const { Text } = Typography;

/** 有效期选项 */
const EXPIRE_OPTIONS = [
  { value: 7, label: '7 天' },
  { value: 30, label: '30 天' },
  { value: 90, label: '90 天' },
  { value: 0, label: '不限期' },
];

/** 分享抽屉属性 */
interface ShareDrawerProps {
  pageId: number;
  open: boolean;
  onClose: () => void;
}

export default function ShareDrawer({ pageId, open, onClose }: ShareDrawerProps) {
  const { message } = App.useApp();
  const [shares, setShares] = useState<DocPageShare[]>([]);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [expireDays, setExpireDays] = useState(7);
  const [revoking, setRevoking] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setShares(await shareApi.list(pageId));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [pageId, message]);

  useEffect(() => {
    if (open) {
      void load();
    }
  }, [open, load]);

  const create = async () => {
    setCreating(true);
    try {
      await shareApi.create(pageId, { expireDays });
      message.success('分享链接已创建');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setCreating(false);
    }
  };

  const revoke = async (share: DocPageShare) => {
    setRevoking(share.id);
    try {
      await shareApi.revoke(pageId, share.id);
      message.success('分享链接已撤销');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setRevoking(null);
    }
  };

  const copy = async (share: DocPageShare) => {
    const url = shareUrl(share.sharePath);
    try {
      await navigator.clipboard.writeText(url);
      message.success('链接已复制');
    } catch {
      message.info(url);
    }
  };

  return (
    <Drawer title="分享页面" width={480} open={open} onClose={onClose} destroyOnHidden>
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Text type="secondary">链接仅限已登录的租户成员访问，默认只读；撤销后立即失效。</Text>
        <Space>
          <Select
            value={expireDays}
            options={EXPIRE_OPTIONS}
            onChange={setExpireDays}
            style={{ width: 140 }}
          />
          <Button type="primary" icon={<LinkOutlined />} loading={creating} onClick={() => void create()}>
            创建链接
          </Button>
        </Space>
        <Spin spinning={loading}>
          {shares.length === 0 ? (
            <Empty description="暂无分享链接" />
          ) : (
            <List
              dataSource={shares}
              renderItem={(share) => {
                const invalid = share.status !== 1 || share.expired;
                return (
                  <List.Item
                    actions={[
                      <Button
                        key="copy"
                        type="text"
                        icon={<CopyOutlined />}
                        disabled={invalid}
                        onClick={() => void copy(share)}
                      />,
                      <Popconfirm
                        key="revoke"
                        title="撤销该分享链接？"
                        okText="撤销"
                        onConfirm={() => void revoke(share)}
                      >
                        <Button
                          type="text"
                          danger
                          icon={<StopOutlined />}
                          disabled={invalid}
                          loading={revoking === share.id}
                        />
                      </Popconfirm>,
                    ]}
                  >
                    <List.Item.Meta
                      title={
                        <Space size={6}>
                          <Tag color={invalid ? 'default' : 'green'}>{invalid ? '已失效' : '有效'}</Tag>
                          <Text copyable={{ text: shareUrl(share.sharePath), tooltips: ['复制', '已复制'] }}>
                            {share.sharePath}
                          </Text>
                        </Space>
                      }
                      description={
                        <Text type="secondary">
                          {share.expiresAt ? `到期：${formatTime(share.expiresAt)}` : '不限期'}
                          {share.createTime ? ` · 创建于 ${formatTime(share.createTime)}` : ''}
                        </Text>
                      }
                    />
                  </List.Item>
                );
              }}
            />
          )}
        </Spin>
        <Input.TextArea value="登录内可读：未登录用户打开链接会先跳转登录。" disabled autoSize={{ minRows: 1 }} />
      </Space>
    </Drawer>
  );
}
