// 党建园地（M-P2）：只展示「已发布」且当前用户可见范围命中的党建内容。
//
// 打开详情即登记阅读：阅读数按人数去重，重复打开只刷新时间。
// 未发布内容在这里不会出现——园地接口只查 PUBLISHED，未审稿只对作者与审核人可见。

import {
  Button,
  Card,
  Descriptions,
  Input,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { getContentPortal, pageContentPortal, readContent, type ContentView } from '../api/party';

/** 每页条数 */
const PAGE_SIZE = 10;

/** 党建园地阅读页 */
export default function ContentPortalPage() {
  const [rows, setRows] = useState<ContentView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<ContentView | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageContentPortal({
        keyword: keyword || undefined,
        current,
        size: PAGE_SIZE,
      });
      setRows(page.records);
      setTotal(Number(page.total));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '党建园地加载失败');
    } finally {
      setLoading(false);
    }
  }, [current, keyword]);

  useEffect(() => {
    void load();
  }, [load]);

  const openDetail = async (row: ContentView) => {
    try {
      const content = await getContentPortal(row.id);
      const read = await readContent(row.id);
      setDetail({ ...content, readCount: read.readCount });
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '内容加载失败');
    }
  };

  const columns: ColumnsType<ContentView> = [
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      render: (value: string, row) => (
        <Button type="link" style={{ paddingInline: 0 }} onClick={() => void openDetail(row)}>
          {value}
        </Button>
      ),
    },
    { title: '分类', dataIndex: 'category', width: 120 },
    {
      title: '范围',
      dataIndex: 'visibilityLabel',
      width: 100,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    {
      title: '阅读人数',
      dataIndex: 'readCount',
      width: 100,
      render: (value: number) => `${value ?? 0} 人`,
    },
    {
      title: '发布时间',
      dataIndex: 'publishedAt',
      width: 170,
      render: (value) => value ?? '—',
    },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_value, row) => (
        <Button type="link" size="small" onClick={() => void openDetail(row)}>
          阅读
        </Button>
      ),
    },
  ];

  return (
    <Card
      title="党建园地"
      size="small"
      extra={
        <Input.Search
          allowClear
          placeholder="标题或分类关键字"
          style={{ width: 240 }}
          onSearch={(value) => {
            setCurrent(1);
            setKeyword(value.trim());
          }}
        />
      }
    >
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 900 }}
        locale={{ emptyText: '暂无已发布且对您可见的党建内容' }}
        pagination={{
          current,
          pageSize: PAGE_SIZE,
          total,
          showSizeChanger: false,
          onChange: setCurrent,
        }}
      />

      <Modal
        title={detail?.title}
        open={detail !== null}
        footer={
          <Space>
            <Button onClick={() => setDetail(null)}>关闭</Button>
          </Space>
        }
        width={760}
        onCancel={() => setDetail(null)}
      >
        {detail ? (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Descriptions size="small" column={2}>
              <Descriptions.Item label="分类">{detail.category}</Descriptions.Item>
              <Descriptions.Item label="范围">{detail.visibilityLabel}</Descriptions.Item>
              <Descriptions.Item label="作者">{detail.authorName ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="阅读人数">{detail.readCount ?? 0} 人</Descriptions.Item>
              <Descriptions.Item label="发布时间">{detail.publishedAt ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="附件">
                {(detail.attachmentFileIds ?? []).length > 0
                  ? (detail.attachmentFileIds ?? []).map((id) => `#${id}`).join('、')
                  : '—'}
              </Descriptions.Item>
            </Descriptions>
            {detail.summary ? (
              <Typography.Text type="secondary">{detail.summary}</Typography.Text>
            ) : null}
            <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
              {detail.body || '（暂无正文）'}
            </Typography.Paragraph>
          </Space>
        ) : null}
      </Modal>
    </Card>
  );
}
