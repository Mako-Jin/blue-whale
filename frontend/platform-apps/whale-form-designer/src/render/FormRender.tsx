// FormRender（F7）：表单填报页（路由 /render/:id）
// 加载 GET /form/definition/{id} → <FormRenderer> 渲染 → 提交前序列化（键为 columnCode，空值不下发）
// → POST /form/data { definitionId, dataJson }。

import { useEffect, useState } from 'react';
import { App, Button, Card, Empty, Skeleton } from 'antd';
import { useParams } from 'react-router-dom';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { formApi, formDataApi, type FieldConfig, type FormDefinition, type WidgetType } from '../api/form';
import FormRenderer from './FormRenderer';

/** 纯布局控件集合（无关联列，不参与提交） */
const LAYOUT_WIDGETS: readonly WidgetType[] = ['title', 'divider', 'text-note'];

/** 是否纯布局字段 */
function isLayoutWidget(widgetType?: WidgetType): boolean {
  return widgetType !== undefined && LAYOUT_WIDGETS.includes(widgetType);
}

/** 空值判定：undefined / null / 空字符串 / 空数组不下发 */
function isEmptyValue(value: unknown): boolean {
  if (value === undefined || value === null || value === '') {
    return true;
  }
  return Array.isArray(value) && value.length === 0;
}

/** 浅比较：受控值回写时未变化则保留原引用，避免无谓重渲染 */
function isSameValues(
  previous: Record<string, unknown>,
  next: Record<string, unknown>,
): boolean {
  const keys = new Set([...Object.keys(previous), ...Object.keys(next)]);
  for (const key of keys) {
    if (previous[key] !== next[key]) {
      return false;
    }
  }
  return true;
}

/** 提交前序列化：仅数据字段，键为 columnCode，空值跳过 */
function serializeValues(
  fields: FieldConfig[],
  values: Record<string, unknown>,
): Record<string, unknown> {
  const payload: Record<string, unknown> = {};
  fields.forEach((field) => {
    const code = field.columnCode;
    if (!code || isLayoutWidget(field.widgetType)) {
      return;
    }
    const current = values[code];
    if (isEmptyValue(current)) {
      return;
    }
    switch (field.widgetType) {
      case 'date':
        payload[code] = dayjs(current as string | number | Date | Dayjs).format('YYYY-MM-DD');
        break;
      case 'datetime':
        payload[code] = dayjs(current as string | number | Date | Dayjs).format(
          'YYYY-MM-DD HH:mm:ss',
        );
        break;
      case 'checkbox':
        payload[code] = Array.isArray(current) ? current.join(',') : String(current);
        break;
      case 'switch':
        payload[code] = current ? 1 : 0;
        break;
      case 'number':
        payload[code] = Number(current);
        break;
      default:
        // text / textarea / select / radio
        payload[code] = String(current);
    }
  });
  return payload;
}

/** 首个未填的必填数据字段（提交按钮在 FormRenderer 之外，故在此做提交前校验） */
function findMissingRequired(
  fields: FieldConfig[],
  values: Record<string, unknown>,
): FieldConfig | undefined {
  return fields.find((field) => {
    const code = field.columnCode;
    return (
      field.required === 1 &&
      code !== undefined &&
      !isLayoutWidget(field.widgetType) &&
      isEmptyValue(values[code])
    );
  });
}

export default function FormRender() {
  const { id } = useParams<{ id: string }>();
  const { message } = App.useApp();
  const [definition, setDefinition] = useState<FormDefinition | null>(null);
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  // 路由参数即后端雪花 ID 的字符串形式，禁止 Number() 转换（>2^53 会丢精度）
  const definitionId = id ?? null;
  const fields = definition?.fields ?? [];

  // 加载表单定义
  useEffect(() => {
    if (!definitionId) {
      setLoading(false);
      message.error('无效的表单 ID');
      return;
    }
    let active = true;
    setLoading(true);
    formApi
      .detail(definitionId)
      .then((data) => {
        if (active) {
          setDefinition(data);
          setValues({});
        }
      })
      .catch((error: unknown) => {
        if (active) {
          message.error(error instanceof Error ? error.message : '表单定义加载失败');
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [definitionId, message]);

  // 受控回写：渲染器的全量值即填报值
  const handleChange = (next: Record<string, unknown>) => {
    setValues((previous) => (isSameValues(previous, next) ? previous : next));
  };

  const handleSubmit = async () => {
    if (!definition || !definitionId) {
      return;
    }
    const missing = findMissingRequired(fields, values);
    if (missing) {
      message.error(`请输入${missing.fieldLabel}`);
      return;
    }
    setSubmitting(true);
    try {
      const payload = serializeValues(fields, values);
      await formDataApi.create({ definitionId, dataJson: JSON.stringify(payload) });
      message.success('提交成功');
      setValues({}); // 重置表单
    } catch (error) {
      message.error(error instanceof Error ? error.message : '提交失败');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <Skeleton active />;
  }

  if (!definition) {
    return <Empty description="表单不存在或加载失败" />;
  }

  return (
    <Card title={definition.name}>
      <FormRenderer fields={fields} value={values} onChange={handleChange} />
      <div style={{ marginTop: 16 }}>
        <Button type="primary" loading={submitting} onClick={() => void handleSubmit()}>
          提交
        </Button>
      </div>
    </Card>
  );
}
