// PDM 变更追溯页面：按业务/对象分页查询，按对象查看时间线（只读）
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Empty,
  InputNumber,
  Select,
  Space,
  Table,
  Tag,
  Timeline,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  getChgTraceTimeline,
  pageChgTraces,
  type ChgTrace,
  type ChgTraceObjectType,
} from '../../api/changeTrace';

/** 对象类型选项 */
const OBJECT_TYPES: ChgTraceObjectType[] = ['PART', 'BOM', 'DOCUMENT'];

/**
 * 变更追溯页
 */
export default function ChangeTracePage() {
  const [rows, setRows] = useState<ChgTrace[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [bizType, setBizType] = useState<string | undefined>();
  const [objectType, setObjectType] = useState<ChgTraceObjectType | undefined>();
  const [objectId, setObjectId] = useState<number | null>(null);
  const [timeline, setTimeline] = useState<ChgTrace[]>([]);
  const [timelineLoading, setTimelineLoading] = useState(false);

  /** 加载追溯分页 */
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageChgTraces({
        current,
        size,
        bizType,
        objectType,
        objectId: objectId ?? undefined,
      });
      setRows(result.records ?? []);
      setTotal(Number(result.total ?? 0));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '追溯加载失败');
    } finally {
      setLoading(false);
    }
  }, [bizType, current, objectId, objectType, size]);

  useEffect(() => {
    void load();
  }, [load]);

  /** 查询对象时间线 */
  const loadTimeline = async () => {
    if (!objectType || !objectId) {
      message.warning('请选择对象类型并填写对象ID');
      return;
    }
    setTimelineLoading(true);
    try {
      setTimeline(await getChgTraceTimeline(objectType, objectId));
    } catch (error) {
      setTimeline([]);
      message.error(error instanceof Error ? error.message : '时间线加载失败');
    } finally {
      setTimelineLoading(false);
    }
  };

  const columns: ColumnsType<ChgTrace> = [
    {
      title: '时间',
      dataIndex: 'traceTime',
      width: 180,
      render: (value: string) => value?.replace('T', ' '),
    },
    {
      title: '业务',
      dataIndex: 'bizType',
      width: 80,
      render: (value: string) => <Tag>{value}</Tag>,
    },
    { title: '业务编号', dataIndex: 'bizNo', width: 180 },
    { title: '对象', dataIndex: 'objectType', width: 100 },
    { title: '对象编码', dataIndex: 'objectCode', width: 160 },
    { title: '动作', dataIndex: 'action', width: 140 },
    {
      title: '版本变化',
      width: 180,
      render: (_, row) =>
        row.fromVersion || row.toVersion
          ? `${row.fromVersion ?? '-'} → ${row.toVersion ?? '-'}`
          : '-',
    },
    { title: '说明', dataIndex: 'content' },
  ];

  return (
    <div style={{ padding: 16 }}>
      <Card title="追溯查询" style={{ marginBottom: 16 }}>
        <Space wrap>
          <Select
            allowClear
            placeholder="业务类型"
            style={{ width: 120 }}
            value={bizType}
            onChange={setBizType}
            options={['ECR', 'ECO', 'ECN'].map((value) => ({ value, label: value }))}
          />
          <Select
            allowClear
            placeholder="对象类型"
            style={{ width: 140 }}
            value={objectType}
            onChange={setObjectType}
            options={OBJECT_TYPES.map((value) => ({ value, label: value }))}
          />
          <InputNumber
            placeholder="对象ID"
            value={objectId ?? undefined}
            onChange={(value) => setObjectId(value ?? null)}
            style={{ width: 160 }}
          />
          <Button type="primary" onClick={() => void load()}>
            查询
          </Button>
        </Space>
        <Table
          style={{ marginTop: 16 }}
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          pagination={{
            current,
            pageSize: size,
            total,
            showSizeChanger: true,
            onChange: (page, pageSize) => {
              setCurrent(page);
              setSize(pageSize);
            },
          }}
        />
      </Card>

      <Card
        title="对象时间线"
        extra={
          <Space>
            <Select
              placeholder="对象类型"
              style={{ width: 140 }}
              value={objectType}
              onChange={setObjectType}
              options={OBJECT_TYPES.map((value) => ({ value, label: value }))}
            />
            <InputNumber
              placeholder="对象ID"
              value={objectId ?? undefined}
              onChange={(value) => setObjectId(value ?? null)}
            />
            <Button type="primary" loading={timelineLoading} onClick={() => void loadTimeline()}>
              查看
            </Button>
          </Space>
        }
      >
        {timeline.length === 0 ? (
          <Empty description="暂无追溯记录" />
        ) : (
          <Timeline
            items={timeline.map((item) => ({
              color:
                item.action === 'OBSOLETE' ? 'red' : item.action === 'EFFECTIVE' ? 'green' : 'blue',
              children: (
                <div>
                  <div>
                    <Tag>{item.bizType}</Tag>
                    <strong>{item.action}</strong>
                    <span style={{ marginLeft: 8, color: '#888' }}>
                      {item.traceTime?.replace('T', ' ')}
                    </span>
                  </div>
                  <div>
                    {item.bizNo}
                    {item.fromVersion || item.toVersion
                      ? ` ${item.fromVersion ?? '-'} → ${item.toVersion ?? '-'}`
                      : ''}
                  </div>
                  {item.content && <div style={{ color: '#555' }}>{item.content}</div>}
                </div>
              ),
            }))}
          />
        )}
      </Card>
    </div>
  );
}
