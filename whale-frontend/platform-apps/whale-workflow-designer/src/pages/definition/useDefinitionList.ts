// 流程定义列表的数据与动作：分页查询、新建 / 编辑 / 删除、校验、BPMN 导入导出。
// 契约层 ../../api/workflow 为冻结实现，本文件只读不改；Id 一律按 string 处理（禁止 Number(id)）。

import { useCallback, useEffect, useState } from 'react';
import { App, Form, type FormInstance, type TableProps, type UploadProps } from 'antd';
import { useNavigate } from 'react-router-dom';
import {
  workflowApi,
  type DefinitionSaveRequest,
  type Id,
  type ProcessDefinition,
  type ProcessDefinitionValidate,
} from '../../api/workflow';
import {
  MINIMAL_DEFINITION_JSON,
  triggerDownload,
  type BpmnFormValues,
  type CreateFormValues,
  type EditFormValues,
  type IssueRow,
  type QueryState,
  type SearchValues,
} from './constants';

export interface DefinitionListController {
  // 列表与查询
  rows: ProcessDefinition[];
  total: number;
  query: QueryState;
  loading: boolean;
  searchForm: FormInstance<SearchValues>;
  handleSearch: (values: SearchValues) => void;
  handleReset: () => void;
  handleTableChange: TableProps<ProcessDefinition>['onChange'];

  // 新建弹窗
  createOpen: boolean;
  submitting: boolean;
  createForm: FormInstance<CreateFormValues>;
  openCreate: () => void;
  closeCreate: () => void;
  handleCreate: () => Promise<void>;

  // 编辑弹窗
  editing: ProcessDefinition | null;
  editForm: FormInstance<EditFormValues>;
  openEdit: (row: ProcessDefinition) => void;
  closeEdit: () => void;
  handleUpdate: () => Promise<void>;

  // 校验结果弹窗
  validateOpen: boolean;
  validating: boolean;
  validateResult: ProcessDefinitionValidate | null;
  issueRows: IssueRow[];
  openValidate: (row: ProcessDefinition) => Promise<void>;
  closeValidate: () => void;

  // BPMN 导入弹窗
  bpmnOpen: boolean;
  bpmnSubmitting: boolean;
  bpmnForm: FormInstance<BpmnFormValues>;
  openBpmn: () => void;
  closeBpmn: () => void;
  handleBpmnFile: UploadProps['beforeUpload'];
  handleBpmnImport: () => Promise<void>;

  // 行操作
  exportingId: Id | null;
  handleExport: (row: ProcessDefinition) => Promise<void>;
  handleDelete: (row: ProcessDefinition) => Promise<void>;
}

export function useDefinitionList(): DefinitionListController {
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
      setTotal(Number(result.total ?? 0));
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

  /** 关闭编辑弹窗 */
  const closeEdit = () => {
    setEditing(null);
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

  /** 关闭 BPMN 导入弹窗 */
  const closeBpmn = () => {
    setBpmnOpen(false);
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

  /** 关闭校验结果弹窗 */
  const closeValidate = () => {
    setValidateOpen(false);
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

  return {
    rows,
    total,
    query,
    loading,
    searchForm,
    handleSearch,
    handleReset,
    handleTableChange,
    createOpen,
    submitting,
    createForm,
    openCreate,
    closeCreate: () => setCreateOpen(false),
    handleCreate,
    editing,
    editForm,
    openEdit,
    closeEdit,
    handleUpdate,
    validateOpen,
    validating,
    validateResult,
    issueRows,
    openValidate,
    closeValidate,
    bpmnOpen,
    bpmnSubmitting,
    bpmnForm,
    openBpmn,
    closeBpmn,
    handleBpmnFile,
    handleBpmnImport,
    exportingId,
    handleExport,
    handleDelete,
  };
}
