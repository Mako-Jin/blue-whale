// 审批抽屉状态与动作：表单定义 / 数据加载、编辑保存、同意 / 驳回。
// 表单加载任一步失败都降级为占位提示，不阻塞审批提交；同意前先保存未提交的表单修改。

import { App } from 'antd';
import { useCallback, useState } from 'react';
import type { WhaleFormFieldConfig } from '@bluewhale/whale-ui';
import { formApi, formDataApi } from '../../api/form';
import { workflowApi, type ApprovalRequest, type Id, type ProcessTask } from '../../api/workflow';
import { serializeValues } from '../start/formValues';
import type { ApprovalAction, InstanceBrief } from './constants';

interface UseApprovalDrawerOptions {
  /** 审批完成后刷新待办列表 */
  loadList: () => Promise<void>;
  /** 补写所属实例摘要（抽屉内加载实例详情时回填） */
  setInstanceBrief: (instanceId: Id, brief: InstanceBrief) => void;
}

export interface ApprovalController {
  drawerOpen: boolean;
  activeTask: ProcessTask | null;
  drawerLoading: boolean;
  formReady: boolean;
  formFields: WhaleFormFieldConfig[];
  formValues: Record<string, unknown>;
  formError: string | null;
  /** 关联表单数据 ID（实例 businessKey）：编辑保存与审批前置保存都依赖它 */
  formDataId: Id | null;
  formDirty: boolean;
  savingForm: boolean;
  comment: string;
  setComment: (value: string) => void;
  submitting: boolean;
  /** 打开审批（待办）/ 查看（非待办）抽屉 */
  openDrawer: (record: ProcessTask) => void;
  closeDrawer: () => void;
  /** 表单值变更：标记未保存（同意时会先保存再审批） */
  handleFormChange: (next: Record<string, unknown>) => void;
  /** 保存审批中编辑的表单（PUT /form/data/{id}），返回是否成功 */
  saveForm: () => Promise<boolean>;
  /** 提交审批：同意 / 驳回 */
  handleApproval: (action: ApprovalAction) => Promise<void>;
}

/** 审批抽屉状态与动作：表单定义 / 数据加载、编辑保存、同意 / 驳回。 */
export function useApprovalDrawer({
  loadList,
  setInstanceBrief,
}: UseApprovalDrawerOptions): ApprovalController {
  const { message } = App.useApp();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [activeTask, setActiveTask] = useState<ProcessTask | null>(null);
  const [drawerLoading, setDrawerLoading] = useState(false);
  const [formReady, setFormReady] = useState(false);
  const [formFields, setFormFields] = useState<WhaleFormFieldConfig[]>([]);
  const [formValues, setFormValues] = useState<Record<string, unknown>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [formDataId, setFormDataId] = useState<Id | null>(null);
  const [formDirty, setFormDirty] = useState(false);
  const [savingForm, setSavingForm] = useState(false);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);

  /**
   * 抽屉内加载表单：formId → 表单定义 fields；实例 businessKey → 表单数据 fields（键为 columnCode）。
   * 任一步缺失或失败即降级为「无表单数据」占位，**不阻塞审批提交**（F4-c）。
   */
  const loadForm = useCallback(
    async (task: ProcessTask) => {
      setDrawerLoading(true);
      try {
        if (!task.formId) {
          setFormError('该任务未绑定表单');
          return;
        }
        const definition = await formApi.detail(task.formId);
        const fields = definition.fields ?? [];
        if (fields.length === 0) {
          setFormError('绑定表单未配置字段');
          return;
        }
        let businessKey: string | null | undefined = null;
        try {
          const instance = await workflowApi.instanceDetail(task.instanceId);
          if (instance) {
            setInstanceBrief(task.instanceId, {
              definitionCode: instance.definitionCode,
              title: instance.title,
            });
            businessKey = instance.businessKey;
          }
        } catch {
          // 实例详情不可达：视为无关联数据，降级为占位
        }
        if (!businessKey) {
          setFormError('未关联表单数据');
          return;
        }
        const data = await formDataApi.detail(businessKey);
        setFormFields(fields);
        setFormValues(data?.fields ?? {});
        setFormDataId(businessKey);
        setFormDirty(false);
        setFormReady(true);
      } catch {
        setFormError('表单加载失败');
      } finally {
        setDrawerLoading(false);
      }
    },
    [setInstanceBrief],
  );

  /** 打开审批（待办）/ 查看（非待办）抽屉 */
  const openDrawer = useCallback(
    (record: ProcessTask) => {
      setActiveTask(record);
      setDrawerOpen(true);
      setComment(record.comment ?? '');
      setFormReady(false);
      setFormFields([]);
      setFormValues({});
      setFormDataId(null);
      setFormDirty(false);
      setFormError(null);
      void loadForm(record);
    },
    [loadForm],
  );

  /** 关闭抽屉并复位当前任务 */
  const closeDrawer = () => {
    setDrawerOpen(false);
    setActiveTask(null);
  };

  /** 表单值变更：标记未保存（同意时会先保存再审批） */
  const handleFormChange = (next: Record<string, unknown>) => {
    setFormValues(next);
    setFormDirty(true);
  };

  /** 保存审批中编辑的表单（PUT /form/data/{id}），返回是否成功 */
  const saveForm = async (): Promise<boolean> => {
    if (!formDataId || formFields.length === 0) {
      return true;
    }
    setSavingForm(true);
    try {
      const dataJson = JSON.stringify(serializeValues(formFields, formValues));
      await formDataApi.update(formDataId, { dataJson });
      setFormDirty(false);
      message.success('表单已保存');
      return true;
    } catch (e) {
      message.error((e as Error).message);
      return false;
    } finally {
      setSavingForm(false);
    }
  };

  /** 提交审批：同意 / 驳回，comment 随 ApprovalRequest 提交；同意前先保存未提交的表单修改 */
  const handleApproval = async (action: ApprovalAction) => {
    if (!activeTask) {
      return;
    }
    setSubmitting(true);
    try {
      if (action === 'approve' && formDirty) {
        const saved = await saveForm();
        if (!saved) {
          return;
        }
      }
      const payload: ApprovalRequest = { comment: comment.trim() || undefined };
      if (action === 'approve') {
        await workflowApi.taskApprove(activeTask.id, payload);
      } else {
        await workflowApi.taskReject(activeTask.id, payload);
      }
      message.success(action === 'approve' ? '已同意' : '已驳回');
      closeDrawer();
      await loadList();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  return {
    drawerOpen,
    activeTask,
    drawerLoading,
    formReady,
    formFields,
    formValues,
    formError,
    formDataId,
    formDirty,
    savingForm,
    comment,
    setComment,
    submitting,
    openDrawer,
    closeDrawer,
    handleFormChange,
    saveForm,
    handleApproval,
  };
}
