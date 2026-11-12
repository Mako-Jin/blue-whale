// 空间回收站：子树列表、恢复、彻底删除与清空（M2）

import { useCallback, useEffect, useState, type ReactNode } from 'react';
import {
  App,
  Button,
  Drawer,
  Empty,
  List,
  Pagination,
  Popconfirm,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { DeleteOutlined, UndoOutlined } from '@ant-design/icons';
import { recycleApi } from '../api/recycle';
import { hasPermission } from '../api/client';
import type { DocRecycleItem } from '../api/types';
import { formatTime } from '../utils/docContent';

const { Text } = Typography;

/** 回收站每页条数 */
const PAGE_SIZE = 10;

/** 回收站抽屉属性 */
interface RecycleBinProps {
  spaceId: number;
  open: boolean;
  onClose: () => void;
  /** 恢复或清除后刷新页面树 */
  onChanged: () => Promise<void>;
}

export default function RecycleBin({ spaceId, open, onClose, onChanged }: RecycleBinProps) {
  const { message } = App.useApp();
  const [records, setRecords] = useState<DocRecycleItem[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(false);
  const [actionId, setActionId] = useState<number | null>(null);

  const canRestore = hasPermission('sys:doc:page:edit');
  const canPurge = hasPermission('sys:doc:page:delete');

  const load = useCallback(
    async (pageNo: number) => {
      setLoading(true);
      try {
        const result = await recycleApi.page(spaceId, pageNo, PAGE_SIZE);
        setRecords(result.records);
        setTotal(Number(result.total));
        setCurrent(pageNo);
      } catch (error) {
        message.error((error as Error).message);
      } finally {
        setLoading(false);
      }
    },
    [spaceId, message],
  );

  useEffect(() => {
    if (open) {
      void load(1);
    }
  }, [open, load]);

  /** 当前页被清空后自动回到上一页 */
  const reloadAfterChange = useCallback(async () => {
    const nextPage = records.length === 1 && current > 1 ? current - 1 : current;
    await load(nextPage);
    await onChanged();
  }, [records.length, current, load, onChanged]);

  const restore = async (item: DocRecycleItem) => {
    setActionId(item.pageId);
    try {
      await recycleApi.restore(item.pageId);
      message.success(`「${item.title}」已恢复`);
      await reloadAfterChange();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setActionId(null);
    }
  };

  const purge = async (item: DocRecycleItem) => {
    setActionId(item.pageId);
    try {
      await recycleApi.purge(item.pageId);
      message.success(`「${item.title}」已彻底删除`);
      await reloadAfterChange();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setActionId(null);
    }
  };

  /** 构造行操作按钮 */
  const renderActions = (item: DocRecycleItem): ReactNode[] => {
    const actions: ReactNode[] = [];
    if (canRestore) {
      actions.push(
        <Button
          key="restore"
          type="link"
          size="small"
          icon={<UndoOutlined />}
          loading={actionId === item.pageId}
          onClick={() => void restore(item)}
        >
          恢复
        </Button>,
      );
    }
    if (canPurge) {
      actions.push(
        <Popconfirm
          key="purge"
          title="彻底删除该子树？"
          description="页面、版本、评论与附件记录将被物理删除，不可恢复。"
          okText="彻底删除"
          okButtonProps={{ danger: true }}
          onConfirm={() => void purge(item)}
        >
          <Button type="link" danger size="small" loading={actionId === item.pageId}>
            彻底删除
          </Button>
        </Popconfirm>,
      );
    }
    return actions;
  };

  const clearAll = async () => {
    setActionId(-1);
    try {
      await recycleApi.clear(spaceId);
      message.success('回收站已清空');
      await load(1);
      await onChanged();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setActionId(null);
    }
  };

  return (
    <Drawer
      title="回收站"
      width={560}
      open={open}
      onClose={onClose}
      extra={
        canPurge &&
        total > 0 && (
          <Popconfirm
            title="清空回收站？"
            description="将彻底删除空间内所有回收站页面及其版本、评论与附件记录，且不可恢复。"
            okText="彻底清空"
            okButtonProps={{ danger: true }}
            onConfirm={() => void clearAll()}
          >
            <Button danger size="small" loading={actionId === -1} icon={<DeleteOutlined />}>
              清空回收站
            </Button>
          </Popconfirm>
        )
      }
    >
      <Text type="secondary">
        回收站只展示删除时的子树根节点；恢复或彻底删除会作用于整棵子树，默认保留 30 天后自动清理。
      </Text>
      <Spin spinning={loading}>
        {records.length === 0 && !loading ? (
          <Empty description="回收站为空" style={{ padding: 48 }} />
        ) : (
          <List
            style={{ marginTop: 16 }}
            dataSource={records}
            renderItem={(item) => (
              <List.Item actions={renderActions(item)}>
                <List.Item.Meta
                  title={
                    <Space size={6} wrap>
                      <span>{item.pageType === 'FOLDER' ? '📁' : '📄'}</span>
                      <Text strong>{item.title}</Text>
                      {item.childCount > 0 && <Tag color="blue">含 {item.childCount} 个子页面</Tag>}
                    </Space>
                  }
                  description={
                    <Space size={12} wrap>
                      <Text type="secondary">
                        原位置：
                        {item.parentTitle ?? (item.parentId === 0 ? '根目录' : '父页面已删除')}
                      </Text>
                      <Text type="secondary">删除时间：{formatTime(item.deletedAt)}</Text>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Spin>
      {total > PAGE_SIZE && (
        <Pagination
          style={{ marginTop: 16, textAlign: 'right' }}
          current={current}
          pageSize={PAGE_SIZE}
          total={total}
          showSizeChanger={false}
          onChange={(pageNo) => void load(pageNo)}
        />
      )}
    </Drawer>
  );
}
