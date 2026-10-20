// 设计器中部画布（F6）：可排序字段卡片 + 12/24 栅格布局。
// 栅格下使用 rectSortingStrategy；整块画布为 droppable，Palette 拖入空白处即追加。

import { Button, Card, Col, Empty, Row, Tag } from 'antd';
import { useDroppable } from '@dnd-kit/core';
import { SortableContext, rectSortingStrategy, useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import FieldPreview from './FieldPreview';
import { CANVAS_DROPPABLE_ID, WIDGET_LABELS, isLayoutField, type DesignerField } from './types';

/** 单个可排序画布字段（整卡为拖拽手柄，点击即选中） */
function CanvasField({
  field,
  selected,
  onSelect,
  onRemove,
}: {
  field: DesignerField;
  selected: boolean;
  onSelect: (uid: string) => void;
  onRemove: (uid: string) => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: field._uid,
    data: { type: 'field' },
  });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };
  return (
    <Col span={field.colSpan ?? 24}>
      <div ref={setNodeRef} style={style}>
        <Card
          size="small"
          hoverable
          style={{
            borderColor: selected ? '#1677ff' : undefined,
            marginBottom: 12,
            boxShadow: selected ? '0 0 0 2px rgba(22,119,255,0.15)' : undefined,
          }}
          {...attributes}
          {...listeners}
          onClick={() => onSelect(field._uid)}
          title={<span style={{ cursor: 'move' }}>{field.fieldLabel || '未命名字段'}</span>}
          extra={
            <span onClick={(event) => event.stopPropagation()}>
              <Tag>{field.widgetType ? WIDGET_LABELS[field.widgetType] : ''}</Tag>
              {!isLayoutField(field) ? (
                <Tag color="processing">{field.colSpan === 12 ? '半宽' : '整宽'}</Tag>
              ) : null}
              <Button type="text" size="small" danger onClick={() => onRemove(field._uid)}>
                删除
              </Button>
            </span>
          }
        >
          <FieldPreview field={field} />
        </Card>
      </div>
    </Col>
  );
}

interface CanvasProps {
  fields: DesignerField[];
  selectedUid: string | null;
  onSelect: (uid: string) => void;
  onRemove: (uid: string) => void;
}

export default function Canvas({ fields, selectedUid, onSelect, onRemove }: CanvasProps) {
  const { setNodeRef, isOver } = useDroppable({ id: CANVAS_DROPPABLE_ID });
  return (
    <div
      ref={setNodeRef}
      style={{
        minHeight: 420,
        padding: 12,
        border: `1px dashed ${isOver ? '#1677ff' : '#d9d9d9'}`,
        borderRadius: 6,
        background: '#fafafa',
      }}
    >
      <SortableContext items={fields.map((field) => field._uid)} strategy={rectSortingStrategy}>
        <Row gutter={16}>
          {fields.map((field) => (
            <CanvasField
              key={field._uid}
              field={field}
              selected={selectedUid === field._uid}
              onSelect={onSelect}
              onRemove={onRemove}
            />
          ))}
        </Row>
      </SortableContext>
      {fields.length === 0 ? (
        <Empty description="从左侧拖拽组件到此处" style={{ marginTop: 80 }} />
      ) : null}
    </div>
  );
}
