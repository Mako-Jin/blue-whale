// 表单视图列表页（F5）：按名称/编码/状态查询、受控分页、设计/发布/填报/数据/删除，
// 以及「新建表单视图」（先建空定义，再带 tableDefinitionId 跳到设计器补齐字段）。
// 依赖 F1 冻结契约层 ../../api/form，本文件不改动契约层。

import {
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  type TableProps,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { formApi, type FormDefinition, type Id } from '../../api/form';

// 后端列表 VO 含 createTime，但冻结契约 FormDefinition 未声明该字段（契约层不改），
// 这里以交叉类型补足列表展示所需字段。
type FormRow = FormDefinition & { createTime?: string };

/** 查询条件：名称模糊 / 编码等值 / 状态 */
interface SearchValues {
  name?: string;
  code?: string;
  status?: number;
}

/** 新建表单视图的表单值 */
interface CreateFormValues {
  code: string;
  name: string;
  tableDefinitionId: Id;
  version?: number;
  remark?: string;
}

const STATUS_OPTIONS = [
  { value: 1, label: '启用' },
  { value: 0, label: '停用' },
];

export default function FormList() {
  const { message } = App.useApp();
  const navigate = useNavigate();

  // 列表数据与受控分页
  const [rows, setRows] = useState<FormRow[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(false);
  // 生效中的查询条件（点击「查询」后写入，避免输入即请求）
  const [query, setQuery] = useState<SearchValues>({});

  const [searchForm] = Form.useForm<SearchValues>();

  // 新建弹窗
  const [createOpen, setCreateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [tableOptions, setTableOptions] = useState<{ value: Id; label: string }[]>([]);
  const [createForm] = Form.useForm<CreateFormValues>();

  /** 拉取分页列表（受控分页 + 查询条件） */
  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const result = await formApi.page({ current, size, ...query });
      setRows(result.records as FormRow[]);
      setTotal(Number(result.total));
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [current, size, query, message]);

  useEffect(() => {
    void fetchList();
  }, [fetchList]);

  /** 查询：写入条件并回到第一页 */
  const handleSearch = (values: SearchValues) => {
    setQuery({ name: values.name, code: values.code, status: values.status });
    setCurrent(1);
  };

  /** 重置查询条件 */
  const handleReset = () => {
    searchForm.resetFields();
    setQuery({});
    setCurrent(1);
  };

  /** 分页变化：每页条数改变时回到第一页 */
  const handleTableChange: TableProps<FormRow>['onChange'] = (pagination) => {
    const nextSize = pagination.pageSize ?? size;
    if (nextSize !== size) {
      setSize(nextSize);
      setCurrent(1);
      return;
    }
    setCurrent(pagination.current ?? 1);
  };

  /** 发布表单视图 */
  const handlePublish = async (row: FormRow) => {
    if (row.id === undefined) {
      return;
    }
    try {
      await formApi.publish(row.id);
      message.success('发布成功');
      void fetchList();
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  /** 删除表单视图 */
  const handleDelete = async (row: FormRow) => {
    if (row.id === undefined) {
      return;
    }
    try {
      await formApi.remove(row.id);
      message.success('删除成功');
      void fetchList();
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  /** 打开新建弹窗并加载「所属表定义」下拉数据 */
  const openCreate = () => {
    setCreateOpen(true);
    void (async () => {
      try {
        const result = await formApi.tables({ current: 1, size: 200 });
        setTableOptions(
          result.records
            .filter((t) => t.id !== undefined)
            .map((t) => ({ value: t.id as Id, label: `${t.tableCode} - ${t.tableName}` })),
        );
      } catch (e) {
        message.error((e as Error).message);
      }
    })();
  };

  /** 提交新建：先建空定义，再带 tableDefinitionId 跳设计器 */
  const handleCreate = async () => {
    let values: CreateFormValues;
    try {
      values = await createForm.validateFields();
    } catch {
      return; // 表单校验失败由字段自身提示，此处不再弹 message
    }
    setSubmitting(true);
    try {
      // 重要（后端约束）：后端在「带 tableDefinitionId 且 fields 为空」时会报
      // 「表单视图未配置任何数据字段」。因此这里刻意「不带 tableDefinitionId」，
      // 先按旧模型创建可成功落库的空定义；随后把所选表定义通过 query 传给设计器，
      // 设计器保存时会以元数据模型（PUT 带 tableDefinitionId + fields）补齐并落库。
      const created = await formApi.create({
        code: values.code,
        name: values.name,
        version: values.version ?? 1,
        remark: values.remark,
      });
      message.success('表单视图已创建，请在设计器中配置字段');
      setCreateOpen(false);
      navigate(`/design/${created.id}?tableDefinitionId=${values.tableDefinitionId}`);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  const columns: TableProps<FormRow>['columns'] = [
    { title: '编码', dataIndex: 'code', width: 180 },
    { title: '名称', dataIndex: 'name', width: 180 },
    {
      title: '所属表定义',
      dataIndex: 'tableDefinitionId',
      width: 120,
      render: (value: Id | undefined) => value ?? '-',
    },
    {
      title: '版本',
      dataIndex: 'version',
      width: 80,
      render: (value: number | undefined) => value ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: number | undefined) =>
        value === 1 ? <Tag color="green">启用</Tag> : <Tag color="red">停用</Tag>,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 300,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => navigate(`/design/${row.id}`)}>
            设计
          </Button>
          <Button type="link" size="small" onClick={() => void handlePublish(row)}>
            发布
          </Button>
          <Button type="link" size="small" onClick={() => navigate(`/render/${row.id}`)}>
            填报
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() => navigate(`/data?definitionCode=${row.code}`)}
          >
            数据
          </Button>
          <Popconfirm title="确认删除该表单视图？" onConfirm={() => void handleDelete(row)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card size="small">
        <Form<SearchValues> form={searchForm} layout="inline" onFinish={handleSearch}>
          <Form.Item name="name" label="名称">
            <Input allowClear placeholder="模糊匹配" style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="code" label="编码">
            <Input allowClear placeholder="精确匹配" style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select allowClear placeholder="全部" options={STATUS_OPTIONS} style={{ width: 120 }} />
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
        title="表单视图列表"
        extra={
          <Button type="primary" onClick={openCreate}>
            新建表单视图
          </Button>
        }
      >
        <Table<FormRow>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          onChange={handleTableChange}
          pagination={{
            current,
            pageSize: size,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
          }}
        />
      </Card>

      <Modal
        open={createOpen}
        title="新建表单视图"
        destroyOnClose
        confirmLoading={submitting}
        onOk={() => void handleCreate()}
        onCancel={() => setCreateOpen(false)}
      >
        <Form<CreateFormValues> form={createForm} layout="vertical" initialValues={{ version: 1 }}>
          <Form.Item
            name="code"
            label="表单编码"
            rules={[{ required: true, message: '请输入表单编码' }]}
          >
            <Input placeholder="如 guest_form" />
          </Form.Item>
          <Form.Item
            name="name"
            label="表单名称"
            rules={[{ required: true, message: '请输入表单名称' }]}
          >
            <Input placeholder="如 访客登记表" />
          </Form.Item>
          <Form.Item
            name="tableDefinitionId"
            label="所属表定义"
            rules={[{ required: true, message: '请选择所属表定义' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="请选择所属表定义"
              options={tableOptions}
            />
          </Form.Item>
          <Form.Item name="version" label="版本">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
