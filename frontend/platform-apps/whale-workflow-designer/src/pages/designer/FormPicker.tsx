// 绑定表单下拉（数据源 GET /form/definition/page?current=1&size=200）：
// 展示表单 name，选中后同时回填节点 formId（字符串）与 formCode。
// 雪花 ID 全链路按 string 处理（禁止 Number(id)）。

import { Select } from 'antd';
import { useEffect, useState } from 'react';
import { formApi } from '../../api/form';

/** 下拉选项：value 为 formId，label 为表单名，code 随选项携带 */
interface FormOption {
  value: string;
  label: string;
  code: string;
}

interface FormPickerProps {
  /** 当前绑定表单 ID（字符串，null 表示未绑定） */
  value: string | null;
  /** 选中 / 清空回调：同时给出 formId 与 formCode */
  onChange: (patch: { formId: string | null; formCode: string | null }) => void;
}

export default function FormPicker({ value, onChange }: FormPickerProps) {
  const [options, setOptions] = useState<FormOption[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    formApi
      .page({ current: 1, size: 200 })
      .then((result) => {
        if (!alive) {
          return;
        }
        const records = (result?.records ?? [])
          .filter((item) => !!item.id)
          .map((item) => ({ value: item.id as string, label: item.name || item.code, code: item.code }));
        setOptions(records);
      })
      .catch(() => {
        if (alive) {
          setOptions([]);
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
  }, []);

  return (
    <Select
      style={{ width: '100%' }}
      placeholder="选择表单（可空）"
      allowClear
      showSearch
      loading={loading}
      optionFilterProp="label"
      value={value ?? undefined}
      options={options}
      onChange={(next: string | undefined) => {
        const matched = options.find((option) => option.value === next);
        onChange({ formId: next ?? null, formCode: matched?.code ?? null });
      }}
    />
  );
}
