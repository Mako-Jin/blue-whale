// 流程发起页（Wave 5.32 F3）
// 选定义 → 求入口审批节点 → 填绑定表单 → 提交表单数据取数据 ID → 以该 ID 为 businessKey 发起实例。
// 入口节点无表单 / 表单降级时：仅填业务标题即可发起。

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Alert,
  App,
  Button,
  Card,
  Empty,
  Input,
  Result,
  Select,
  Skeleton,
  Space,
  Typography,
} from 'antd';
import { FormRenderer } from '@bluewhale/whale-ui';
import { formApi, formDataApi, type FormDefinition } from '../../api/form';
import { getToken } from '../../api/token';
import {
  workflowApi,
  type Id,
  type ProcessDefinition,
  type ProcessInstance,
} from '../../api/workflow';
import { findEntryApprovalNode, parseDefinitionModel, type ParsedNode } from './model';
import {
  findMissingRequired,
  isSameValues,
  serializeValues,
  stringifyFormData,
} from './formValues';

const { Text } = Typography;

/** 定义下拉一次拉取上限（对齐 spec §3.4） */
const DEFINITION_PAGE_SIZE = 200;

export default function ProcessStart() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [definitions, setDefinitions] = useState<ProcessDefinition[]>([]);
  const [loadingDefs, setLoadingDefs] = useState(false);
  // 路由 query 即后端雪花 ID 的字符串形式，禁止 Number() 转换（>2^53 会丢精度）
  const [definitionId, setDefinitionId] = useState<string | undefined>(
    searchParams.get('definitionId') ?? undefined,
  );
  const [definition, setDefinition] = useState<ProcessDefinition | null>(null);
  const [entryNode, setEntryNode] = useState<ParsedNode | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const [title, setTitle] = useState('');

  const [formDefinition, setFormDefinition] = useState<FormDefinition | null>(null);
  const [formLoading, setFormLoading] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [values, setValues] = useState<Record<string, unknown>>({});

  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<ProcessInstance | null>(null);

  // 加载定义下拉
  useEffect(() => {
    let active = true;
    setLoadingDefs(true);
    workflowApi
      .definitionPage({ current: 1, size: DEFINITION_PAGE_SIZE })
      .then((page) => {
        if (active) {
          setDefinitions(page.records ?? []);
        }
      })
      .catch((error: unknown) => {
        if (active) {
          message.error(error instanceof Error ? error.message : '流程定义列表加载失败');
        }
      })
      .finally(() => {
        if (active) {
          setLoadingDefs(false);
        }
      });
    return () => {
      active = false;
    };
  }, [message]);

  // 选中定义 → 详情 → 解析定义 JSON → 求入口审批节点
  useEffect(() => {
    let active = true;
    setDefinition(null);
    setEntryNode(null);
    if (!definitionId) {
      setDetailLoading(false);
      return () => {
        active = false;
      };
    }
    setDetailLoading(true);
    workflowApi
      .definitionDetail(definitionId)
      .then((data) => {
        if (!active) {
          return;
        }
        setDefinition(data);
        const model = parseDefinitionModel(data.definitionJson);
        setEntryNode(model ? findEntryApprovalNode(model) : null);
      })
      .catch((error: unknown) => {
        if (active) {
          message.error(error instanceof Error ? error.message : '流程定义加载失败');
        }
      })
      .finally(() => {
        if (active) {
          setDetailLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [definitionId, message]);

  const entryFormId = entryNode?.formId ?? null;

  // 入口节点有 formId → 加载表单定义（失败降级为可不填表单直接发起）
  useEffect(() => {
    let active = true;
    setValues({});
    setFormError(null);
    if (!entryFormId) {
      setFormDefinition(null);
      setFormLoading(false);
      return () => {
        active = false;
      };
    }
    setFormDefinition(null);
    setFormLoading(true);
    formApi
      .detail(entryFormId)
      .then((data) => {
        if (active) {
          setFormDefinition(data);
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setFormError(error instanceof Error ? error.message : '表单加载失败');
        }
      })
      .finally(() => {
        if (active) {
          setFormLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [entryFormId]);

  const handleValuesChange = useCallback((next: Record<string, unknown>) => {
    setValues((previous) => (isSameValues(previous, next) ? previous : next));
  }, []);

  const definitionOptions = useMemo(() => {
    const options = definitions
      .filter((item): item is ProcessDefinition & { id: Id } => Boolean(item.id))
      .map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }));
    if (definitionId && definition && !options.some((option) => option.value === definitionId)) {
      options.unshift({ label: `${definition.name}（${definition.code}）`, value: definitionId });
    }
    return options;
  }, [definitions, definitionId, definition]);

  /** 提交：先提交表单数据取数据 ID，再以该 ID 作为 businessKey 发起实例 */
  const handleSubmit = async () => {
    if (!definitionId) {
      message.error('请先选择流程定义');
      return;
    }
    if (!title.trim()) {
      message.error('请填写业务标题');
      return;
    }

    const fields = formDefinition?.fields ?? [];
    // 表单已成功加载才做必填校验；加载失败（降级）或未绑定表单时跳过
    if (formDefinition) {
      const missing = findMissingRequired(fields, values);
      if (missing) {
        message.error(`请输入${missing.fieldLabel}`);
        return;
      }
    }

    setSubmitting(true);
    try {
      let businessKey: string | undefined;
      if (formDefinition && entryFormId) {
        const payload = serializeValues(fields, values);
        const created = await formDataApi.create({
          definitionId: entryFormId,
          dataJson: stringifyFormData(payload),
        });
        businessKey = created.id;
      }
      const instance = await workflowApi.instanceStart({
        definitionId,
        title: title.trim(),
        businessKey,
      });
      setResult(instance);
      message.success('流程发起成功');
    } catch (error: unknown) {
      message.error(error instanceof Error ? error.message : '流程发起失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleReset = () => {
    setResult(null);
    setTitle('');
    setValues({});
  };

  // 表单区域（按入口节点与表单加载态分派）
  const renderFormSection = () => {
    if (!definitionId) {
      return <Alert type="info" showIcon message="请先选择流程定义" />;
    }
    if (detailLoading) {
      return <Skeleton active />;
    }
    if (!entryNode) {
      return (
        <Alert
          type="info"
          showIcon
          message="未解析出入口审批节点"
          description="该定义无审批节点或结构不可解析，仅需填写业务标题即可发起。"
        />
      );
    }
    if (!entryNode.formId) {
      return (
        <Alert
          type="info"
          showIcon
          message={`入口审批节点「${entryNode.name ?? entryNode.key}」未绑定表单`}
          description="仅需填写业务标题即可发起。"
        />
      );
    }
    if (formLoading) {
      return <Skeleton active />;
    }
    if (formError) {
      return (
        <Alert
          type="warning"
          showIcon
          message="表单加载失败，可不填表单直接发起"
          description={formError}
        />
      );
    }
    if (!formDefinition) {
      return <Empty description="表单不存在" />;
    }
    return (
      <Space direction="vertical" size="small" style={{ width: '100%' }}>
        <Text strong>表单信息：{formDefinition.name}</Text>
        <FormRenderer
          fields={formDefinition.fields ?? []}
          value={values}
          onChange={handleValuesChange}
          authToken={getToken() ?? undefined}
          apiBaseUrl={import.meta.env.VITE_API_BASE_URL ?? ''}
        />
      </Space>
    );
  };

  if (result) {
    return (
      <Card>
        <Result
          status="success"
          title="流程发起成功"
          subTitle={`业务标题：${result.title}`}
          extra={[
            <Button type="primary" key="view" onClick={() => navigate('/instances')}>
              查看实例
            </Button>,
            <Button key="again" onClick={handleReset}>
              再发起一单
            </Button>,
          ]}
        />
      </Card>
    );
  }

  return (
    <Card title="流程发起">
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <div>
          <Text strong>选择流程定义</Text>
          <Select
            style={{ display: 'block', width: 400, marginTop: 8 }}
            placeholder="请选择流程定义"
            loading={loadingDefs}
            value={definitionId}
            options={definitionOptions}
            showSearch
            optionFilterProp="label"
            allowClear
            onChange={(value: string | undefined) => setDefinitionId(value)}
          />
        </div>

        <div>
          <Text strong>业务标题</Text>
          <Input
            style={{ display: 'block', width: 480, marginTop: 8 }}
            placeholder="请输入业务标题"
            value={title}
            maxLength={128}
            onChange={(event) => setTitle(event.target.value)}
          />
        </div>

        {renderFormSection()}

        <Button
          type="primary"
          loading={submitting}
          disabled={!definitionId || detailLoading || formLoading}
          onClick={() => void handleSubmit()}
        >
          发起流程
        </Button>
      </Space>
    </Card>
  );
}
