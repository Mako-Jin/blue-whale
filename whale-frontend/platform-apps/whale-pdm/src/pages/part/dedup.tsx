// 零件去重：归一化名称+材质+规格候选组，人工确认源件与保留件后合并
import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Select, Space, Table, Tag, message } from 'antd';
import type { TableProps } from 'antd';
import {
  listDedupCandidates,
  mergeParts,
  type PartDedupGroup,
  type PartDedupItem,
} from '../../api/part';
import { hasPermission } from '../../utils/permission';

interface MergeForm {
  sourceId: string;
  targetId: string;
  remark?: string;
}

/**
 * 候选组内零件明细表头
 */
const ITEM_COLUMNS: TableProps<PartDedupItem>['columns'] = [
  { title: '零件编码', dataIndex: 'code', width: 160 },
  { title: '零件名称', dataIndex: 'name' },
  { title: '类型', dataIndex: 'partType', width: 110 },
  { title: '计量单位', dataIndex: 'uom', width: 100 },
  { title: 'ERP 物料编码', dataIndex: 'erpItemCode', width: 160 },
  {
    title: '状态',
    dataIndex: 'status',
    width: 100,
    render: (status: string) => (
      <Tag color={status === 'ENABLED' ? 'green' : 'default'}>
        {status === 'ENABLED' ? '启用' : '停用'}
      </Tag>
    ),
  },
];

export default function PartDedup() {
  const [groups, setGroups] = useState<PartDedupGroup[]>([]);
  const [loading, setLoading] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [merging, setMerging] = useState(false);
  const [current, setCurrent] = useState<PartDedupGroup | null>(null);
  const [form] = Form.useForm<MergeForm>();
  const canMerge = hasPermission('sys:pdm:part:merge');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setGroups(await listDedupCandidates());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '去重候选加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  /**
   * 打开合并弹窗：默认取组内前两件作为源件与保留件
   *
   * @param group 候选组
   */
  const openMerge = (group: PartDedupGroup) => {
    setCurrent(group);
    form.setFieldsValue({
      sourceId: group.parts[0]?.id,
      targetId: group.parts[1]?.id,
      remark: '',
    });
  };

  /**
   * 提交合并
   */
  const submitMerge = async () => {
    const values = await form.validateFields();
    setMerging(true);
    try {
      const result = await mergeParts(values);
      message.success(
        `已合并：改写草稿行 ${result.rewrittenLines}，累加重复行 ${result.mergedLines}，跳过已发布行 ${result.skippedReleasedLines}`,
      );
      setCurrent(null);
      setRefreshKey((key) => key + 1);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '合并失败');
    } finally {
      setMerging(false);
    }
  };

  const columns: TableProps<PartDedupGroup>['columns'] = [
    { title: '零件名称', dataIndex: 'name' },
    { title: '材质', dataIndex: 'material', width: 140, render: (value?: string) => value || '—' },
    { title: '规格', dataIndex: 'spec', width: 180, render: (value?: string) => value || '—' },
    {
      title: '候选数',
      dataIndex: 'count',
      width: 100,
      render: (count: number) => <Tag color="orange">{count}</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_, group) =>
        canMerge ? (
          <Button type="link" onClick={() => openMerge(group)}>
            合并
          </Button>
        ) : null,
    },
  ];

  const options = (current?.parts ?? []).map((part) => ({
    value: part.id,
    label: `${part.code} ${part.name}`,
  }));

  return (
    <Card
      title="零件去重"
      extra={
        <Space>
          <Button onClick={() => setRefreshKey((key) => key + 1)} loading={loading}>
            刷新
          </Button>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="候选按归一化名称 + 材质 + 规格匹配；合并仅改写草稿 BOM 的子件引用，已发布版本保持不可变，源零件将停用并记录合并目标。"
      />
      <Table<PartDedupGroup>
        rowKey="dedupKey"
        loading={loading}
        columns={columns}
        dataSource={groups}
        pagination={false}
        locale={{ emptyText: '暂无重复零件候选' }}
        expandable={{
          expandedRowRender: (group) => (
            <Table<PartDedupItem>
              rowKey="id"
              size="small"
              columns={ITEM_COLUMNS}
              dataSource={group.parts}
              pagination={false}
            />
          ),
        }}
      />

      <Modal
        title="人工合并零件"
        open={current !== null}
        onOk={() => void submitMerge()}
        onCancel={() => setCurrent(null)}
        confirmLoading={merging}
        okText="确认合并"
        destroyOnHidden
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="源零件合并后立即停用，草稿 BOM 中与其同父件的保留件行会累加用量。"
        />
        <Form form={form} layout="vertical">
          <Form.Item
            name="sourceId"
            label="源零件（合并后停用）"
            rules={[{ required: true, message: '请选择源零件' }]}
          >
            <Select options={options} placeholder="选择源零件" />
          </Form.Item>
          <Form.Item
            name="targetId"
            label="保留零件（承接引用）"
            rules={[
              { required: true, message: '请选择保留零件' },
              ({ getFieldValue }) => ({
                validator(_, value: string) {
                  return value === getFieldValue('sourceId')
                    ? Promise.reject(new Error('源零件与保留零件不能相同'))
                    : Promise.resolve();
                },
              }),
            ]}
          >
            <Select options={options} placeholder="选择保留零件" />
          </Form.Item>
          <Form.Item name="remark" label="合并说明">
            <Input.TextArea rows={2} maxLength={200} showCount placeholder="如：同图号重复建码" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
