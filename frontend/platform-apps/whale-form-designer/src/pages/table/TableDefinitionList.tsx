// 表定义管理页（F4）：按表名称/表编码/状态查询、受控分页，编辑（含列编辑器）/发布/删除/新建表单视图。
// 依赖 F1 冻结契约层 ../../api/form，本文件不改动契约层。
// 说明：后端列表 VO 含 createTime，但冻结契约 TableDefinition 未声明该字段（契约层不改），
//      这里以交叉类型补足列表展示所需字段。

import {
  App,
  Button,
  Card,
  Col,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  type TableProps,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { tableDefinitionApi, type DataType, type TableDefinition } from '../../api/form';

/** 列表行：契约 TableDefinition 补足 createTime（后端 VO 携带） */
type TableDefinitionRow = TableDefinition & { createTime?: string };

/** 查询条件：表名称模糊（映射后端 name）/ 表编码等值（映射后端 code）/ 状态 */
interface SearchValues {
  tableName?: string;
  tableCode?: string;
  status?: number;
}

const STATUS_OPTIONS = [
  { value: 1, label: '正常' },
  { value: 0, label: '禁用' },
];

const DATA_TYPE_OPTIONS = [
  { value: 'text', label: '文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字' },
  { value: 'date', label: '日期' },
  { value: 'datetime', label: '日期时间' },
  { value: 'select', label: '下拉' },
  { value: 'bool', label: '布尔' },
];

const NULLABLE_OPTIONS = [
  { value: 1, label: '可空' },
  { value: 0, label: '非空' },
];

const REQUIRED_OPTIONS = [
  { value: 1, label: '必填' },
  { value: 0, label: '非必填' },
];

/** 表编码正则（小写字母开头，仅小写字母/数字/下划线，长度≤40） */
const TABLE_CODE_PATTERN = /^[a-z][a-z0-9_]{0,39}$/;

/** 判断是否为 antd 表单校验错误（此类错误由字段自身提示，无需额外 message） */
function isValidationError(error: unknown): boolean {
  return typeof error === 'object' && error !== null && 'errorFields' in error;
}

export default function TableDefinitionList() {
  const { message } = App.useApp();
  const navigate = useNavigate();

  // 列表数据与受控分页
  const [rows, setRows] = useState<TableDefinitionRow[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(false);
  // 生效中的查询条件（点击「查询」后写入，避免输入即请求）
  const [query, setQuery] = useState<SearchValues>({});

  const [searchForm] = Form.useForm<SearchValues>();

  // 新建/编辑弹窗：编辑前先拉详情回填整列，提交时整体替换 columns
  const [modalOpen, setModalOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<TableDefinitionRow | null>(null);
  const [modalLoading, setModalLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<TableDefinition>();

  /** 拉取分页列表（受控分页 + 查询条件；表名称→name、表编码→code、状态→status） */
  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const result = await tableDefinitionApi.page({
        current,
        size,
        name: query.tableName,
        code: query.tableCode,
        status: query.status,
      });
      setRows(result.records as TableDefinitionRow[]);
      setTotal(result.total);
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
    setQuery({ tableName: values.tableName, tableCode: values.tableCode, status: values.status });
    setCurrent(1);
  };

  /** 重置查询条件 */
  const handleReset = () => {
    searchForm.resetFields();
    setQuery({});
    setCurrent(1);
  };

  /** 分页变化：每页条数改变时回到第一页 */
  const handleTableChange: TableProps<TableDefinitionRow>['onChange'] = (pagination) => {
    const nextSize = pagination.pageSize ?? size;
    if (nextSize !== size) {
      setSize(nextSize);
      setCurrent(1);
      return;
    }
    setCurrent(pagination.current ?? 1);
  };

  /** 关闭弹窗并清理编辑态 */
  const closeModal = () => {
    setModalOpen(false);
    setEditRecord(null);
  };

  /** 新建：直接挂载空表单（initialValues 提供默认值） */
  const openCreate = () => {
    setEditRecord(null);
    setModalOpen(true);
  };

  /** 编辑：先拉详情回填，columns 保留每列 id 与 sortNo（后端整体替换） */
  const openEdit = (row: TableDefinitionRow) => {
    if (row.id === undefined) {
      return;
    }
    setEditRecord(row);
    setModalOpen(true);
  };

  // 弹窗打开时回填：编辑拉详情，新建用 initialValues 默认值
  useEffect(() => {
    if (!modalOpen) {
      return;
    }
    if (!editRecord || editRecord.id === undefined) {
      form.resetFields();
      return;
    }
    const id = editRecord.id;
    let cancelled = false;
    setModalLoading(true);
    tableDefinitionApi
      .detail(id)
      .then((detail) => {
        if (cancelled) {
          return;
        }
        form.setFieldsValue({
          tableCode: detail.tableCode,
          tableName: detail.tableName,
          version: detail.version ?? 1,
          status: detail.status ?? 1,
          remark: detail.remark,
          columns: (detail.columns ?? []).map((col) => ({ ...col })),
        });
      })
      .catch((e) => {
        if (cancelled) {
          return;
        }
        message.error((e as Error).message);
        closeModal();
      })
      .finally(() => {
        if (!cancelled) {
          setModalLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modalOpen, editRecord, form, message]);

  /** 发布：失败时把后端 message 原样展示（如「该表版本已发布…」） */
  const handlePublish = async (row: TableDefinitionRow) => {
    if (row.id === undefined) {
      return;
    }
    try {
      await tableDefinitionApi.publish(row.id);
      message.success('发布成功');
      void fetchList();
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  /** 删除 */
  const handleDelete = async (row: TableDefinitionRow) => {
    if (row.id === undefined) {
      return;
    }
    try {
      await tableDefinitionApi.remove(row.id);
      message.success('删除成功');
      void fetchList();
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  /** 提交：新建 POST / 编辑 PUT（携带全部列，后端整体替换） */
  const handleSubmit = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const payload: TableDefinition = {
        tableCode: values.tableCode,
        tableName: values.tableName,
        version: values.version,
        status: values.status,
        remark: values.remark,
        columns: values.columns ?? [],
      };
      if (editRecord && editRecord.id !== undefined) {
        await tableDefinitionApi.update(editRecord.id, payload);
        message.success('保存成功');
      } else {
        await tableDefinitionApi.create(payload);
        message.success('创建成功');
      }
      closeModal();
      void fetchList();
    } catch (e) {
      if (!isValidationError(e)) {
        message.error((e as Error).message);
      }
    } finally {
      setSaving(false);
    }
  };

  const columns: TableProps<TableDefinitionRow>['columns'] = [
    { title: '表编码', dataIndex: 'tableCode', width: 160 },
    { title: '表名称', dataIndex: 'tableName', width: 180 },
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
        value === 1 ? (
          <Tag color="green">正常</Tag>
        ) : value === 0 ? (
          <Tag color="red">禁用</Tag>
        ) : (
          '-'
        ),
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
      width: 280,
      fixed: 'right',
      render: (_, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => openEdit(row)}>
            编辑
          </Button>
          <Button type="link" size="small" onClick={() => void handlePublish(row)}>
            发布
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() => navigate(`/design?tableDefinitionId=${row.id}`)}
          >
            新建表单视图
          </Button>
          <Popconfirm title="确认删除该表定义？" onConfirm={() => void handleDelete(row)}>
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
          <Form.Item name="tableName" label="表名称">
            <Input allowClear placeholder="模糊匹配" style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="tableCode" label="表编码">
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
        title="表定义列表"
        extra={
          <Button type="primary" onClick={openCreate}>
            新建表定义
          </Button>
        }
      >
        <Table<TableDefinitionRow>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1000 }}
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
        open={modalOpen}
        title={editRecord ? '编辑表定义' : '新建表定义'}
        width={1000}
        destroyOnClose
        maskClosable={false}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        onOk={() => void handleSubmit()}
        onCancel={closeModal}
      >
        <Spin spinning={modalLoading}>
          <Form<TableDefinition>
            form={form}
            layout="vertical"
            initialValues={{ version: 1, status: 1, columns: [] }}
          >
            <Row gutter={16}>
              <Col span={8}>
                <Form.Item
                  name="tableCode"
                  label="表编码"
                  rules={[
                    { required: true, message: '请输入表编码' },
                    {
                      pattern: TABLE_CODE_PATTERN,
                      message: '小写字母开头，仅小写字母/数字/下划线，长度≤40',
                    },
                  ]}
                >
                  <Input placeholder="如 user_info" />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item
                  name="tableName"
                  label="表名称"
                  rules={[{ required: true, message: '请输入表名称' }]}
                >
                  <Input placeholder="如 用户信息表" />
                </Form.Item>
              </Col>
              <Col span={4}>
                <Form.Item name="version" label="版本">
                  <InputNumber min={1} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={4}>
                <Form.Item name="status" label="状态">
                  <Select options={STATUS_OPTIONS} />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name="remark" label="备注">
              <Input.TextArea rows={2} />
            </Form.Item>

            <Divider orientation="left">列定义</Divider>
            <Form.List name="columns">
              {(fields, { add, remove }) => (
                <>
                  {fields.map(({ key, name, ...restField }) => (
                    <div
                      key={key}
                      style={{
                        border: '1px solid #f0f0f0',
                        borderRadius: 6,
                        padding: '12px 12px 0',
                        marginBottom: 12,
                      }}
                    >
                      {/* 保留列 id，提交时后端整体替换 columns 并据此更新既有列 */}
                      <Form.Item {...restField} name={[name, 'id']} hidden>
                        <Input />
                      </Form.Item>
                      <Row gutter={12}>
                        <Col span={6}>
                          <Form.Item
                            {...restField}
                            name={[name, 'columnCode']}
                            label="列编码"
                            rules={[{ required: true, message: '请输入列编码' }]}
                          >
                            <Input placeholder="如 user_name" />
                          </Form.Item>
                        </Col>
                        <Col span={6}>
                          <Form.Item
                            {...restField}
                            name={[name, 'columnName']}
                            label="列名称"
                            rules={[{ required: true, message: '请输入列名称' }]}
                          >
                            <Input placeholder="如 用户名" />
                          </Form.Item>
                        </Col>
                        <Col span={4}>
                          <Form.Item {...restField} name={[name, 'dataType']} label="存储类型">
                            <Select options={DATA_TYPE_OPTIONS} />
                          </Form.Item>
                        </Col>
                        <Col span={8}>
                          <Form.Item noStyle shouldUpdate>
                            {() => {
                              const dataType = form.getFieldValue([
                                'columns',
                                name,
                                'dataType',
                              ]) as DataType | undefined;
                              if (dataType === 'number') {
                                return (
                                  <Row gutter={12}>
                                    <Col span={12}>
                                      <Form.Item
                                        {...restField}
                                        name={[name, 'precision']}
                                        label="精度"
                                      >
                                        <InputNumber min={1} style={{ width: '100%' }} />
                                      </Form.Item>
                                    </Col>
                                    <Col span={12}>
                                      <Form.Item
                                        {...restField}
                                        name={[name, 'scale']}
                                        label="小数位"
                                      >
                                        <InputNumber min={0} style={{ width: '100%' }} />
                                      </Form.Item>
                                    </Col>
                                  </Row>
                                );
                              }
                              if (dataType === 'text') {
                                return (
                                  <Form.Item {...restField} name={[name, 'length']} label="长度">
                                    <InputNumber min={1} style={{ width: '100%' }} />
                                  </Form.Item>
                                );
                              }
                              return null;
                            }}
                          </Form.Item>
                        </Col>
                      </Row>
                      <Row gutter={12}>
                        <Col span={3}>
                          <Form.Item {...restField} name={[name, 'nullable']} label="是否可空">
                            <Select options={NULLABLE_OPTIONS} />
                          </Form.Item>
                        </Col>
                        <Col span={3}>
                          <Form.Item {...restField} name={[name, 'required']} label="是否必填">
                            <Select options={REQUIRED_OPTIONS} />
                          </Form.Item>
                        </Col>
                        <Col span={3}>
                          <Form.Item {...restField} name={[name, 'sortNo']} label="排序">
                            <InputNumber min={0} style={{ width: '100%' }} />
                          </Form.Item>
                        </Col>
                        <Col span={3}>
                          <Form.Item {...restField} name={[name, 'status']} label="状态">
                            <Select options={STATUS_OPTIONS} />
                          </Form.Item>
                        </Col>
                        <Col span={4}>
                          <Form.Item {...restField} name={[name, 'defaultValue']} label="默认值">
                            <Input />
                          </Form.Item>
                        </Col>
                        <Col span={5}>
                          <Form.Item {...restField} name={[name, 'remark']} label="备注">
                            <Input />
                          </Form.Item>
                        </Col>
                        <Col span={3}>
                          <Form.Item label=" " colon={false}>
                            <Button danger onClick={() => remove(name)}>
                              删除
                            </Button>
                          </Form.Item>
                        </Col>
                      </Row>
                    </div>
                  ))}
                  <Button
                    type="dashed"
                    block
                    onClick={() =>
                      add({
                        dataType: 'text',
                        nullable: 1,
                        required: 0,
                        sortNo: fields.length,
                        status: 1,
                      })
                    }
                  >
                    新增列
                  </Button>
                </>
              )}
            </Form.List>
          </Form>
        </Spin>
      </Modal>
    </Space>
  );
}
