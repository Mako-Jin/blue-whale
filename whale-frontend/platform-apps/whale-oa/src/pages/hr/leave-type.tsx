// OA 假期类型管理页：分页表格 + 新增/编辑弹窗 + 启用停用开关
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import {
  createLeaveType,
  pageLeaveTypes,
  toggleLeaveType,
  updateLeaveType,
  type LeaveTypeSaveCommand,
  type LeaveTypeUnit,
  type LeaveTypeView,
} from '../../api/hr';
import { hasPermission } from '../../utils/permission';

/** 计量单位文案 */
const UNIT_LABEL: Record<LeaveTypeUnit, string> = {
  DAY: '天',
  HOUR: '小时',
};

/** 表单字段 */
interface FormValues {
  code: string;
  name: string;
  unit: LeaveTypeUnit;
  annualQuota: number;
  minUnit: number;
  requiresAttachment?: boolean;
  allowNegative?: boolean;
  enabled?: boolean;
  sortNo?: number;
  remark?: string;
}

/** 查询条件 */
interface Query {
  current: number;
  size: number;
  code: string;
  name: string;
  enabled?: boolean;
}

export default function LeaveTypeCenter() {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const [data, setData] = useState<LeaveTypeView[]>([]);
  const [total, setTotal] = useState(0);
  const [query, setQuery] = useState<Query>({ current: 1, size: 10, code: '', name: '' });
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<LeaveTypeView | null>(null);

  const canEdit = hasPermission('sys:oa:hr:leave-type:edit');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageLeaveTypes({
        current: query.current,
        size: query.size,
        code: query.code || undefined,
        name: query.name || undefined,
        enabled: query.enabled,
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '假期类型查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [query, message]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!modalOpen) {
      return;
    }
    if (editing) {
      form.setFieldsValue({
        code: editing.code,
        name: editing.name,
        unit: editing.unit,
        annualQuota: editing.annualQuota,
        minUnit: editing.minUnit,
        requiresAttachment: editing.requiresAttachment,
        allowNegative: editing.allowNegative,
        enabled: editing.enabled,
        sortNo: editing.sortNo,
        remark: editing.remark,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        unit: 'DAY',
        annualQuota: 0,
        minUnit: 0.5,
        requiresAttachment: false,
        allowNegative: false,
        enabled: true,
        sortNo: 0,
      });
    }
  }, [modalOpen, editing, form]);

  const openCreate = () => {
    setEditing(null);
    setModalOpen(true);
  };

  const openEdit = (record: LeaveTypeView) => {
    setEditing(record);
    setModalOpen(true);
  };

  const handleOk = async () => {
    const values = await form.validateFields();
    const payload: LeaveTypeSaveCommand = {
      code: values.code.trim(),
      name: values.name.trim(),
      unit: values.unit,
      annualQuota: values.annualQuota,
      minUnit: values.minUnit,
      requiresAttachment: values.requiresAttachment,
      allowNegative: values.allowNegative,
      enabled: values.enabled,
      sortNo: values.sortNo,
      remark: values.remark?.trim() || undefined,
    };
    try {
      if (editing) {
        await updateLeaveType(editing.id, payload);
      } else {
        await createLeaveType(payload);
      }
      message.success('保存成功');
      setModalOpen(false);
      setEditing(null);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    }
  };

  const changeEnabled = async (record: LeaveTypeView, enabled: boolean) => {
    try {
      await toggleLeaveType(record.id, enabled);
      message.success(enabled ? '已启用' : '已停用');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '启停失败');
    }
  };

  const columns: TableProps<LeaveTypeView>['columns'] = [
    { title: '编码', dataIndex: 'code', key: 'code', width: 160 },
    { title: '名称', dataIndex: 'name', key: 'name' },
    {
      title: '单位',
      dataIndex: 'unit',
      key: 'unit',
      width: 90,
      render: (value: LeaveTypeUnit) => UNIT_LABEL[value] ?? value,
    },
    { title: '年度额度', dataIndex: 'annualQuota', key: 'annualQuota', width: 110 },
    { title: '最小单位', dataIndex: 'minUnit', key: 'minUnit', width: 110 },
    {
      title: '需附件',
      dataIndex: 'requiresAttachment',
      key: 'requiresAttachment',
      width: 90,
      render: (value?: boolean) => (
        <Tag color={value ? 'blue' : 'default'}>{value ? '是' : '否'}</Tag>
      ),
    },
    {
      title: '允许负额度',
      dataIndex: 'allowNegative',
      key: 'allowNegative',
      width: 110,
      render: (value?: boolean) => (
        <Tag color={value ? 'orange' : 'default'}>{value ? '是' : '否'}</Tag>
      ),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 90,
      render: (value: boolean | undefined, record) => (
        <Switch
          checked={Boolean(value)}
          disabled={!canEdit}
          onChange={(checked) => void changeEnabled(record, checked)}
        />
      ),
    },
    { title: '排序', dataIndex: 'sortNo', key: 'sortNo', width: 80 },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_value, record) =>
        canEdit ? (
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="搜索编码"
          style={{ width: 200 }}
          onSearch={(value) => setQuery((prev) => ({ ...prev, current: 1, code: value.trim() }))}
        />
        <Input.Search
          allowClear
          placeholder="搜索名称"
          style={{ width: 200 }}
          onSearch={(value) => setQuery((prev) => ({ ...prev, current: 1, name: value.trim() }))}
        />
        <Select
          allowClear
          placeholder="启用状态"
          style={{ width: 140 }}
          value={query.enabled}
          options={[
            { label: '已启用', value: true },
            { label: '已停用', value: false },
          ]}
          onChange={(value) =>
            setQuery((prev) => ({ ...prev, current: 1, enabled: value ?? undefined }))
          }
        />
        {canEdit ? (
          <Button type="primary" onClick={openCreate}>
            新增假期类型
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<LeaveTypeView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={{
          current: query.current,
          pageSize: query.size,
          total,
          showSizeChanger: true,
          onChange: (page, pageSize) =>
            setQuery((prev) => ({ ...prev, current: page, size: pageSize })),
        }}
      />
      <Modal
        open={modalOpen}
        title={editing ? '编辑假期类型' : '新增假期类型'}
        onOk={() => void handleOk()}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="code"
            label="编码"
            rules={[
              { required: true, message: '请输入假期类型编码' },
              {
                pattern: /^[A-Z][A-Z0-9_]*$/,
                message: '编码须以大写字母开头，仅含大写字母/数字/下划线',
              },
            ]}
          >
            <Input placeholder="如 ANNUAL" maxLength={32} />
          </Form.Item>
          <Form.Item
            name="name"
            label="名称"
            rules={[{ required: true, message: '请输入假期类型名称' }]}
          >
            <Input placeholder="如 年假" maxLength={64} />
          </Form.Item>
          <Form.Item
            name="unit"
            label="计量单位"
            rules={[{ required: true, message: '请选择计量单位' }]}
          >
            <Select
              options={[
                { label: '天', value: 'DAY' },
                { label: '小时', value: 'HOUR' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="annualQuota"
            label="年度额度"
            rules={[{ required: true, message: '请输入年度额度' }]}
          >
            <InputNumber style={{ width: '100%' }} min={0} precision={2} />
          </Form.Item>
          <Form.Item
            name="minUnit"
            label="最小请假单位"
            rules={[{ required: true, message: '请输入最小请假单位' }]}
          >
            <InputNumber style={{ width: '100%' }} min={0.01} step={0.01} precision={2} />
          </Form.Item>
          <Form.Item name="requiresAttachment" label="需上传附件" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="allowNegative" label="允许负额度" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="sortNo" label="排序号">
            <InputNumber style={{ width: '100%' }} min={0} precision={0} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
