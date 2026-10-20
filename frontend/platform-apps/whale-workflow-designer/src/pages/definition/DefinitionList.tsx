// 流程定义管理页（F2-a）：分页列表 + 按 code/name 过滤 + 新建/编辑/删除 +
// 校验结果展示 + 跳设计器/发起 + BPMN 导入导出。
// 契约层 ../../api/workflow 为冻结实现，本页只读不改；Id 一律按 string 处理（禁止 Number(id)）。

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  type TableProps,
  type UploadProps,
} from 'antd';
import { useNavigate } from 'react-router-dom';
import {
  workflowApi,
  type DefinitionSaveRequest,
  type Id,
  type ProcessDefinition,
  type ProcessDefinitionValidate,
  type ValidationIssue,
} from '../../api/workflow';

const { Paragraph, Text } = Typography;

/** 新建定义时写入的最小合法骨架（规范化 JSON），后续在设计器中补齐 */
const MINIMAL_DEFINITION_JSON = JSON.stringify({
  type: 'stateMachine',
  nodes: [{ key: 'start', type: 'START_EVENT', name: '开始' }],
  flows: [],
});

/** 查询条件（写入 state 后触发请求，空串由 http 层自动忽略） */
interface QueryState {
  current: number;
  size: number;
  code?: string;
  name?: string;
}

/** 查询表单值 */
interface SearchValues {
  code?: string;
  name?: string;
}

/** 新建弹窗表单值 */
interface CreateFormValues {
  code: string;
  name: string;
}

/** 编辑弹窗表单值 */
interface EditFormValues {
  code: string;
  name: string;
}

/** BPMN 导入弹窗表单值 */
interface BpmnFormValues {
  code: string;
  name: string;
  bpmnXml: string;
}

/** 校验结果表格行：错误与警告合并展示，以 level 区分 */
interface IssueRow extends ValidationIssue {
  rowKey: string;
}

/** 触发浏览器下载原始字节流（参考 form-designer 的导出用法） */
function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export default function DefinitionList() {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();

  // 列表数据与受控分页
  const [rows, setRows] = useState<ProcessDefinition[]>([]);
  const [total, setTotal] = useState(0);
  const [query, setQuery] = useState<QueryState>({ current: 1, size: 10 });
  const [loading, setLoading] = useState(false);
  const [searchForm] = Form.useForm<SearchValues>();

  // 新建弹窗
  const [createOpen, setCreateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [createForm] = Form.useForm<CreateFormValues>();

  // 编辑弹窗：记录当前编辑行以沿用原 definitionJson
  const [editing, setEditing] = useState<ProcessDefinition | null>(null);
  const [editForm] = Form.useForm<EditFormValues>();

  // 校验结果弹窗
  const [validateOpen, setValidateOpen] = useState(false);
  const [validating, setValidating] = useState(false);
  const [validateResult, setValidateResult] = useState<ProcessDefinitionValidate | null>(null);

  // BPMN 导入弹窗
  const [bpmnOpen, setBpmnOpen] = useState(false);
  const [bpmnSubmitting, setBpmnSubmitting] = useState(false);
  const [bpmnForm] = Form.useForm<BpmnFormValues>();

  /** BPMN 导出按钮 loading（按行 id 标记，避免全表模糊） */
  const [exportingId, setExportingId] = useState<Id | null>(null);

  /** 拉取分页列表 */
  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const result = await workflowApi.definitionPage({
        current: query.current,
        size: query.size,
        code: query.code,
        name: query.name,
      });
      setRows(result.records ?? []);
      setTotal(result.total ?? 0);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [query, message]);

  useEffect(() => {
    void fetchList();
  }, [fetchList]);

  /** 查询：写入条件并回到第一页 */
  const handleSearch = (values: SearchValues) => {
    setQuery((prev) => ({
      ...prev,
      current: 1,
      code: values.code?.trim() || undefined,
      name: values.name?.trim() || undefined,
    }));
  };

  /** 重置查询条件 */
  const handleReset = () => {
    searchForm.resetFields();
    setQuery({ current: 1, size: 10 });
  };

  /** 分页变化：每页条数改变时回到第一页 */
  const handleTableChange: TableProps<ProcessDefinition>['onChange'] = (pagination) => {
    const nextSize = pagination.pageSize ?? query.size;
    if (nextSize !== query.size) {
      setQuery((prev) => ({ ...prev, size: nextSize, current: 1 }));
      return;
    }
    setQuery((prev) => ({ ...prev, current: pagination.current ?? 1 }));
  };

  /** 打开新建弹窗 */
  const openCreate = () => {
    createForm.resetFields();
    setCreateOpen(true);
  };

  /** 提交新建：写入最小骨架，成功后可跳设计器继续绘制 */
  const handleCreate = async () => {
    let values: CreateFormValues;
    try {
      values = await createForm.validateFields();
    } catch {
      return; // 校验失败由字段提示
    }
    setSubmitting(true);
    try {
      const created = await workflowApi.definitionCreate({
        code: values.code,
        name: values.name,
        definitionJson: MINIMAL_DEFINITION_JSON,
      });
      message.success('流程定义已创建');
      setCreateOpen(false);
      if (created.id !== undefined) {
        const newId: Id = created.id;
        modal.confirm({
          title: '是否立即前往设计器绘制流程？',
          okText: '去设计',
          cancelText: '稍后',
          onOk: () => navigate(`/design/${newId}`),
          onCancel: () => void fetchList(),
        });
      } else {
        void fetchList();
      }
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  /** 打开编辑弹窗（仅改 code / name，definitionJson 沿用原值） */
  const openEdit = (row: ProcessDefinition) => {
    setEditing(row);
    editForm.setFieldsValue({ code: row.code, name: row.name });
  };

  /** 提交编辑 */
  const handleUpdate = async () => {
    if (!editing || editing.id === undefined) {
      return;
    }
    let values: EditFormValues;
    try {
      values = await editForm.validateFields();
    } catch {
      return;
    }
    setSubmitting(true);
    try {
      const payload: DefinitionSaveRequest = {
        code: values.code,
        name: values.name,
        // 沿用原 definitionJson，避免误清空；原值缺失时用最小骨架兜底
        definitionJson: editing.definitionJson ?? MINIMAL_DEFINITION_JSON,
        version: editing.version,
        status: editing.status,
        remark: editing.remark ?? undefined,
      };
      await workflowApi.definitionUpdate(editing.id, payload);
      message.success('保存成功');
      setEditing(null);
      void fetchList();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  /** 删除定义（当前页删空且非首页时回退一页） */
  const handleDelete = async (row: ProcessDefinition) => {
    if (row.id === undefined) {
      return;
    }
    try {
      await workflowApi.definitionRemove(row.id);
      message.success('删除成功');
      if (rows.length <= 1 && query.current > 1) {
        setQuery((prev) => ({ ...prev, current: prev.current - 1 }));
      } else {
        void fetchList();
      }
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  /** 校验：对 definitionJson 调用校验接口，结果（含 valid=false）在弹窗中正常展示 */
  const openValidate = async (row: ProcessDefinition) => {
    setValidateResult(null);
    setValidateOpen(true);
    setValidating(true);
    try {
      const result = await workflowApi.definitionValidate({
        definitionJson: row.definitionJson ?? '',
      });
      setValidateResult(result);
    } catch (e) {
      message.error((e as Error).message);
      setValidateOpen(false);
    } finally {
      setValidating(false);
    }
  };

  /** BPMN 导出：按 blob + filename 触发浏览器下载 */
  const handleExport = async (row: ProcessDefinition) => {
    if (row.id === undefined) {
      return;
    }
    setExportingId(row.id);
    try {
      const { blob, filename } = await workflowApi.definitionBpmnExport(row.id);
      triggerDownload(blob, filename || `${row.code || 'process'}.bpmn`);
      message.success('导出成功');
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setExportingId(null);
    }
  };

  /** 打开 BPMN 导入弹窗 */
  const openBpmn = () => {
    bpmnForm.resetFields();
    setBpmnOpen(true);
  };

  /** 读取所选 .bpmn/.xml 文件为文本并填入文本域 */
  const handleBpmnFile: UploadProps['beforeUpload'] = (file) => {
    const reader = new FileReader();
    reader.onload = () => {
      const text = typeof reader.result === 'string' ? reader.result : '';
      bpmnForm.setFieldValue('bpmnXml', text);
    };
    reader.onerror = () => message.error('文件读取失败');
    reader.readAsText(file);
    return false; // 阻止上传，仅本地读取文本
  };

  /** 提交 BPMN 导入 */
  const handleBpmnImport = async () => {
    let values: BpmnFormValues;
    try {
      values = await bpmnForm.validateFields();
    } catch {
      return;
    }
    setBpmnSubmitting(true);
    try {
      await workflowApi.definitionBpmnImport({
        code: values.code,
        name: values.name,
        bpmnXml: values.bpmnXml,
      });
      message.success('BPMN 导入成功');
      setBpmnOpen(false);
      void fetchList();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setBpmnSubmitting(false);
    }
  };

  // 校验结果：错误 + 警告合并为可展示行
  const issueRows: IssueRow[] = validateResult
    ? [
        ...(validateResult.errors ?? []).map((issue, index) => ({
          ...issue,
          rowKey: `e-${index}`,
        })),
        ...(validateResult.warnings ?? []).map((issue, index) => ({
          ...issue,
          rowKey: `w-${index}`,
        })),
      ]
    : [];

  const columns: TableProps<ProcessDefinition>['columns'] = [
    { title: '编码', dataIndex: 'code', width: 180 },
    { title: '名称', dataIndex: 'name', width: 180 },
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
        value === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>,
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
      width: 340,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => navigate(`/design/${row.id}`)}>
            设计
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() => navigate(`/start?definitionId=${row.id}`)}
          >
            发起
          </Button>
          <Button type="link" size="small" onClick={() => void openValidate(row)}>
            校验
          </Button>
          <Button
            type="link"
            size="small"
            loading={exportingId === row.id}
            onClick={() => void handleExport(row)}
          >
            BPMN导出
          </Button>
          <Button type="link" size="small" onClick={() => openEdit(row)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该流程定义？"
            okText="删除"
            cancelText="取消"
            onConfirm={() => void handleDelete(row)}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const issueColumns: TableProps<IssueRow>['columns'] = [
    {
      title: '级别',
      dataIndex: 'level',
      width: 100,
      render: (level: ValidationIssue['level']) =>
        level === 'ERROR' ? <Tag color="red">错误</Tag> : <Tag color="orange">警告</Tag>,
    },
    { title: '校验码', dataIndex: 'code', width: 240 },
    { title: '说明', dataIndex: 'message' },
    {
      title: '关联',
      dataIndex: 'refKey',
      width: 140,
      render: (refKey: string | null | undefined) => refKey ?? '-',
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card size="small">
        <Form<SearchValues> form={searchForm} layout="inline" onFinish={handleSearch}>
          <Form.Item name="code" label="编码">
            <Input allowClear placeholder="精确匹配" style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="name" label="名称">
            <Input allowClear placeholder="模糊匹配" style={{ width: 180 }} />
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
        title="流程定义列表"
        extra={
          <Space>
            <Button onClick={openBpmn}>BPMN导入</Button>
            <Button type="primary" onClick={openCreate}>
              新建流程定义
            </Button>
          </Space>
        }
      >
        <Table<ProcessDefinition>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          onChange={handleTableChange}
          pagination={{
            current: query.current,
            pageSize: query.size,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
          }}
        />
      </Card>

      <Modal
        open={createOpen}
        title="新建流程定义"
        destroyOnClose
        confirmLoading={submitting}
        onOk={() => void handleCreate()}
        onCancel={() => setCreateOpen(false)}
      >
        <Form<CreateFormValues> form={createForm} layout="vertical">
          <Form.Item
            name="code"
            label="流程编码"
            rules={[{ required: true, message: '请输入流程编码' }]}
          >
            <Input placeholder="如 leave_apply" />
          </Form.Item>
          <Form.Item
            name="name"
            label="流程名称"
            rules={[{ required: true, message: '请输入流程名称' }]}
          >
            <Input placeholder="如 请假申请" />
          </Form.Item>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            将创建含单个开始节点的最小定义，创建后可跳转设计器继续绘制。
          </Paragraph>
        </Form>
      </Modal>

      <Modal
        open={editing !== null}
        title="编辑流程定义"
        destroyOnClose
        confirmLoading={submitting}
        onOk={() => void handleUpdate()}
        onCancel={() => setEditing(null)}
      >
        <Form<EditFormValues> form={editForm} layout="vertical">
          <Form.Item
            name="code"
            label="流程编码"
            rules={[{ required: true, message: '请输入流程编码' }]}
          >
            <Input placeholder="如 leave_apply" />
          </Form.Item>
          <Form.Item
            name="name"
            label="流程名称"
            rules={[{ required: true, message: '请输入流程名称' }]}
          >
            <Input placeholder="如 请假申请" />
          </Form.Item>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            仅修改编码与名称，流程结构（definitionJson）保持原值不变。
          </Paragraph>
        </Form>
      </Modal>

      <Modal
        open={validateOpen}
        title="校验结果"
        loading={validating}
        footer={<Button onClick={() => setValidateOpen(false)}>关闭</Button>}
        width={720}
        onCancel={() => setValidateOpen(false)}
      >
        {validateResult ? (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="是否合法">
                {validateResult.valid ? <Tag color="green">合法</Tag> : <Tag color="red">不合法</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="模式">{validateResult.mode}</Descriptions.Item>
              <Descriptions.Item label="节点数">{validateResult.nodeCount}</Descriptions.Item>
              <Descriptions.Item label="流转数">{validateResult.flowCount}</Descriptions.Item>
            </Descriptions>
            {issueRows.length === 0 ? (
              <Text type="secondary">无错误与警告</Text>
            ) : (
              <Table<IssueRow>
                rowKey="rowKey"
                size="small"
                columns={issueColumns}
                dataSource={issueRows}
                pagination={false}
              />
            )}
          </Space>
        ) : (
          <Text type="secondary">校验中…</Text>
        )}
      </Modal>

      <Modal
        open={bpmnOpen}
        title="BPMN 导入"
        destroyOnClose
        confirmLoading={bpmnSubmitting}
        onOk={() => void handleBpmnImport()}
        onCancel={() => setBpmnOpen(false)}
      >
        <Form<BpmnFormValues> form={bpmnForm} layout="vertical">
          <Form.Item
            name="code"
            label="流程编码"
            rules={[{ required: true, message: '请输入流程编码' }]}
          >
            <Input placeholder="如 leave_apply" />
          </Form.Item>
          <Form.Item
            name="name"
            label="流程名称"
            rules={[{ required: true, message: '请输入流程名称' }]}
          >
            <Input placeholder="如 请假申请" />
          </Form.Item>
          <Form.Item label="BPMN 文件">
            <Upload accept=".bpmn,.xml" showUploadList={false} beforeUpload={handleBpmnFile}>
              <Button>选择 .bpmn / .xml 文件</Button>
            </Upload>
          </Form.Item>
          <Form.Item
            name="bpmnXml"
            label="BPMN XML"
            rules={[{ required: true, message: '请粘贴或上传 BPMN XML' }]}
          >
            <Input.TextArea rows={8} placeholder="粘贴 BPMN 2.0 XML 内容" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
