// 我的收藏：分页展示收藏页面，支持取消收藏与跳转

import { useCallback, useEffect, useState } from 'react';
import { App, Button, Card, Empty, List, Pagination, Space, Spin, Tag, Typography } from 'antd';
import { FileTextOutlined, StarFilled } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { starApi } from '../api/star';
import type { DocPageStatus, DocStarItem } from '../api/types';
import { formatTime } from '../utils/docContent';

const { Text } = Typography;

/** 每页条数 */
const PAGE_SIZE = 10;

/** 状态文案 */
const STATUS_LABEL: Record<DocPageStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档',
  PENDING_APPROVAL: '待审批',
};

/** 状态颜色 */
const STATUS_COLOR: Record<DocPageStatus, string> = {
  DRAFT: 'gold',
  PUBLISHED: 'green',
  ARCHIVED: 'default',
  PENDING_APPROVAL: 'blue',
};

export default function FavoritesPage() {
  const { message } = App.useApp();
  const [items, setItems] = useState<DocStarItem[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await starApi.page(current, PAGE_SIZE);
      setItems(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [current, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const unstar = async (item: DocStarItem) => {
    try {
      const result = await starApi.toggle(item.pageId);
      if (!result.starred) {
        message.success('已取消收藏');
      }
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  return (
    <Card>
      <Space align="center" style={{ marginBottom: 16 }}>
        <StarFilled style={{ color: '#faad14' }} />
        <Typography.Title level={5} style={{ margin: 0 }}>
          我的收藏
        </Typography.Title>
        <Text type="secondary">共 {total} 个页面</Text>
      </Space>

      <Spin spinning={loading}>
        <List
          dataSource={items}
          locale={{ emptyText: <Empty description="还没有收藏页面，在编辑器右上角点击星标即可收藏" /> }}
          renderItem={(item) => (
            <List.Item
              className="doc-result-item"
              actions={[
                <Button key="unstar" type="link" size="small" onClick={() => void unstar(item)}>
                  取消收藏
                </Button>,
              ]}
            >
              <List.Item.Meta
                avatar={<FileTextOutlined />}
                title={
                  <Space size={8} wrap>
                    <Link to={`/space/${item.spaceId}?pageId=${item.pageId}`}>{item.title}</Link>
                    <Tag color={STATUS_COLOR[item.status]}>{STATUS_LABEL[item.status]}</Tag>
                    <Text type="secondary">{item.spaceName}</Text>
                  </Space>
                }
                description={
                  <Text type="secondary">
                    收藏于 {formatTime(item.starredAt)} · 页面更新于 {formatTime(item.updateTime)} · /{item.slug}
                  </Text>
                }
              />
            </List.Item>
          )}
        />
      </Spin>

      {total > PAGE_SIZE && (
        <div style={{ marginTop: 16, textAlign: 'right' }}>
          <Pagination
            current={current}
            pageSize={PAGE_SIZE}
            total={total}
            showSizeChanger={false}
            onChange={setCurrent}
          />
        </div>
      )}
    </Card>
  );
}
