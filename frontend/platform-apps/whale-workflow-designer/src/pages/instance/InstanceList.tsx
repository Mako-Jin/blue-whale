// 流程实例监控（F5）：分页列表（按 status / definitionCode 过滤）+ 详情抽屉（基本信息 + 任务流转轨迹）
// + 撤销运行中实例（status=0 才显示，Popconfirm 确认后调用 instanceCancel）。
// 契约层 ../../api/workflow 只读不改；雪花 ID 全程 string（禁止 Number(id)）。

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  App,
  Button,
  Card,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  type TableColumnsType,
} from 'antd';
import {
  workflowApi,
  type Id,
  type InstanceQuery,
  type ProcessInstance,
} from '../../api/workflow';
import InstanceDetailDrawer from './InstanceDetailDrawer';
import { InstanceStatusTag } from './statusTag';

/** 状态下拉选项：0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销 */
const STATUS_OPTIONS = [
  { value: 0, label: '运行中' },
  { value: 1, label: '已通过' },
  { value: 2, label: '已驳回' },
  { value: 3, label: '已撤销' },
];

/** 查询表单字段 */
interface SearchValues {
  status?: number;
  definitionCode?: string;
}

/** 从 definitionJson 抽取「节点键 → 节点名」映射（无 name / 解析失败均返回空对象） */
function extractNodeNames(definitionJson?: string | null): Record<string, string> {
  if (!definitionJson) {
    return {};
  }
  try {
    const parsed = JSON.parse(definitionJson) as {
      nodes?: Array<{ key?: string; name?: string | null }>;
    };
    const map: Record<string, string> = {};
    (parsed.nodes ?? []).forEach((node) => {
      if (node.key && node.name) {
        map[node.key] = node.name;
      }
    });
    return map;
  } catch {
    return {};
  }
}

export default function InstanceList() {
  const { message } = App.useApp();
  const [searchForm] = Form.useForm<SearchValues>();

  const [rows, setRows] = useState<ProcessInstance[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<InstanceQuery>({ current: 1, size: 10 });

  // 详情抽屉：detailId 非空即打开
  const [detailId, setDetailId] = useState<Id | null>(null);

  // 节点名映射缓存 definitionId → (nodeKey → nodeName)，失败静默回退显示 key
  const nodeNameCacheRef = useRef<Record<string, Record<string, string>>>({});
  const [nodeNames, setNodeNames] = useState<Record<string, Record<string, string>>>({});

  /** 对当前页出现且未缓存的 definitionId 逐个拉定义并抽取节点名（失败不打断列表/不报错） */
  const loadNodeNames = useCallback(async (records: ProcessInstance[]) => {
    const missing = Array.from(new Set(records.map((row) => row.definitionId))).filter(
      (id) => !(id in nodeNameCacheRef.current),
    );
    if (missing.length === 0) {
      return;
    }
    const fetched = await Promise.all(
      missing.map(async (id) => {
        try {
          const definition = await workflowApi.definitionDetail(id);
          return [id, extractNodeNames(definition.definitionJson)] as const;
        } catch {
          return [id, {}] as const;
        }
      }),
    );
    fetched.forEach(([id, map]) => {
      nodeNameCacheRef.current[id] = map;
    });
    setNodeNames({ ...nodeNameCacheRef.current });
  }, []);

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const result = await workflowApi.instancePage(query);
      const records = result.records ?? [];
      setRows(records);
      setTotal(result.total ?? 0);
      void loadNodeNames(records);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [query, message, loadNodeNames]);

  useEffect(() => {
    void fetchList();
  }, [fetchList]);

  /** 查询：写入条件并回到第一页 */
  const handleSearch = (values: SearchValues) => {
    setQuery((prev) => ({
      ...prev,
      current: 1,
      status: values.status,
      definitionCode: values.definitionCode?.trim() || undefined,
    }));
  };

  /** 重置：清空过滤条件（保留每页条数）并回到第一页 */
  const handleReset = () => {
    searchForm.resetFields();
    setQuery((prev) => ({ current: 1, size: prev.size }));
  };

  /** 分页变化（每页条数改变时回到第一页） */
  const handleTableChange = (pagination: { current?: number; pageSize?: number }) => {
    setQuery((prev) => ({
      ...prev,
      current: pagination.current ?? 1,
      size: pagination.pageSize ?? prev.size ?? 10,
    }));
  };

  /** 撤销实例：仅运行中（status=0）调用，成功后刷新列表 */
  const handleCancel = async (row: ProcessInstance) => {
    try {
      await workflowApi.instanceCancel(row.id);
      message.success('已撤销');
      void fetchList();
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  /** 当前节点展示：优先映射节点名（拿不到则直接显示 key） */
  const renderCurrentNode = (record: ProcessInstance) => {
    const key = record.currentNodeKey;
    if (!key) {
      return '-';
    }
    const name = nodeNames[record.definitionId]?.[key];
    return name ? `${name}（${key}）` : key;
  };

  const columns: TableColumnsType<ProcessInstance> = [
    { title: '标题', dataIndex: 'title', width: 200, ellipsis: true },
    { title: '流程编码', dataIndex: 'definitionCode', width: 160 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: number) => <InstanceStatusTag status={value} />,
    },
    {
      title: '当前节点',
      key: 'currentNodeKey',
      width: 180,
      ellipsis: true,
      render: (_, record) => renderCurrentNode(record),
    },
    { title: '发起人', dataIndex: 'initiator', width: 120, render: (value?: string) => value || '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 180, render: (value?: string) => value || '-' },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => setDetailId(record.id)}>
            详情
          </Button>
          {record.status === 0 ? (
            <Popconfirm
              title="确认撤销该流程实例？"
              okText="撤销"
              cancelText="取消"
              onConfirm={() => void handleCancel(record)}
            >
              <Button type="link" size="small" danger>
                撤销
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={12} style={{ width: '100%' }}>
      <Card size="small">
        <Form<SearchValues> form={searchForm} layout="inline" onFinish={handleSearch}>
          <Form.Item name="status" label="状态">
            <Select allowClear placeholder="全部" options={STATUS_OPTIONS} style={{ width: 130 }} />
          </Form.Item>
          <Form.Item name="definitionCode" label="流程编码">
            <Input allowClear placeholder="精确匹配" style={{ width: 200 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
              <Button onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card
        size="small"
        title="流程实例列表"
        extra={
          <Button onClick={() => void fetchList()} loading={loading}>
            刷新
          </Button>
        }
      >
        <Table<ProcessInstance>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          onChange={handleTableChange}
          pagination={{
            current: query.current ?? 1,
            pageSize: query.size ?? 10,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
          }}
        />
      </Card>

      <InstanceDetailDrawer
        open={detailId !== null}
        instanceId={detailId}
        onClose={() => setDetailId(null)}
      />
    </Space>
  );
}
