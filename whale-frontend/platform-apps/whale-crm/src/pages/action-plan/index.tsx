// CRM 行动计划（M6）：规则化行动项生成、分页、手工关闭与规则查看
import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { hasPermission } from '../../utils/permission';
import {
  closeActionItem,
  generateActionItems,
  listActionRules,
  pageActionItems,
  type ActionItem,
  type ActionRule,
} from '../../api/analytics';

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 可展示的错误信息
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}

/** 优先级颜色 */
const PRIORITY_COLOR: Record<string, string> = { P1: 'red', P2: 'orange', P3: 'blue' };

/** 行动计划页 */
export default function ActionPlanPage() {
  const [rows, setRows] = useState<ActionItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<Record<string, unknown>>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [rules, setRules] = useState<ActionRule[]>([]);
  const [target, setTarget] = useState<ActionItem | null>(null);
  const [form] = Form.useForm();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageActionItems({ ...filters, ...page });
      setRows(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    listActionRules()
      .then(setRules)
      .catch(() => {
        // 规则加载失败不阻断行动项列表
      });
  }, []);

  const handleGenerate = async () => {
    try {
      const count = await generateActionItems();
      message.success(`已生成 ${count} 条行动项（去重键保证不重复）`);
      void loadData();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const handleClose = async () => {
    if (!target) {
      return;
    }
    try {
      const values = await form.validateFields();
      await closeActionItem(target.id, values.reason);
      message.success('行动项已关闭');
      setTarget(null);
      void loadData();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    }
  };

  const columns: TableProps<ActionItem>['columns'] = [
    { title: '规则', dataIndex: 'ruleCode', width: 170 },
    {
      title: '优先级',
      dataIndex: 'priority',
      width: 90,
      render: (value?: string) => <Tag color={PRIORITY_COLOR[value ?? '']}>{value ?? '-'}</Tag>,
    },
    { title: '标题', dataIndex: 'title', width: 240, ellipsis: true },
    { title: '客户', dataIndex: 'customerName', width: 160, render: (value?: string, row?: ActionItem) => value || row?.customerId || '-' },
    { title: '建议', dataIndex: 'suggestion', width: 240, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '截止日期', dataIndex: 'dueDate', width: 120, render: (value?: string) => value || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value?: string) => <Tag color={value === 'OPEN' ? 'processing' : 'default'}>{value === 'OPEN' ? '待处理' : '已关闭'}</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, row) => (
        <Space size={0}>
          {row.status === 'OPEN' && hasPermission('sys:crm:action:plan') && (
            <Button size="small" type="link" onClick={() => { setTarget(row); form.resetFields(); }}>关闭</Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="行动计划"
      extra={
        hasPermission('sys:crm:action:plan') && (
          <Space>
            <Button onClick={() => void handleGenerate()}>生成行动项</Button>
          </Space>
        )
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="行动计划为规则化建议：低活客户、商机逾期、目标缺口、合同到期、应收逾期、低满意度；同一对象同一周期只生成一次。"
      />
      <Form
        layout="inline"
        style={{ marginBottom: 16, rowGap: 12 }}
        onFinish={(values) => {
          setFilters({
            ruleCode: values.ruleCode || undefined,
            status: values.status || undefined,
            priority: values.priority || undefined,
            customerId: values.customerId ?? null,
          });
          setPage((prev) => ({ ...prev, current: 1 }));
        }}
      >
        <Form.Item name="ruleCode">
          <Select
            placeholder="规则"
            allowClear
            style={{ width: 220 }}
            options={rules.map((rule) => ({ value: rule.ruleCode, label: rule.ruleName || rule.ruleCode }))}
          />
        </Form.Item>
        <Form.Item name="status">
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 120 }}
            options={[{ value: 'OPEN', label: '待处理' }, { value: 'CLOSED', label: '已关闭' }]}
          />
        </Form.Item>
        <Form.Item name="priority">
          <Select
            placeholder="优先级"
            allowClear
            style={{ width: 110 }}
            options={[{ value: 'P1', label: 'P1' }, { value: 'P2', label: 'P2' }, { value: 'P3', label: 'P3' }]}
          />
        </Form.Item>
        <Form.Item name="customerId"><InputNumber min={1} precision={0} placeholder="客户 ID" style={{ width: 130 }} /></Form.Item>
        <Form.Item><Button type="primary" htmlType="submit">查询</Button></Form.Item>
      </Form>
      <Table<ActionItem>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1300 }}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />
      <Typography.Text strong style={{ display: 'block', margin: '20px 0 8px' }}>规则清单</Typography.Text>
      <List
        size="small"
        dataSource={rules}
        renderItem={(rule) => (
          <List.Item>
            <Space>
              <Tag color={rule.enabled ? 'success' : 'default'}>{rule.enabled ? '启用' : '停用'}</Tag>
              <Typography.Text strong>{rule.ruleName || rule.ruleCode}</Typography.Text>
              <Typography.Text type="secondary">{rule.ruleType}</Typography.Text>
              <Typography.Text type="secondary">阈值 {rule.thresholdDays ?? '-'} 天</Typography.Text>
            </Space>
            <Typography.Text type="secondary">{rule.description || ''}</Typography.Text>
          </List.Item>
        )}
      />

      <Modal
        open={target !== null}
        title="关闭行动项"
        okText="确认关闭"
        cancelText="取消"
        onOk={() => void handleClose()}
        onCancel={() => setTarget(null)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="reason" label="关闭原因" rules={[{ required: true, message: '请输入关闭原因' }]}>
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
