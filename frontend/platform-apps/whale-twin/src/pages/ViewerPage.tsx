// 3D 查看器页：左实体树 + 右 three.js 画布 + 数据绑定与实时联动（M2）
//
// 渲染规则：
//   - 有 modelFileId 的实体：从 /file/{id}/preview 拉流为 Blob → GLTFLoader 加载；
//   - 无模型的实体：占位立方体；
//   - transformJson 约定：{"position":[x,y,z],"rotation":[x,y,z],"scale":[x,y,z]}
//     （rotation 为弧度欧拉角；缺省分量按 0/1 处理）；
//   - 相机：OrbitControls（three/addons），鼠标拖拽旋转、滚轮缩放。
//
// 实时联动（M2）：
//   - 订阅 /twin/scenes/{id}/stream（fetch 读流手工分帧，EventSource 带不了头）；
//   - binding 事件按 propertyMap 的 thresholds 驱动实体 emissive 变色：
//     离线灰 / 低于 t1 绿 / t1~t2 橙 / 高于 t2 红 / 在线无阈值青色底光；
//   - 右上角实时面板展示各绑定最新值。

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  App,
  Badge,
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Switch,
  Tree,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import { WhaleSceneManager, loadGltf } from '@bluewhale/whale-common-three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { Box3, BoxGeometry, Color, Group, Mesh, MeshStandardMaterial, Object3D, Vector3 } from 'three';
import {
  bindingApi,
  entityApi,
  fetchIotDevices,
  fetchModelUrl,
  revokeModelUrl,
  sceneApi,
  subscribeSceneStream,
  type BindingSaveRequest,
  type BindingStateVO,
  type BindingVO,
  type EntitySaveRequest,
  type EntityVO,
  type IotDeviceItem,
  type SceneVO,
} from '../api/twin';

/** 变换 JSON 约定结构（分量可缺省） */
interface TransformJson {
  position?: [number, number, number];
  rotation?: [number, number, number];
  scale?: [number, number, number];
}

/** 解析变换 JSON（非法/为空返回缺省值） */
function parseTransform(json: string | null | undefined): TransformJson {
  if (!json) return {};
  try {
    const parsed = JSON.parse(json) as TransformJson;
    return typeof parsed === 'object' && parsed !== null ? parsed : {};
  } catch {
    return {};
  }
}

/** 把变换 JSON 应用到 three 对象（缺省分量不覆盖） */
function applyTransform(target: Object3D, json: string | null | undefined): void {
  const transform = parseTransform(json);
  if (transform.position) target.position.set(...transform.position);
  if (transform.rotation) target.rotation.set(...transform.rotation);
  if (transform.scale) target.scale.set(...transform.scale);
}

/** 属性映射条目（值可为字符串状态键，或 {stateKey, thresholds} 对象） */
interface PropertyMapEntry {
  stateKey?: string;
  thresholds?: number[];
}

/** 解析属性映射 JSON（非法返回空对象，不打断联动） */
function parsePropertyMap(json: string | null | undefined): Record<string, PropertyMapEntry> {
  if (!json) return {};
  try {
    const parsed = JSON.parse(json) as unknown;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    return parsed as Record<string, PropertyMapEntry>;
  } catch {
    return {};
  }
}

/** 状态 → 自发光色：null 表示恢复本色 */
function resolveEmissive(
  state: BindingStateVO | null,
  mapping: Record<string, PropertyMapEntry>,
): number | null {
  if (!state) return null;
  if (!state.online) return 0x606060;
  for (const entry of Object.values(mapping)) {
    const key = entry?.stateKey;
    if (!key) continue;
    const value = state.values?.[key];
    const thresholds = entry?.thresholds;
    if (typeof value === 'number' && Array.isArray(thresholds) && thresholds.length === 2) {
      if (value < thresholds[0]) return 0x2ecc71;
      if (value < thresholds[1]) return 0xe67e22;
      return 0xe74c3c;
    }
  }
  return 0x18a999;
}

/** antd 树节点装配（附加原始实体引用便于选中编辑） */
interface EntityNode extends DataNode {
  key: string;
  title: string;
  entity: EntityVO;
  children: EntityNode[];
}

function toTreeNodes(list: EntityVO[]): EntityNode[] {
  return list.map((entity) => ({
    key: entity.id,
    title: `${entity.name}（${entity.entityCode}）`,
    entity,
    children: toTreeNodes(entity.children ?? []),
  }));
}

/** 扁平化实体树（供父节点下拉选择；树节点内持有原始实体引用） */
function flatten(list: EntityNode[], depth = 0): Array<{ entity: EntityVO; depth: number }> {
  const result: Array<{ entity: EntityVO; depth: number }> = [];
  for (const node of list) {
    result.push({ entity: node.entity, depth });
    result.push(...flatten(node.children ?? [], depth + 1));
  }
  return result;
}

/** 实体表单值（与 EntitySaveRequest 对齐） */
interface EntityFormValues {
  entityCode: string;
  name: string;
  parentId?: string | null;
  modelFileId?: string | null;
  transformJson?: string;
  sortOrder?: number;
}

/** 绑定表单值（bindKey 按来源拆成设备ID/接口路径两个控件） */
interface BindingFormValues {
  entityId: string;
  sourceType: BindingVO['sourceType'];
  deviceId?: string;
  apiPath?: string;
  propertyMap?: string;
  pollIntervalMs?: number;
  enabled?: boolean;
}

export default function ViewerPage({ initialSceneId }: { initialSceneId: string | null }) {
  const { message } = App.useApp();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const managerRef = useRef<WhaleSceneManager | null>(null);
  const controlsRef = useRef<OrbitControls | null>(null);
  const rootGroupRef = useRef<Group | null>(null);
  const objectUrlsRef = useRef<string[]>([]);
  /** 实体ID → 3D 宿主对象（实时变色用） */
  const holdersRef = useRef<Map<string, Group>>(new Map());
  const streamAbortRef = useRef<AbortController | null>(null);

  const [scenes, setScenes] = useState<SceneVO[]>([]);
  const [sceneId, setSceneId] = useState<string | null>(initialSceneId);
  const [tree, setTree] = useState<EntityNode[]>([]);
  const [selected, setSelected] = useState<EntityVO | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [loadingModel, setLoadingModel] = useState(false);
  const [form] = Form.useForm<EntityFormValues>();

  // ---- M2：绑定与实时态 ----
  const [bindings, setBindings] = useState<BindingVO[]>([]);
  const [devices, setDevices] = useState<IotDeviceItem[]>([]);
  const [liveStates, setLiveStates] = useState<Record<string, BindingStateVO>>({});
  const [streaming, setStreaming] = useState(false);
  const [bindingDrawerOpen, setBindingDrawerOpen] = useState(false);
  const [editingBinding, setEditingBinding] = useState<BindingVO | null>(null);
  const [bindingForm] = Form.useForm<BindingFormValues>();

  // 1) 场景下拉数据（含从场景页带入的初始场景）
  useEffect(() => {
    sceneApi
      .page({ current: 1, size: 100 })
      .then((page) => setScenes(page.records ?? []))
      .catch((error) => message.error(`加载场景列表失败：${String(error)}`));
    fetchIotDevices().then(setDevices);
  }, [message]);

  // 2) 实体树
  const loadTree = useCallback(
    async (id: string) => {
      try {
        const data = await entityApi.tree(id);
        setTree(toTreeNodes(data ?? []));
      } catch (error) {
        message.error(`加载实体树失败：${String(error)}`);
      }
    },
    [message],
  );

  // 3) three 初始化（挂载一次；容器随页面布局给满高度）
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const manager = new WhaleSceneManager({ container, backgroundColor: 0x1a2b3c }).init();
    manager.getCamera().position.set(6, 5, 8);
    const controls = new OrbitControls(manager.getCamera(), manager.getRenderer().domElement);
    controls.enableDamping = true;
    const root = new Group();
    manager.getScene().add(root);
    manager.onFrame(() => controls.update());
    manager.start();
    managerRef.current = manager;
    controlsRef.current = controls;
    rootGroupRef.current = root;
    return () => {
      manager.dispose();
      managerRef.current = null;
      controlsRef.current = null;
      rootGroupRef.current = null;
    };
  }, []);

  /** 按绑定映射与实时态给实体上自发光色 */
  const applyLiveColor = useCallback((state: BindingStateVO, propertyMap: string | null | undefined) => {
    const holder = holdersRef.current.get(state.entityId);
    if (!holder) return;
    const emissive = resolveEmissive(state, parsePropertyMap(propertyMap));
    holder.traverse((child) => {
      const mesh = child as Mesh;
      const isMesh = (child as unknown as { isMesh?: boolean }).isMesh === true;
      if (!isMesh) return;
      const material = mesh.material as MeshStandardMaterial | MeshStandardMaterial[];
      const list = Array.isArray(material) ? material : [material];
      for (const mat of list) {
        if (!mat || !(mat as MeshStandardMaterial).emissive) continue;
        const userData = mesh.userData as Record<string, unknown>;
        if (!userData.__baseEmissive) {
          userData.__baseEmissive = mat.emissive.clone();
        }
        if (emissive === null) {
          mat.emissive.copy(userData.__baseEmissive as Color);
        } else {
          mat.emissive.setHex(emissive);
        }
      }
    });
  }, []);

  /** 用一批实时态刷新面板与 3D 颜色 */
  const mergeStates = useCallback(
    (items: BindingStateVO[]) => {
      setLiveStates((previous) => {
        const next = { ...previous };
        for (const item of items) {
          next[item.entityId] = item;
        }
        return next;
      });
      for (const item of items) {
        const binding = bindings.find((item2) => item2.id === item.bindingId);
        applyLiveColor(item, binding?.propertyMap);
      }
    },
    [applyLiveColor, bindings],
  );

  // 4) 场景变化 → 重建场景内容（先清空旧对象与 objectURL）
  const buildSceneObjects = useCallback(
    async (id: string) => {
      const manager = managerRef.current;
      const root = rootGroupRef.current;
      if (!manager || !root) return;
      setLoadingModel(true);
      for (const url of objectUrlsRef.current) revokeModelUrl(url);
      objectUrlsRef.current = [];
      holdersRef.current.clear();
      root.clear();
      try {
        const flat = await entityApi.list(id);
        const placeholder = new BoxGeometry(1, 1, 1);
        for (const entity of flat) {
          const holder = new Group();
          holder.name = `${entity.entityCode}:${entity.name}`;
          holdersRef.current.set(entity.id, holder);
          if (entity.modelFileId) {
            try {
              const url = await fetchModelUrl(entity.modelFileId);
              objectUrlsRef.current.push(url);
              const gltf = await loadGltf(url);
              holder.add(gltf.scene);
            } catch {
              holder.add(new Mesh(placeholder, new MeshStandardMaterial({ color: 0x8b0000 })));
            }
          } else {
            holder.add(new Mesh(placeholder, new MeshStandardMaterial({ color: 0x4c8dbf })));
          }
          applyTransform(holder, entity.transformJson);
          root.add(holder);
        }
        const box = new Box3().setFromObject(root);
        if (!box.isEmpty()) {
          const center = box.getCenter(new Vector3());
          const size = box.getSize(new Vector3()).length();
          const camera = manager.getCamera();
          camera.position.set(center.x + size, center.y + size * 0.6, center.z + size);
          controlsRef.current?.target.copy(center);
          controlsRef.current?.update();
        }
      } catch (error) {
        message.error(`装配 3D 场景失败：${String(error)}`);
      } finally {
        setLoadingModel(false);
      }
    },
    [message],
  );

  const loadBindings = useCallback(
    async (id: string) => {
      try {
        setBindings(await bindingApi.list(id));
      } catch (error) {
        message.error(`加载绑定失败：${String(error)}`);
      }
    },
    [message],
  );

  useEffect(() => {
    if (!sceneId) return;
    void loadTree(sceneId);
    void buildSceneObjects(sceneId);
    void loadBindings(sceneId);
  }, [sceneId, loadTree, buildSceneObjects, loadBindings]);

  // 卸载时释放 objectURL 与实时流
  useEffect(
    () => () => {
      streamAbortRef.current?.abort();
      streamAbortRef.current = null;
      for (const url of objectUrlsRef.current) revokeModelUrl(url);
      objectUrlsRef.current = [];
    },
    [],
  );

  // 5) 实时订阅（订阅前先拉一次快照，避免白屏等 tick）
  const stopStream = useCallback(() => {
    streamAbortRef.current?.abort();
    streamAbortRef.current = null;
    setStreaming(false);
  }, []);

  const startStream = useCallback(async () => {
    if (!sceneId || streaming) return;
    const controller = new AbortController();
    streamAbortRef.current = controller;
    setStreaming(true);
    try {
      const snapshot = await bindingApi.state(sceneId);
      mergeStates(snapshot ?? []);
      await subscribeSceneStream(
        `/twin/scenes/${sceneId}/stream`,
        (event, data) => {
          if (event !== 'binding') return;
          try {
            const parsed = JSON.parse(data) as { items?: BindingStateVO[] };
            if (parsed.items?.length) mergeStates(parsed.items);
          } catch {
            // 半包/脏帧忽略，等下一帧
          }
        },
        controller.signal,
      );
    } catch (error) {
      if (!controller.signal.aborted) {
        message.error(error instanceof Error ? error.message : '实时订阅失败');
      }
    } finally {
      if (streamAbortRef.current === controller) {
        streamAbortRef.current = null;
        setStreaming(false);
      }
    }
  }, [sceneId, streaming, mergeStates, message]);

  // 切场景即断流并清实时面板
  useEffect(() => {
    stopStream();
    setLiveStates({});
    // 清除上一场景的颜色标记
    for (const holder of holdersRef.current.values()) {
      holder.traverse((child) => {
        const mesh = child as Mesh;
        if ((child as unknown as { isMesh?: boolean }).isMesh !== true) return;
        const material = mesh.material as MeshStandardMaterial | MeshStandardMaterial[];
        const list = Array.isArray(material) ? material : [material];
        for (const mat of list) {
          const base = (mesh.userData as Record<string, unknown>).__baseEmissive;
          if (mat && (mat as MeshStandardMaterial).emissive && base) {
            mat.emissive.copy(base as Color);
          }
        }
      });
    }
  }, [sceneId, stopStream]);

  // 6) 实体编辑抽屉
  const openCreate = (parentId: string | null) => {
    setSelected(null);
    form.resetFields();
    form.setFieldsValue({ parentId: parentId ?? null, sortOrder: 0 });
    setDrawerOpen(true);
  };

  const openEdit = (entity: EntityVO) => {
    setSelected(entity);
    form.setFieldsValue({
      entityCode: entity.entityCode,
      name: entity.name,
      parentId: entity.parentId ?? null,
      modelFileId: entity.modelFileId ?? null,
      transformJson: entity.transformJson ?? undefined,
      sortOrder: entity.sortOrder,
    });
    setDrawerOpen(true);
  };

  const submit = async () => {
    if (!sceneId) return;
    const values = await form.validateFields();
    const body: EntitySaveRequest = {
      entityCode: (values.entityCode ?? '').trim(),
      name: (values.name ?? '').trim(),
      parentId: values.parentId ? Number(values.parentId) : null,
      modelFileId: values.modelFileId ? Number(values.modelFileId) : null,
      transformJson: values.transformJson?.trim() || null,
      sortOrder: values.sortOrder ?? 0,
    };
    try {
      if (selected) {
        await entityApi.update(sceneId, selected.id, body);
        message.success('实体已更新');
      } else {
        await entityApi.create(sceneId, body);
        message.success('实体已创建');
      }
      setDrawerOpen(false);
      void loadTree(sceneId);
      void buildSceneObjects(sceneId);
    } catch (error) {
      message.error(`保存失败：${String(error)}`);
    }
  };

  const removeEntity = async (entity: EntityVO) => {
    if (!sceneId) return;
    try {
      await entityApi.remove(sceneId, entity.id);
      message.success('实体已删除');
      setSelected(null);
      void loadTree(sceneId);
      void buildSceneObjects(sceneId);
    } catch (error) {
      message.error(`删除失败：${String(error)}`);
    }
  };

  // 7) 绑定抽屉
  const openBindingCreate = () => {
    setEditingBinding(null);
    bindingForm.resetFields();
    bindingForm.setFieldsValue({
      sourceType: 'iot_device',
      pollIntervalMs: 5000,
      enabled: true,
      entityId: selected?.id,
    });
    setBindingDrawerOpen(true);
  };

  const openBindingEdit = (binding: BindingVO) => {
    setEditingBinding(binding);
    bindingForm.setFieldsValue({
      entityId: binding.entityId,
      sourceType: binding.sourceType,
      deviceId: binding.sourceType === 'iot_device' ? binding.bindKey : undefined,
      apiPath: binding.sourceType === 'internal_api' ? binding.bindKey : undefined,
      propertyMap: binding.propertyMap ?? undefined,
      pollIntervalMs: binding.pollIntervalMs,
      enabled: binding.enabled === 1,
    });
    setBindingDrawerOpen(true);
  };

  const submitBinding = async () => {
    if (!sceneId) return;
    const values = await bindingForm.validateFields();
    const bindKey = values.sourceType === 'iot_device' ? values.deviceId : values.apiPath;
    if (!bindKey) {
      message.error(values.sourceType === 'iot_device' ? '请选择设备' : '请填写接口路径');
      return;
    }
    const body: BindingSaveRequest = {
      entityId: Number(values.entityId),
      sourceType: values.sourceType,
      bindKey,
      propertyMap: values.propertyMap?.trim() || null,
      pollIntervalMs: values.pollIntervalMs ?? 5000,
      enabled: values.enabled === false ? 0 : 1,
    };
    try {
      if (editingBinding) {
        await bindingApi.update(sceneId, editingBinding.id, body);
        message.success('绑定已更新');
      } else {
        await bindingApi.create(sceneId, body);
        message.success('绑定已创建');
      }
      setBindingDrawerOpen(false);
      void loadBindings(sceneId);
    } catch (error) {
      message.error(`保存绑定失败：${String(error)}`);
    }
  };

  const removeBinding = async (binding: BindingVO) => {
    if (!sceneId) return;
    try {
      await bindingApi.remove(sceneId, binding.id);
      message.success('绑定已删除');
      void loadBindings(sceneId);
    } catch (error) {
      message.error(`删除绑定失败：${String(error)}`);
    }
  };

  const flatEntities = flatten(tree);
  const liveItems = Object.values(liveStates);

  return (
    <div className="twin-viewer">
      <div className="twin-viewer-tree">
        <Space direction="vertical" style={{ width: '100%' }} size="small">
          <Select
            placeholder="选择场景"
            style={{ width: '100%' }}
            value={sceneId ?? undefined}
            options={scenes.map((scene) => ({ value: scene.id, label: `${scene.name}（${scene.code}）` }))}
            onChange={(value) => setSceneId(value)}
          />
          <Space wrap>
            <Button size="small" type="primary" disabled={!sceneId} onClick={() => openCreate(null)}>
              新建根实体
            </Button>
            <Button size="small" disabled={!selected} onClick={() => selected && openEdit(selected)}>
              编辑选中
            </Button>
            <Button size="small" disabled={!sceneId} onClick={openBindingCreate}>
              数据绑定
            </Button>
            {streaming ? (
              <Button size="small" danger onClick={stopStream}>
                停止实时
              </Button>
            ) : (
              <Button size="small" type="primary" ghost disabled={!sceneId} onClick={() => void startStream()}>
                订阅实时
              </Button>
            )}
          </Space>
          <Badge
            status={streaming ? 'processing' : 'default'}
            text={streaming ? `实时联动中（${bindings.length} 条绑定）` : '未订阅'}
          />
          {selected && (
            <Popconfirm title="确认删除选中实体？" onConfirm={() => void removeEntity(selected)}>
              <Button size="small" danger>
                删除选中
              </Button>
            </Popconfirm>
          )}
        </Space>
        <Tree<EntityNode>
          style={{ marginTop: 12 }}
          treeData={tree}
          defaultExpandAll
          selectedKeys={selected ? [selected.id] : []}
          onSelect={(keys) => {
            const key = keys[0];
            if (!key) return;
            const found = flatEntities.find((item) => item.entity.id === key);
            if (found) setSelected(found.entity);
          }}
        />
      </div>
      <div className="twin-viewer-canvas">
        <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
        <div className="twin-viewer-hint">
          {loadingModel ? '模型加载中…' : '左键旋转 · 滚轮缩放 · 右键平移'}
        </div>
        {streaming && (
          <div className="twin-live-panel">
            <h4>实时状态</h4>
            {liveItems.length === 0 && <div className="twin-live-off">等待数据…</div>}
            {liveItems.map((item) => (
              <div key={item.bindingId} className="twin-live-item">
                <Badge status={item.online ? 'success' : 'error'} />
                <span className="twin-live-code">{item.entityCode}</span>
                {!item.online && <span className="twin-live-off">设备离线</span>}
                {Object.entries(item.values).map(([key, value]) => (
                  <span key={key} className="twin-live-value">
                    {key}={String(value)}{' '}
                  </span>
                ))}
              </div>
            ))}
          </div>
        )}
      </div>
      {/* 实体编辑抽屉 */}
      <Drawer
        title={selected ? '编辑实体' : '新建实体'}
        width={440}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        extra={
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" onClick={() => void submit()}>
              保存
            </Button>
          </Space>
        }
      >
        <Form<EntityFormValues> form={form} layout="vertical">
          <Form.Item
            name="entityCode"
            label="编码"
            rules={[
              { required: true, message: '请输入实体编码' },
              { pattern: /^[a-zA-Z][a-zA-Z0-9_-]{0,63}$/, message: '字母开头的字母/数字/下划线/中划线' },
            ]}
            extra={selected ? '编码创建后不可修改' : '场景内唯一，如 boiler-01'}
          >
            <Input disabled={!!selected} placeholder="boiler-01" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入实体名称' }]}>
            <Input placeholder="1号锅炉" maxLength={128} />
          </Form.Item>
          <Form.Item name="parentId" label="父节点">
            <Select
              allowClear
              placeholder="无（作为根节点）"
              options={flatEntities
                .filter((item) => item.entity.id !== selected?.id)
                .map((item) => ({
                  value: item.entity.id,
                  label: `${'　'.repeat(item.depth)}${item.entity.name}`,
                }))}
            />
          </Form.Item>
          <Form.Item name="modelFileId" label="模型文件ID" extra="whale-file 文件ID（GLB/GLTF），留空显示占位体">
            <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="文件ID" />
          </Form.Item>
          <Form.Item
            name="transformJson"
            label="变换 JSON"
            rules={[
              {
                validator: (_, value: string | undefined) => {
                  if (!value) return Promise.resolve();
                  try {
                    JSON.parse(value);
                    return Promise.resolve();
                  } catch {
                    return Promise.reject(new Error('不是合法的 JSON'));
                  }
                },
              },
            ]}
            extra='{"position":[0,0,0],"rotation":[0,0,0],"scale":[1,1,1]}（rotation 弧度）'
          >
            <Input.TextArea rows={3} placeholder='{"position":[0,0,0]}' />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序号">
            <InputNumber style={{ width: 160 }} precision={0} />
          </Form.Item>
        </Form>
      </Drawer>
      {/* 数据绑定抽屉 */}
      <Drawer
        title="数据绑定"
        width={520}
        open={bindingDrawerOpen}
        onClose={() => setBindingDrawerOpen(false)}
        extra={
          <Space>
            <Button onClick={() => setBindingDrawerOpen(false)}>关闭</Button>
            <Button type="primary" onClick={() => void submitBinding()}>
              {editingBinding ? '保存修改' : '创建绑定'}
            </Button>
          </Space>
        }
      >
        <Form<BindingFormValues> form={bindingForm} layout="vertical">
          <Form.Item name="entityId" label="绑定实体" rules={[{ required: true, message: '请选择实体' }]}>
            <Select
              placeholder="选择实体"
              options={flatEntities.map((item) => ({
                value: item.entity.id,
                label: `${item.entity.name}（${item.entity.entityCode}）`,
              }))}
            />
          </Form.Item>
          <Form.Item name="sourceType" label="数据源类型" rules={[{ required: true }]}>
            <Select
              disabled={!!editingBinding}
              options={[
                { value: 'iot_device', label: 'IoT 设备属性（whale-iot）' },
                { value: 'internal_api', label: '内网接口（M3 接入轮询）' },
              ]}
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.sourceType !== next.sourceType}>
            {({ getFieldValue }) =>
              getFieldValue('sourceType') === 'internal_api' ? (
                <Form.Item name="apiPath" label="接口路径" rules={[{ required: true, message: '请填写接口路径' }]}>
                  <Input placeholder="/metrics/device/boiler-01" />
                </Form.Item>
              ) : (
                <Form.Item name="deviceId" label="IoT 设备" rules={[{ required: true, message: '请选择设备' }]}>
                  <Select
                    showSearch
                    optionFilterProp="label"
                    placeholder="选择设备"
                    options={devices.map((device) => ({
                      value: device.id,
                      label: `${device.deviceName}${device.nickname ? `（${device.nickname}）` : ''}`,
                    }))}
                  />
                </Form.Item>
              )
            }
          </Form.Item>
          <Form.Item
            name="propertyMap"
            label="属性映射 JSON"
            rules={[
              {
                validator: (_, value: string | undefined) => {
                  if (!value) return Promise.resolve();
                  try {
                    const parsed = JSON.parse(value) as unknown;
                    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
                      return Promise.reject(new Error('必须是 JSON 对象'));
                    }
                    return Promise.resolve();
                  } catch {
                    return Promise.reject(new Error('不是合法的 JSON'));
                  }
                },
              },
            ]}
            extra={'{"temperature":{"stateKey":"color","thresholds":[50,80]}} —— thresholds 驱动变色（低于 t1 绿 / t1~t2 橙 / 高于 t2 红）'}
          >
            <Input.TextArea rows={3} placeholder='{"temperature":{"stateKey":"color","thresholds":[50,80]}}' />
          </Form.Item>
          <Form.Item name="pollIntervalMs" label="轮询间隔（毫秒）" extra="最小 1000；实际受服务端 tick 粒度约束">
            <InputNumber style={{ width: 180 }} min={1000} max={3600000} step={1000} precision={0} />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
        <div style={{ marginTop: 16 }}>
          <h4 style={{ marginBottom: 8 }}>场景绑定（{bindings.length}）</h4>
          {bindings.map((binding) => (
            <div key={binding.id} className="twin-live-item">
              <Badge status={binding.enabled === 1 ? 'success' : 'default'} />
              <span className="twin-live-code">
                {binding.entityName ?? binding.entityCode}
              </span>
              <span className="twin-live-value">
                {binding.sourceType === 'iot_device' ? '设备' : '接口'} {binding.bindKey} ·{' '}
                {binding.pollIntervalMs}ms
              </span>
              <Space size="small" style={{ float: 'right' }}>
                <Button size="small" type="link" onClick={() => openBindingEdit(binding)}>
                  编辑
                </Button>
                <Popconfirm title="确认删除该绑定？" onConfirm={() => void removeBinding(binding)}>
                  <Button size="small" type="link" danger>
                    删除
                  </Button>
                </Popconfirm>
              </Space>
            </div>
          ))}
        </div>
      </Drawer>
    </div>
  );
}
