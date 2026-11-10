// 设计器左侧组件面板（F6）：@dnd-kit 拖拽源。
// 数据字段来自当前表定义的列元数据；布局字段为固定三种（标题/分割线/说明）。
// 「新增列」跳转表定义管理页 —— 列元数据归属表定义层，本页不落库列（跨模块边界）。

import { Button, Divider, Empty, Tag, Tooltip, Typography } from 'antd';
import { useDraggable } from '@dnd-kit/core';
import type { ReactNode } from 'react';
import type { ColumnMeta, Id } from '../../api/form';
import {
  DATA_TYPE_LABELS,
  LAYOUT_LABELS,
  LAYOUT_WIDGETS,
  WIDGET_LABELS,
  defaultWidget,
  type PalettePayload,
} from './types';

/** 调色板条目：可拖拽源（使用 DragOverlay 展示拖影，故源节点不位移） */
function PaletteItem({
  id,
  payload,
  disabled,
  children,
}: {
  id: string;
  payload: PalettePayload;
  disabled?: boolean;
  children: ReactNode;
}) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id,
    data: { type: 'palette', payload },
    disabled,
  });
  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      style={{
        padding: '6px 8px',
        marginBottom: 6,
        border: '1px solid #f0f0f0',
        borderRadius: 4,
        background: '#fff',
        cursor: disabled ? 'not-allowed' : 'grab',
        opacity: disabled ? 0.5 : isDragging ? 0.4 : 1,
      }}
    >
      {children}
    </div>
  );
}

interface PaletteProps {
  /** 当前表定义的列（数据字段来源） */
  columns: ColumnMeta[];
  /** 画布已引用的列 ID（重复拖入将被拒绝） */
  usedColumnMetaIds: Set<Id>;
  /** 新增列（跳转表定义管理页） */
  onAddColumn: () => void;
}

export default function Palette({ columns, usedColumnMetaIds, onAddColumn }: PaletteProps) {
  return (
    <div>
      <Divider orientation="left" style={{ marginTop: 0 }}>
        数据字段
      </Divider>
      {columns.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请先选择所属表定义" />
      ) : (
        columns.map((column) => {
          const used = column.id !== undefined && usedColumnMetaIds.has(column.id);
          return (
            <PaletteItem
              key={column.id ?? column.columnCode}
              id={`palette-col-${column.id ?? column.columnCode}`}
              disabled={used || column.id === undefined}
              payload={{
                columnMetaId: column.id ?? null,
                fieldLabel: column.columnName || column.columnCode,
                widgetType: defaultWidget(column.dataType),
              }}
            >
              <Tooltip title={used ? '该列已在画布中' : '拖拽到画布'}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                  <span>
                    {column.columnName || column.columnCode}
                    <Typography.Text type="secondary" style={{ marginLeft: 6, fontSize: 12 }}>
                      {column.columnCode}
                    </Typography.Text>
                  </span>
                  <Tag color={used ? 'default' : 'blue'}>
                    {used ? '已使用' : DATA_TYPE_LABELS[column.dataType]}
                  </Tag>
                </div>
              </Tooltip>
            </PaletteItem>
          );
        })
      )}
      <Button type="dashed" block onClick={onAddColumn}>
        + 新增列
      </Button>

      <Divider orientation="left">布局字段</Divider>
      {LAYOUT_WIDGETS.map((widget) => (
        <PaletteItem
          key={widget}
          id={`palette-layout-${widget}`}
          payload={{ columnMetaId: null, fieldLabel: LAYOUT_LABELS[widget], widgetType: widget }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>{LAYOUT_LABELS[widget]}</span>
            <Tag>{WIDGET_LABELS[widget]}</Tag>
          </div>
        </PaletteItem>
      ))}
    </div>
  );
}
