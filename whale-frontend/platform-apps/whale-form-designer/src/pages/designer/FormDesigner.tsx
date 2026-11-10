// 拖拽式表单设计器主组件（F6）：三栏布局 + @dnd-kit 拖拽 + 保存/发布/预览/填报。
// 数据源：GET /form/definition/{id}（含关联列冗余回填）；列来源：GET /form/table-definition/{tid}。
// 保存语义：PUT/POST /form/definition 携带 tableDefinitionId + fields（后端编译 schema_json 快照）。
// 后端约束规避：新建表单视图时（FormList）先建「空定义」，再带 ?tableDefinitionId= 进入本页，
//   由本页在保存时以元数据模型（tableDefinitionId + fields）补齐并落库。
// 拆分：数据加载见 useDesignerData，撤销/重做见 useFieldHistory，纯函数见 designerUtils，
//   顶栏见 Toolbar，预览抽屉见 PreviewDrawer。

import { App, Card, Col, Row, Space, Spin } from 'antd';
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import { arrayMove } from '@dnd-kit/sortable';
import { useCallback, useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { formApi, type FormDefinition, type Id } from '../../api/form';
import Canvas from './Canvas';
import Palette from './Palette';
import PropertyPanel from './PropertyPanel';
import PreviewDrawer from './PreviewDrawer';
import Toolbar from './Toolbar';
import { buildField, toSaveField, validateCanvas } from './designerUtils';
import { useDesignerData } from './useDesignerData';
import { useFieldHistory } from './useFieldHistory';
import { CANVAS_DROPPABLE_ID, type DesignerField, type PalettePayload } from './types';

export default function FormDesigner() {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const queryTableId = searchParams.get('tableDefinitionId');
  // 路由参数即后端雪花 ID 的字符串形式，禁止 Number() 转换（>2^53 会丢精度）
  const definitionId = id ?? null;

  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);

  // 画布选中与拖拽拖影文案、预览抽屉
  const [selectedUid, setSelectedUid] = useState<string | null>(null);
  const [activeLabel, setActiveLabel] = useState<string | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);

  const { fields, resetFields, commit, undo, redo, canUndo, canRedo } = useFieldHistory();
  // 稳定引用：加载 effect 的依赖项，避免每次渲染重新触发加载
  const resetSelection = useCallback(() => setSelectedUid(null), []);
  const {
    loading,
    definition,
    code,
    setCode,
    name,
    setName,
    tableId,
    setTableId,
    tableOptions,
    columns,
    columnsById,
  } = useDesignerData({
    definitionId,
    queryTableId,
    resetFields,
    resetSelection,
  });

  const usedColumnMetaIds = useMemo(
    () =>
      new Set(
        fields
          .filter((field) => field.columnMetaId != null)
          .map((field) => field.columnMetaId as Id),
      ),
    [fields],
  );
  const selectedField = useMemo(
    () => fields.find((field) => field._uid === selectedUid) ?? null,
    [fields, selectedUid],
  );

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }));

  const handleDragStart = (event: DragStartEvent) => {
    const data = event.active.data.current;
    if (data?.type === 'palette') {
      setActiveLabel((data.payload as PalettePayload).fieldLabel);
      return;
    }
    const field = fields.find((item) => item._uid === event.active.id);
    setActiveLabel(field?.fieldLabel ?? null);
  };

  const handleDragEnd = (event: DragEndEvent) => {
    setActiveLabel(null);
    const { active, over } = event;
    if (!over) {
      return;
    }
    const activeData = active.data.current;

    // 从调色板拖入
    if (activeData?.type === 'palette') {
      const payload = activeData.payload as PalettePayload;
      if (payload.columnMetaId != null && usedColumnMetaIds.has(payload.columnMetaId)) {
        message.warning('该列已在画布中，请勿重复添加');
        return;
      }
      const created = buildField(payload, columnsById);
      commit((previous) => {
        const next = [...previous];
        const index =
          over.id === CANVAS_DROPPABLE_ID
            ? next.length
            : next.findIndex((field) => field._uid === over.id);
        next.splice(index < 0 ? next.length : index, 0, created);
        return next;
      });
      setSelectedUid(created._uid);
      return;
    }

    // 画布内排序
    if (active.id !== over.id) {
      commit((previous) => {
        const from = previous.findIndex((field) => field._uid === active.id);
        const to =
          over.id === CANVAS_DROPPABLE_ID
            ? previous.length - 1
            : previous.findIndex((field) => field._uid === over.id);
        if (from < 0 || to < 0 || from === to) {
          return previous;
        }
        return arrayMove(previous, from, to);
      });
      setSelectedUid(String(active.id));
    }
  };

  const handleRemove = (uid: string) => {
    commit((previous) => previous.filter((field) => field._uid !== uid));
    setSelectedUid((current) => (current === uid ? null : current));
  };

  const handlePatchField = (patch: Partial<DesignerField>) => {
    if (!selectedUid) {
      return;
    }
    commit((previous) =>
      previous.map((field) => (field._uid === selectedUid ? { ...field, ...patch } : field)),
    );
  };

  /** 切换所属表定义：已有数据字段归属旧表，需确认后清理 */
  const handleTableChange = (value: Id) => {
    const hasDataField = fields.some((field) => field.columnMetaId != null);
    if (!hasDataField) {
      setTableId(value);
      return;
    }
    modal.confirm({
      title: '切换所属表定义',
      content: '切换会移除画布中已放置的数据字段（列归属不同表），布局字段保留。是否继续？',
      okText: '确认切换',
      cancelText: '取消',
      onOk: () => {
        commit((previous) => previous.filter((field) => field.columnMetaId == null));
        setSelectedUid(null);
        setTableId(value);
      },
    });
  };

  const handleAddColumn = () => {
    modal.confirm({
      title: '新增列',
      content:
        '列元数据需在「表定义管理」中维护。跳转前请先保存当前设计，否则未保存的画布改动会丢失。',
      okText: '去表定义管理',
      cancelText: '取消',
      onOk: () => navigate('/tables'),
    });
  };

  /** 持久化画布并返回表单 ID（新建返回新 ID；编辑返回原 ID） */
  const persist = async (): Promise<Id> => {
    const invalid = validateCanvas(fields);
    if (invalid) {
      throw new Error(invalid);
    }
    if (!code.trim()) {
      throw new Error('请输入表单编码');
    }
    if (!name.trim()) {
      throw new Error('请输入表单名称');
    }
    if (tableId === null) {
      throw new Error('请选择所属表定义');
    }
    const payload: FormDefinition = {
      code: code.trim(),
      name: name.trim(),
      tableDefinitionId: tableId,
      version: definition?.version ?? 1,
      status: definition?.status ?? 1,
      fields: fields.map((field, index) => toSaveField(field, index)),
    };
    if (definitionId !== null) {
      await formApi.update(definitionId, payload);
      return definitionId;
    }
    const created = await formApi.create(payload);
    if (created.id === undefined) {
      throw new Error('创建失败：未返回表单 ID');
    }
    return created.id;
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const savedId = await persist();
      message.success('保存成功');
      if (definitionId === null) {
        // 新建完成：切换到带 ID 的路由，触发重新加载（后续保存走 PUT）
        navigate(`/design/${savedId}?tableDefinitionId=${tableId}`, { replace: true });
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handlePublish = async () => {
    setPublishing(true);
    try {
      // 发布前先持久化，确保表定义与字段快照一致；元数据模型发布幂等
      const savedId = await persist();
      await formApi.publish(savedId);
      message.success('发布成功');
      if (definitionId === null) {
        navigate(`/design/${savedId}?tableDefinitionId=${tableId}`, { replace: true });
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '发布失败');
    } finally {
      setPublishing(false);
    }
  };

  const handleRender = () => {
    if (definitionId === null) {
      message.warning('请先保存后再填报');
      return;
    }
    navigate(`/render/${definitionId}`);
  };

  return (
    <Spin spinning={loading}>
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Toolbar
          code={code}
          name={name}
          tableId={tableId}
          tableOptions={tableOptions}
          saving={saving}
          publishing={publishing}
          canUndo={canUndo}
          canRedo={canRedo}
          onCodeChange={setCode}
          onNameChange={setName}
          onTableChange={handleTableChange}
          onUndo={undo}
          onRedo={redo}
          onSave={() => void handleSave()}
          onPublish={() => void handlePublish()}
          onPreview={() => setPreviewOpen(true)}
          onRender={handleRender}
          onBack={() => navigate('/forms')}
        />

        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragStart={handleDragStart}
          onDragEnd={handleDragEnd}
        >
          <Row gutter={12}>
            <Col span={6}>
              <Card size="small" title="组件面板">
                <Palette
                  columns={columns}
                  usedColumnMetaIds={usedColumnMetaIds}
                  onAddColumn={handleAddColumn}
                />
              </Card>
            </Col>
            <Col span={12}>
              <Card size="small" title="表单画布">
                <Canvas
                  fields={fields}
                  selectedUid={selectedUid}
                  onSelect={setSelectedUid}
                  onRemove={handleRemove}
                />
              </Card>
            </Col>
            <Col span={6}>
              <Card size="small" title="属性设置">
                <PropertyPanel
                  field={selectedField}
                  columnsById={columnsById}
                  onChange={handlePatchField}
                />
              </Card>
            </Col>
          </Row>
          <DragOverlay>
            {activeLabel ? (
              <Card size="small" style={{ cursor: 'grabbing' }}>
                {activeLabel}
              </Card>
            ) : null}
          </DragOverlay>
        </DndContext>
      </Space>

      <PreviewDrawer open={previewOpen} onClose={() => setPreviewOpen(false)} fields={fields} />
    </Spin>
  );
}
