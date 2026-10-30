// 数据详情 Drawer（F8）：按字段配置顺序展示「标签: 值」，dataJson 解析失败给出提示。
// 从 FormDataList 拆出的视图组件：只负责详情展示，明细与表单定义的拉取由容器组件负责。

import { useMemo } from 'react';
import { Alert, Descriptions, Drawer, Spin } from 'antd';
import type { FieldConfig, FormDataItem } from '../../../api/form';
import { formatValue } from './filterHelpers';

/** 详情行：一条「标签: 值」 */
interface DetailRow {
  label: string;
  value: string;
}

/** 详情视图：有序行 + 是否解析失败 */
interface DetailView {
  rows: DetailRow[];
  parseError: boolean;
}

/**
 * 组装详情展示行：
 * - fields 非空：按 fieldConfigs 中 columnCode 的顺序输出「fieldLabel: 值」，跳过纯布局字段；
 * - fields 为 null（旧模型/列表态）：兜底 JSON.parse(dataJson) 输出键值对，键命中则用中文标签；
 * - dataJson 解析失败：置 parseError。
 */
function buildDetailView(detail: FormDataItem | null, fieldConfigs: FieldConfig[]): DetailView {
  if (!detail) {
    return { rows: [], parseError: false };
  }

  // columnCode -> fieldLabel 映射，供兜底解析时还原中文标签
  const labelMap = new Map<string, string>();
  fieldConfigs.forEach((config) => {
    if (config.columnCode) {
      labelMap.set(config.columnCode, config.fieldLabel || config.columnCode);
    }
  });

  const fields = detail.fields;
  if (fields) {
    const rows: DetailRow[] = [];
    fieldConfigs.forEach((config) => {
      if (!config.columnCode) {
        return;
      }
      rows.push({
        label: config.fieldLabel || config.columnCode,
        value: formatValue(fields[config.columnCode]),
      });
    });
    return { rows, parseError: false };
  }

  // fields 为空：兜底解析落库的 dataJson
  try {
    const parsed = JSON.parse(detail.dataJson || '{}') as Record<string, unknown>;
    const rows: DetailRow[] = Object.entries(parsed).map(([key, value]) => ({
      label: labelMap.get(key) ?? key,
      value: formatValue(value),
    }));
    return { rows, parseError: false };
  } catch {
    return { rows: [], parseError: true };
  }
}

interface DetailDrawerProps {
  open: boolean;
  /** 明细与表单定义并行拉取中 */
  loading: boolean;
  /** 数据明细（打开后拉取完成前为 null） */
  detail: FormDataItem | null;
  /** 表单定义的字段配置（标签映射用） */
  fieldConfigs: FieldConfig[];
  onClose: () => void;
}

export default function DetailDrawer({
  open,
  loading,
  detail,
  fieldConfigs,
  onClose,
}: DetailDrawerProps) {
  const detailView = useMemo(() => buildDetailView(detail, fieldConfigs), [detail, fieldConfigs]);

  return (
    <Drawer
      title={detail ? `数据详情 #${detail.id}` : '数据详情'}
      width={560}
      open={open}
      onClose={onClose}
    >
      <Spin spinning={loading}>
        {detailView.parseError ? (
          <Alert
            type="warning"
            showIcon
            message="无法解析数据内容"
            description="dataJson 不是合法 JSON，无法展示明细。"
          />
        ) : detailView.rows.length === 0 ? (
          <div>暂无字段数据</div>
        ) : (
          <Descriptions
            column={1}
            bordered
            size="small"
            items={detailView.rows.map((row, index) => ({
              key: `${index}`,
              label: row.label,
              children: row.value,
            }))}
          />
        )}
      </Spin>
    </Drawer>
  );
}
