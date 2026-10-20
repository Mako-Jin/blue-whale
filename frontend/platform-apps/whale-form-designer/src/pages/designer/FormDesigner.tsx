// 拖拽式表单设计器主组件（F6）：三栏布局 + @dnd-kit 拖拽 + 保存/发布/预览/填报。
// 数据源：GET /form/definition/{id}（含关联列冗余回填）；列来源：GET /form/table-definition/{tid}。
// 保存语义：PUT/POST /form/definition 携带 tableDefinitionId + fields（后端编译 schema_json 快照）。
// 后端约束规避：新建表单视图时（FormList）先建「空定义」，再带 ?tableDefinitionId= 进入本页，
//   由本页在保存时以元数据模型（tableDefinitionId + fields）补齐并落库。

import { App, Button, Card, Col, Drawer, Input, Row, Select, Space, Spin } from 'antd';
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
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  formApi,
  tableDefinitionApi,
  type ColumnMeta,
  type FieldConfig,
  type FormDefinition,
  type Id,
} from '../../api/form';
import FormRenderer from '../../render/FormRenderer';
import Canvas from './Canvas';
import Palette from './Palette';
import PropertyPanel from './PropertyPanel';
import {
  CANVAS_DROPPABLE_ID,
  isLayoutField,
  nextUid,
  type DesignerField,
  type PalettePayload,
} from './types';

/** 画布字段 → 保存请求字段（剥离前端 _uid 与冗余列信息；sortNo 以画布顺序为准） */
function toSaveField(field: DesignerField, index: number): FieldConfig {
  const layout = isLayoutField(field);
  return {
    columnMetaId: field.columnMetaId ?? null,
    fieldLabel: field.fieldLabel,
    widgetType: field.widgetType,
    showInList: field.showInList ?? 1,
    showInForm: field.showInForm ?? 1,
    editable: layout ? 1 : (field.editable ?? 1),
    sortNo: index,
    colSpan: field.colSpan ?? 24,
    placeholder: layout ? undefined : field.placeholder,
    defaultValue: layout ? undefined : field.defaultValue,
    validateJson: field.validateJson,
    remark: field.remark,
    status: field.status ?? 1,
    options: layout ? undefined : field.options,
  };
}

/** 撤销 / 重做历史栈深度上限（超出丢弃最旧快照，避免长时间编辑内存膨胀） */
const HISTORY_LIMIT = 50;

/** 保存前画布校验：至少一个数据字段、同列不重复、显示名非空 */
function validateCanvas(fields: DesignerField[]): string | null {
  const dataFields = fields.filter((field) => !isLayoutField(field));
  if (dataFields.length === 0) {
    return '请至少拖入一个数据字段（纯布局字段不构成表单数据）';
  }
  const ids = dataFields.map((field) => field.columnMetaId as Id);
  if (new Set(ids).size !== ids.length) {
    return '同一列被重复引用，请移除重复的数据字段';
  }
  if (fields.some((field) => !field.fieldLabel || !field.fieldLabel.trim())) {
    return '存在未填写显示名的字段';
  }
  return null;
}

export default function FormDesigner() {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const queryTableId = searchParams.get('tableDefinitionId');
  // 路由参数即后端雪花 ID 的字符串形式，禁止 Number() 转换（>2^53 会丢精度）
  const definitionId = id ?? null;

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [definition, setDefinition] = useState<FormDefinition | null>(null);

  // 顶栏元信息
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [tableId, setTableId] = useState<Id | null>(null);
  const [tableOptions, setTableOptions] = useState<{ value: Id; label: string }[]>([]);

  // 列来源与画布
  const [columns, setColumns] = useState<ColumnMeta[]>([]);
  const [fields, setFields] = useState<DesignerField[]>([]);
  const [selectedUid, setSelectedUid] = useState<string | null>(null);

  // 拖拽拖影文案与预览抽屉
  const [activeLabel, setActiveLabel] = useState<string | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);

  const columnsById = useMemo(
    () =>
      new Map(
        columns
          .filter((column) => column.id !== undefined)
          .map((column) => [column.id as Id, column]),
      ),
    [columns],
  );
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

  // 撤销 / 重做历史栈（R4）：past / future 存于 useRef，入栈出栈不触发渲染；
  // historyVersion 为变更计数，仅用于驱动工具栏按钮禁用态重渲染（栈内容 React 无法感知）；
  // fieldsRef 与画布状态同步维护「当前值」，供 commit / 撤销 / 重做在同步上下文中读取快照。
  const [historyVersion, setHistoryVersion] = useState(0);
  const pastRef = useRef<DesignerField[][]>([]);
  const futureRef = useRef<DesignerField[][]>([]);
  const fieldsRef = useRef<DesignerField[]>([]);

  /** 载入服务端表单（首次 / 保存后回填 / 切换表单）或切换表定义清空：以结果作为新历史基点，不入栈且清空 past / future */
  const resetFields = useCallback((initial: DesignerField[]) => {
    fieldsRef.current = initial;
    pastRef.current = [];
    futureRef.current = [];
    setFields(initial);
    setHistoryVersion((version) => version + 1);
  }, []);

  /** 画布字段变更统一入口：先把当前值快照压入 past、清空 future，再落地新值（无实际变化时不入栈） */
  const commit = useCallback(
    (next: DesignerField[] | ((prev: DesignerField[]) => DesignerField[])) => {
      const previous = fieldsRef.current;
      const resolved = typeof next === 'function' ? next(previous) : next;
      if (resolved === previous) {
        return;
      }
      pastRef.current = [...pastRef.current, previous].slice(-HISTORY_LIMIT);
      futureRef.current = [];
      fieldsRef.current = resolved;
      setFields(resolved);
      setHistoryVersion((version) => version + 1);
    },
    [],
  );

  /** 撤销：past 弹栈 → 当前值压入 future → 设为弹栈值 */
  const handleUndo = useCallback(() => {
    const past = pastRef.current;
    if (past.length === 0) {
      return;
    }
    const previous = past[past.length - 1];
    pastRef.current = past.slice(0, -1);
    futureRef.current = [...futureRef.current, fieldsRef.current].slice(-HISTORY_LIMIT);
    fieldsRef.current = previous;
    setFields(previous);
    setHistoryVersion((version) => version + 1);
  }, []);

  /** 重做：future 弹栈 → 当前值压入 past → 设为弹栈值 */
  const handleRedo = useCallback(() => {
    const future = futureRef.current;
    if (future.length === 0) {
      return;
    }
    const next = future[future.length - 1];
    futureRef.current = future.slice(0, -1);
    pastRef.current = [...pastRef.current, fieldsRef.current].slice(-HISTORY_LIMIT);
    fieldsRef.current = next;
    setFields(next);
    setHistoryVersion((version) => version + 1);
  }, []);

  // 以 historyVersion 为依赖，在栈增减后重新计算按钮禁用态
  const canUndo = useMemo(() => pastRef.current.length > 0, [historyVersion]);
  const canRedo = useMemo(() => futureRef.current.length > 0, [historyVersion]);

  // 快捷键（Windows 口径）：Ctrl+Z 撤销，Ctrl+Y / Ctrl+Shift+Z 重做；
  // 事件源为输入控件（顶栏输入框、属性面板等）时直接放行，不劫持原生编辑行为。
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (!event.ctrlKey) {
        return;
      }
      const target = event.target as HTMLElement | null;
      if (
        target !== null &&
        (target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.tagName === 'SELECT' ||
          target.isContentEditable)
      ) {
        return;
      }
      const key = event.key.toLowerCase();
      if (key === 'z' && !event.shiftKey) {
        event.preventDefault();
        handleUndo();
        return;
      }
      if (key === 'y' || (key === 'z' && event.shiftKey)) {
        event.preventDefault();
        handleRedo();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [handleUndo, handleRedo]);

  // 加载表单定义与画布（新建 / 编辑），并解析所属表定义
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    const resolve = async () => {
      const queryTable = queryTableId;
      try {
        if (definitionId !== null) {
          const detail = await formApi.detail(definitionId);
          if (cancelled) {
            return;
          }
          setDefinition(detail);
          setCode(detail.code);
          setName(detail.name);
          setTableId(detail.tableDefinitionId ?? queryTable ?? null);
          // 服务端加载结果作为历史基点：不入历史并清空 past / future
          resetFields(
            (detail.fields ?? [])
              .slice()
              .sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0))
              .map((field) => ({ ...field, _uid: nextUid() })),
          );
        } else {
          setDefinition(null);
          setCode('');
          setName('');
          setTableId(queryTable ?? null);
          resetFields([]);
        }
        setSelectedUid(null);
      } catch (error) {
        if (!cancelled) {
          message.error(error instanceof Error ? error.message : '表单定义加载失败');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };
    void resolve();
    return () => {
      cancelled = true;
    };
  }, [definitionId, queryTableId, message, resetFields]);

  // 加载表定义列（作为调色板数据字段来源）
  useEffect(() => {
    if (tableId === null) {
      setColumns([]);
      return;
    }
    let cancelled = false;
    tableDefinitionApi
      .detail(tableId)
      .then((table) => {
        if (!cancelled) {
          setColumns(table.columns ?? []);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          message.error(error instanceof Error ? error.message : '表定义加载失败');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [tableId, message]);

  // 加载「所属表定义」下拉数据
  useEffect(() => {
    let cancelled = false;
    formApi
      .tables({ current: 1, size: 200 })
      .then((result) => {
        if (!cancelled) {
          setTableOptions(
            result.records
              .filter((table) => table.id !== undefined)
              .map((table) => ({
                value: table.id as Id,
                label: `${table.tableCode} - ${table.tableName}`,
              })),
          );
        }
      })
      .catch(() => {
        // 下拉数据失败不阻塞设计器主流程
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }));

  /** 调色板载荷 → 画布字段（回填关联列信息，便于预览与属性面板只读展示） */
  const buildField = useCallback(
    (payload: PalettePayload): DesignerField => {
      const column = payload.columnMetaId != null ? columnsById.get(payload.columnMetaId) : undefined;
      return {
        _uid: nextUid(),
        columnMetaId: payload.columnMetaId,
        fieldLabel: payload.fieldLabel,
        widgetType: payload.widgetType,
        showInList: 1,
        showInForm: 1,
        editable: 1,
        colSpan: 24,
        status: 1,
        placeholder: column ? `请输入${column.columnName || column.columnCode}` : undefined,
        options:
          payload.widgetType === 'select' ||
          payload.widgetType === 'radio' ||
          payload.widgetType === 'checkbox'
            ? []
            : undefined,
        columnCode: column?.columnCode,
        dataType: column?.dataType,
        required: column?.required,
        maxLength: column?.length,
      };
    },
    [columnsById],
  );

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
      const created = buildField(payload);
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
        <Card size="small">
          <Row gutter={12} align="middle">
            <Col span={5}>
              <Input
                addonBefore="编码"
                value={code}
                onChange={(event) => setCode(event.target.value)}
              />
            </Col>
            <Col span={5}>
              <Input
                addonBefore="名称"
                value={name}
                onChange={(event) => setName(event.target.value)}
              />
            </Col>
            <Col span={7}>
              <Select
                style={{ width: '100%' }}
                placeholder="所属表定义"
                value={tableId ?? undefined}
                onChange={handleTableChange}
                options={tableOptions}
                showSearch
                optionFilterProp="label"
              />
            </Col>
            <Col span={7}>
              <Space>
                <Button disabled={!canUndo} title="撤销（Ctrl+Z）" onClick={handleUndo}>
                  撤销
                </Button>
                <Button
                  disabled={!canRedo}
                  title="重做（Ctrl+Y / Ctrl+Shift+Z）"
                  onClick={handleRedo}
                >
                  重做
                </Button>
                <Button type="primary" loading={saving} onClick={() => void handleSave()}>
                  保存
                </Button>
                <Button loading={publishing} onClick={() => void handlePublish()}>
                  发布
                </Button>
                <Button onClick={() => setPreviewOpen(true)}>预览</Button>
                <Button onClick={handleRender}>填报</Button>
                <Button onClick={() => navigate('/forms')}>返回</Button>
              </Space>
            </Col>
          </Row>
        </Card>

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

      <Drawer
        open={previewOpen}
        width={720}
        title="表单预览"
        onClose={() => setPreviewOpen(false)}
        destroyOnClose
      >
        <FormRenderer fields={fields} />
        <div style={{ marginTop: 8, color: '#999' }}>
          预览为交互态，仅用于检验布局与控件；实际提交以填报页为准。
        </div>
      </Drawer>
    </Spin>
  );
}
