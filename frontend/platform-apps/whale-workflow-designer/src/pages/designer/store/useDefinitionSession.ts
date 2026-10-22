// 流程定义的加载与动作：code/name 元信息、加载既有定义（:id）、保存、校验、导出 BPMN。
// 加载 / 发布后统一走 applyDefinition：回填元信息、重置画布模型、清空选中与校验结果。

import { App } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  workflowApi,
  type Id,
  type ProcessDefinition,
  type ProcessDefinitionValidate,
} from '../../../api/workflow';
import { toMessage } from '../errors';
import { parseDefinition, serializeDefinition, type CanvasModel } from '../model';
import { EMPTY_MODEL } from './useHistory';

const EMPTY_CODE = '';
const EMPTY_NAME = '';

export interface DefinitionSessionOptions {
  definitionId: Id | null;
  /** 当前画布模型（保存 / 校验入参） */
  model: CanvasModel;
  /** 丢弃历史并替换模型 */
  resetModel: (model: CanvasModel) => void;
  /** 清空画布选中态与连线起点 */
  clearSelection: () => void;
  /** 新建成功后跳转（携带新定义 id） */
  onCreated: (id: Id) => void;
}

export interface DefinitionSession {
  code: string;
  setCode: (value: string) => void;
  name: string;
  setName: (value: string) => void;
  loading: boolean;
  saving: boolean;
  validating: boolean;
  validation: ProcessDefinitionValidate | null;
  /** 校验 errors 的 refKey 集合（节点 key 或流转 key） */
  errorKeys: Set<string>;
  /** 应用一份定义（null 表示新建 / 清空） */
  applyDefinition: (definition: ProcessDefinition | null) => void;
  handleSave: () => Promise<void>;
  handleValidate: () => Promise<void>;
  handleExportBpmn: () => Promise<void>;
}

export function useDefinitionSession({
  definitionId,
  model,
  resetModel,
  clearSelection,
  onCreated,
}: DefinitionSessionOptions): DefinitionSession {
  const { message } = App.useApp();
  const [code, setCode] = useState(EMPTY_CODE);
  const [name, setName] = useState(EMPTY_NAME);
  const [validation, setValidation] = useState<ProcessDefinitionValidate | null>(null);
  const [loading, setLoading] = useState(definitionId !== null);
  const [saving, setSaving] = useState(false);
  const [validating, setValidating] = useState(false);

  /** 应用一份定义（null 表示新建 / 清空） */
  const applyDefinition = (definition: ProcessDefinition | null) => {
    setCode(definition?.code ?? EMPTY_CODE);
    setName(definition?.name ?? EMPTY_NAME);
    resetModel(definition ? parseDefinition(definition.definitionJson) : EMPTY_MODEL);
    clearSelection();
    setValidation(null);
  };

  // 加载 effect 只依赖 definitionId：applyDefinition 每次渲染新建，经 ref 取最新值
  const applyRef = useRef(applyDefinition);
  useEffect(() => {
    applyRef.current = applyDefinition;
  });

  // 加载既有定义（:id）；无 :id 即新建（清空画布与元信息）
  useEffect(() => {
    if (!definitionId) {
      applyRef.current(null);
      setLoading(false);
      return;
    }
    let alive = true;
    setLoading(true);
    workflowApi
      .definitionDetail(definitionId)
      .then((definition) => {
        if (!alive) {
          return;
        }
        applyRef.current(definition);
      })
      .catch((error) => {
        if (alive) {
          message.error(toMessage(error));
        }
      })
      .finally(() => {
        if (alive) {
          setLoading(false);
        }
      });
    return () => {
      alive = false;
    };
  }, [definitionId, message]);

  const errorKeys = useMemo(
    () =>
      new Set(
        (validation?.errors ?? [])
          .map((issue) => issue.refKey)
          .filter((key): key is string => !!key),
      ),
    [validation],
  );

  const handleSave = async () => {
    if (!code.trim()) {
      message.warning('请填写流程编码 code');
      return;
    }
    if (!name.trim()) {
      message.warning('请填写流程名称 name');
      return;
    }
    setSaving(true);
    const payload = {
      code: code.trim(),
      name: name.trim(),
      definitionJson: serializeDefinition(model),
    };
    try {
      if (definitionId) {
        await workflowApi.definitionUpdate(definitionId, payload);
        message.success('保存成功');
      } else {
        const created = await workflowApi.definitionCreate(payload);
        message.success('新建成功');
        if (created?.id) {
          onCreated(created.id);
        }
      }
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSaving(false);
    }
  };

  const handleValidate = async () => {
    setValidating(true);
    try {
      const result = await workflowApi.definitionValidate({
        definitionJson: serializeDefinition(model),
      });
      setValidation(result);
      if (result.valid) {
        message.success(
          `校验通过（节点 ${result.nodeCount} / 流转 ${result.flowCount}，模式 ${result.mode}）`,
        );
      }
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setValidating(false);
    }
  };

  const handleExportBpmn = async () => {
    if (!definitionId) {
      message.warning('请先保存流程定义后再导出 BPMN');
      return;
    }
    try {
      const { blob, filename } = await workflowApi.definitionBpmnExport(definitionId);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
      message.success('BPMN 已导出');
    } catch (error) {
      message.error(toMessage(error));
    }
  };

  return {
    code,
    setCode,
    name,
    setName,
    loading,
    saving,
    validating,
    validation,
    errorKeys,
    applyDefinition,
    handleSave,
    handleValidate,
    handleExportBpmn,
  };
}
