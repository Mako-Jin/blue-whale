// 3D 查看器页：左实体树 + 右 three.js 画布 + 数据绑定与实时联动（M2）+ 标注热点（M3）
//   + AI 解读（6.04 M1）+ 告警中心（6.06 M2）
//
// 渲染规则：
//   - 有 modelFileId 的实体：从 /file/{id}/preview 拉流为 Blob → GLTFLoader 加载；
//   - 无模型的实体：占位立方体；
//   - transformJson 约定：{"position":[x,y,z],"rotation":[x,y,z],"scale":[x,y,z]}
//     （rotation 为弧度欧拉角；缺省分量按 0/1 处理）；
//   - 相机：OrbitControls（three/addons），鼠标拖拽旋转、滚轮缩放；
//   - 标注（M3）：twin_marker 按 positionJson 渲染为文字 Sprite（label 黄 /
//     hotspot 青 / alarm 红），热点带 linkUrl 时点击 Sprite 新窗口打开。
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
  List,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Space,
  Switch,
  Tag,
  Tree,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import { WhaleSceneManager, loadGltf } from '@bluewhale/whale-common-three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import {
  Box3,
  BoxGeometry,
  CanvasTexture,
  Color,
  Group,
  Mesh,
  MeshStandardMaterial,
  Object3D,
  Raycaster,
  Sprite,
  SpriteMaterial,
  Vector2,
  Vector3,
} from 'three';
import {
  alertApi,
  bindingApi,
  entityApi,
  fetchIotDevices,
  fetchModelUrl,
  buildInsightStreamUrl,
  markerApi,
  revokeModelUrl,
  sceneApi,
  subscribeSceneStream,
  type BindingAlertVO,
  type BindingSaveRequest,
  type BindingStateVO,
  type BindingVO,
  type EntitySaveRequest,
  type EntityVO,
  type IotDeviceItem,
  type InsightDoneMeta,
  type MarkerSaveRequest,
  type MarkerVO,
  type SceneVO,
  type TwinAlertVO,
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

/** 标注类型 → 描边/文字色（label 黄 / hotspot 青 / alarm 红） */
const MARKER_COLORS: Record<MarkerVO['markerType'], string> = {
  label: '#f1c40f',
  hotspot: '#18a999',
  alarm: '#e74c3c',
};

/** 标注类型缺省文案 */
const MARKER_LABELS: Record<MarkerVO['markerType'], string> = {
  label: '标签',
  hotspot: '热点',
  alarm: '告警',
};

/** 告警级别 → 展示文案与配色（与实时变色口径一致：橙预警 / 红严重） */
const ALERT_LEVEL_META: Record<string, { color: string; label: string }> = {
  critical: { color: 'red', label: '严重' },
  warn: { color: 'orange', label: '预警' },
  normal: { color: 'green', label: '恢复' },
};

/** SSE alert 帧条目 → 告警视图（实时插入列表用，resolved 恒 false） */
function frameToAlert(item: BindingAlertVO): TwinAlertVO {
  return {
    id: `live-${item.bindingId}-${item.stateKey}-${item.ts ?? ''}`,
    entityId: item.entityId ?? null,
    entityCode: item.entityCode ?? null,
    bindingId: item.bindingId,
    stateKey: item.stateKey,
    value: item.value == null ? null : String(item.value),
    previousLevel: item.previous ?? null,
    level: item.level,
    thresholds: item.thresholds ?? null,
    resolved: false,
    resolveTime: null,
    createTime: item.ts ?? null,
  };
}

/** 解析标注坐标 JSON（支持 {"x","y","z"} 与 [x,y,z] 两种形态；非法返回 null） */
function parseMarkerPosition(json: string | null | undefined): [number, number, number] | null {
  if (!json) return null;
  try {
    const parsed = JSON.parse(json) as unknown;
    if (Array.isArray(parsed) && parsed.length === 3 && parsed.every((n) => typeof n === 'number')) {
      return [parsed[0], parsed[1], parsed[2]];
    }
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      const object = parsed as Record<string, unknown>;
      const x = Number(object.x);
      const y = Number(object.y);
      const z = Number(object.z);
      if ([x, y, z].every((n) => Number.isFinite(n))) return [x, y, z];
    }
    return null;
  } catch {
    return null;
  }
}

/** 画布圆角矩形路径 */
function traceRoundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number): void {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

/** 构建标注文字 Sprite（离屏 canvas → CanvasTexture；纹理挂在 userData 便于释放） */
function buildMarkerSprite(marker: MarkerVO): Sprite {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 96;
  const ctx = canvas.getContext('2d');
  const color = MARKER_COLORS[marker.markerType] ?? MARKER_COLORS.label;
  if (ctx) {
    const text = (marker.content ?? MARKER_LABELS[marker.markerType] ?? '标注').slice(0, 12);
    traceRoundRect(ctx, 10, 18, 236, 60, 14);
    ctx.fillStyle = 'rgba(12, 22, 32, 0.85)';
    ctx.fill();
    ctx.lineWidth = 4;
    ctx.strokeStyle = color;
    ctx.stroke();
    ctx.fillStyle = color;
    ctx.font = 'bold 30px "PingFang SC", "Microsoft YaHei", sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(text, 128, 49);
  }
  const texture = new CanvasTexture(canvas);
  const sprite = new Sprite(
    new SpriteMaterial({ map: texture, transparent: true, depthTest: false }),
  );
  sprite.scale.set(3.2, 1.2, 1);
  sprite.userData.__texture = texture;
  sprite.userData.__linkUrl = marker.linkUrl ?? null;
  return sprite;
}

/** 释放 Sprite 的纹理与材质（重建/卸载时调用） */
function disposeMarkerSprites(group: Group | null): void {
  if (!group) return;
  for (const child of group.children) {
    const sprite = child as Sprite;
    const material = sprite.material as SpriteMaterial | undefined;
    const texture = sprite.userData?.__texture as CanvasTexture | undefined;
    texture?.dispose();
    material?.dispose();
  }
  group.clear();
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

/** 标注表单值（与 MarkerSaveRequest 对齐） */
interface MarkerFormValues {
  entityId?: string | null;
  markerType: MarkerVO['markerType'];
  content?: string;
  positionJson?: string;
  linkUrl?: string;
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

  // ---- M3：标注/热点 ----
  const markersRootRef = useRef<Group | null>(null);
  const [markers, setMarkers] = useState<MarkerVO[]>([]);
  const [markerDrawerOpen, setMarkerDrawerOpen] = useState(false);
  const [editingMarker, setEditingMarker] = useState<MarkerVO | null>(null);
  const [markerForm] = Form.useForm<MarkerFormValues>();
  // 坐标拾取模式（6.05 M1）：点击模型表面 → Raycaster 世界坐标回填 positionJson
  const [picking, setPicking] = useState(false);
  const pickingRef = useRef(false);

  // AI 解读（6.04 M1 → 6.05 M0 流式）：服务端组装上下文，SSE 逐帧渲染
  const [insightOpen, setInsightOpen] = useState(false);
  const [insightStreaming, setInsightStreaming] = useState(false);
  const [insightText, setInsightText] = useState('');
  const [insightMeta, setInsightMeta] = useState<InsightDoneMeta | null>(null);
  const [insightQuestion, setInsightQuestion] = useState('');
  const insightAbortRef = useRef<AbortController | null>(null);

  // 告警中心（6.06 M2）：未恢复角标 + 抽屉列表；SSE alert 帧实时插入
  const [alertDrawerOpen, setAlertDrawerOpen] = useState(false);
  const [alerts, setAlerts] = useState<TwinAlertVO[]>([]);
  const [alertFilter, setAlertFilter] = useState<'unresolved' | 'all'>('unresolved');
  const [unresolvedCount, setUnresolvedCount] = useState(0);

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
    // 标注根节点（独立于实体 root：实体重建 root.clear() 不影响标注）
    const markersRoot = new Group();
    manager.getScene().add(markersRoot);
    // 热点点击：Raycaster 命中 Sprite 且带 linkUrl 时新窗口打开；
    // 拾取模式：点击模型表面把世界坐标回填到标注表单（并自动带出挂靠实体）
    const raycaster = new Raycaster();
    const pointer = new Vector2();
    const domElement = manager.getRenderer().domElement;
    // 拖拽（旋转相机）后浏览器仍会派发 click：位移超阈值即视为拖拽，不触发拾取/跳转
    let downX = 0;
    let downY = 0;
    const handlePointerDown = (event: PointerEvent) => {
      downX = event.clientX;
      downY = event.clientY;
    };
    const handleClick = (event: MouseEvent) => {
      if (Math.hypot(event.clientX - downX, event.clientY - downY) > 4) return;
      const rect = domElement.getBoundingClientRect();
      pointer.set(
        ((event.clientX - rect.left) / rect.width) * 2 - 1,
        -((event.clientY - rect.top) / rect.height) * 2 + 1,
      );
      raycaster.setFromCamera(pointer, manager.getCamera());
      if (pickingRef.current) {
        const entityRoot = rootGroupRef.current;
        if (!entityRoot) return;
        const entityHits = raycaster.intersectObjects(entityRoot.children, true);
        if (entityHits.length === 0) {
          message.info('未命中模型表面，请点击实体网格（Esc 退出拾取）');
          return;
        }
        const hit = entityHits[0];
        let node: Object3D | null = hit.object;
        let entityId: string | null = null;
        while (node && !entityId) {
          const raw = (node.userData as Record<string, unknown>).__entityId;
          if (raw) entityId = String(raw);
          node = node.parent;
        }
        const point = hit.point;
        const json = `{"x":${point.x.toFixed(3)},"y":${point.y.toFixed(3)},"z":${point.z.toFixed(3)}}`;
        markerForm.setFieldsValue({
          positionJson: json,
          ...(entityId ? { entityId } : {}),
        });
        setPicking(false);
        message.success(`已拾取坐标 ${json}${entityId ? '，挂靠实体已带出' : ''}`);
        return;
      }
      if (markersRoot.children.length === 0) return;
      const hits = raycaster.intersectObjects(markersRoot.children, false);
      const link = hits[0]?.object.userData?.__linkUrl;
      if (typeof link === 'string' && link) {
        window.open(link, '_blank', 'noopener');
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && pickingRef.current) {
        setPicking(false);
      }
    };
    domElement.addEventListener('click', handleClick);
    domElement.addEventListener('pointerdown', handlePointerDown);
    window.addEventListener('keydown', handleKeyDown);
    manager.onFrame(() => controls.update());
    manager.start();
    managerRef.current = manager;
    controlsRef.current = controls;
    rootGroupRef.current = root;
    markersRootRef.current = markersRoot;
    return () => {
      domElement.removeEventListener('click', handleClick);
      domElement.removeEventListener('pointerdown', handlePointerDown);
      window.removeEventListener('keydown', handleKeyDown);
      disposeMarkerSprites(markersRootRef.current);
      manager.dispose();
      managerRef.current = null;
      controlsRef.current = null;
      rootGroupRef.current = null;
      markersRootRef.current = null;
    };
  }, [markerForm, message]);

  // 拾取模式：同步 ref（点击回调早于 state 生效）+ 十字光标
  useEffect(() => {
    pickingRef.current = picking;
    const dom = managerRef.current?.getRenderer().domElement;
    if (dom) dom.style.cursor = picking ? 'crosshair' : '';
    if (picking) {
      message.info('拾取模式：点击模型表面获取坐标，Esc 退出');
    }
  }, [picking, message]);

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
          holder.userData.__entityId = entity.id;
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

  const loadMarkers = useCallback(
    async (id: string) => {
      try {
        setMarkers(await markerApi.list(id));
      } catch (error) {
        message.error(`加载标注失败：${String(error)}`);
      }
    },
    [message],
  );

  useEffect(() => {
    if (!sceneId) return;
    void loadTree(sceneId);
    void buildSceneObjects(sceneId);
    void loadBindings(sceneId);
    void loadMarkers(sceneId);
  }, [sceneId, loadTree, buildSceneObjects, loadBindings, loadMarkers]);

  // M3：标注 Sprite 重建（标注列表或场景变化时；释放旧纹理避免泄漏）
  useEffect(() => {
    const group = markersRootRef.current;
    if (!group) return;
    disposeMarkerSprites(group);
    if (!sceneId) return;
    for (const marker of markers) {
      if (marker.enabled !== 1) continue;
      const position = parseMarkerPosition(marker.positionJson);
      if (!position) continue;
      const sprite = buildMarkerSprite(marker);
      sprite.position.set(...position);
      group.add(sprite);
    }
  }, [markers, sceneId]);

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

  // 未恢复告警角标：切场景重置并按接口拉取种子值（SSE alert 帧实时累加）
  useEffect(() => {
    setUnresolvedCount(0);
    setAlerts([]);
    if (!sceneId) return;
    alertApi
      .unresolved(sceneId)
      .then((list) => setUnresolvedCount(list.length))
      .catch(() => {
        // 角标拉取失败不打断查看器，抽屉打开时会再拉
      });
  }, [sceneId]);

  // 拉取告警列表（抽屉内切换过滤条件时调用）
  const loadAlerts = useCallback(
    async (mode: 'unresolved' | 'all') => {
      if (!sceneId) return;
      try {
        const data =
          mode === 'unresolved'
            ? await alertApi.unresolved(sceneId)
            : await alertApi.list(sceneId, { limit: 100 });
        setAlerts(data);
        if (mode === 'unresolved') setUnresolvedCount(data.length);
      } catch (error) {
        message.error(`加载告警失败：${String(error)}`);
      }
    },
    [sceneId, message],
  );

  const openAlertDrawer = () => {
    setAlertDrawerOpen(true);
    void loadAlerts(alertFilter);
  };

  // SSE alert 帧：非 normal 级别累加角标并插入列表头部；严重级别弹全局提示
  const handleAlertFrame = useCallback(
    (data: string) => {
      try {
        const parsed = JSON.parse(data) as { items?: BindingAlertVO[] };
        for (const item of parsed.items ?? []) {
          const target = item.entityCode ?? '实体';
          if (item.level === 'critical') {
            message.error(`【严重告警】${target}.${item.stateKey} = ${String(item.value)}`);
          } else if (item.level === 'warn') {
            message.warning(`【预警】${target}.${item.stateKey} = ${String(item.value)}`);
          }
          if (item.level !== 'normal') {
            setUnresolvedCount((count) => count + 1);
            setAlerts((list) => [frameToAlert(item), ...list].slice(0, 200));
          }
        }
      } catch {
        // 半包/脏帧忽略
      }
    },
    [message],
  );

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
          if (event === 'binding') {
            try {
              const parsed = JSON.parse(data) as { items?: BindingStateVO[] };
              if (parsed.items?.length) mergeStates(parsed.items);
            } catch {
              // 半包/脏帧忽略，等下一帧
            }
            return;
          }
          if (event === 'alert') {
            handleAlertFrame(data);
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
  }, [sceneId, streaming, mergeStates, handleAlertFrame, message]);

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

  // 8) 标注抽屉（M3）
  const openMarkerCreate = () => {
    setEditingMarker(null);
    setPicking(false);
    markerForm.resetFields();
    markerForm.setFieldsValue({
      markerType: 'label',
      enabled: true,
      entityId: selected?.id ?? null,
    });
    setMarkerDrawerOpen(true);
  };

  /** 打开 AI 解读弹窗（携带当前选中实体上下文） */
  const openInsight = () => {
    setInsightText('');
    setInsightMeta(null);
    setInsightQuestion('');
    setInsightOpen(true);
  };

  /** 流式请求 AI 解读：delta 逐帧追加正文，done 落统计，error 弹错 */
  const askInsight = async () => {
    if (!sceneId || insightStreaming) return;
    setInsightText('');
    setInsightMeta(null);
    setInsightStreaming(true);
    const controller = new AbortController();
    insightAbortRef.current = controller;
    try {
      await subscribeSceneStream(
        buildInsightStreamUrl(sceneId, selected ? Number(selected.id) : null, insightQuestion),
        (event, data) => {
          try {
            if (event === 'delta') {
              const payload = JSON.parse(data) as { text?: string };
              if (payload.text) setInsightText((prev) => prev + payload.text);
            } else if (event === 'done') {
              setInsightMeta(JSON.parse(data) as InsightDoneMeta);
            } else if (event === 'error') {
              const payload = JSON.parse(data) as { message?: string };
              message.error(payload.message || 'AI 解读失败，请稍后重试');
            }
          } catch {
            // 半包/脏帧忽略
          }
        },
        controller.signal,
      );
    } catch (error) {
      if (!controller.signal.aborted) {
        message.error(`AI 解读失败：${String(error)}`);
      }
    } finally {
      setInsightStreaming(false);
      insightAbortRef.current = null;
    }
  };

  /** 中止本次流式解读 */
  const stopInsight = () => {
    insightAbortRef.current?.abort();
  };

  const openMarkerEdit = (marker: MarkerVO) => {
    setEditingMarker(marker);
    markerForm.setFieldsValue({
      entityId: marker.entityId ?? null,
      markerType: marker.markerType,
      content: marker.content ?? undefined,
      positionJson: marker.positionJson ?? undefined,
      linkUrl: marker.linkUrl ?? undefined,
      enabled: marker.enabled === 1,
    });
    setMarkerDrawerOpen(true);
  };

  const submitMarker = async () => {
    if (!sceneId) return;
    const values = await markerForm.validateFields();
    const body: MarkerSaveRequest = {
      entityId: values.entityId ? Number(values.entityId) : null,
      markerType: values.markerType,
      content: values.content?.trim() || null,
      positionJson: values.positionJson?.trim() || null,
      linkUrl: values.linkUrl?.trim() || null,
      enabled: values.enabled === false ? 0 : 1,
    };
    try {
      if (editingMarker) {
        await markerApi.update(sceneId, editingMarker.id, body);
        message.success('标注已更新');
      } else {
        await markerApi.create(sceneId, body);
        message.success('标注已创建');
      }
      setMarkerDrawerOpen(false);
      void loadMarkers(sceneId);
    } catch (error) {
      message.error(`保存标注失败：${String(error)}`);
    }
  };

  const removeMarker = async (marker: MarkerVO) => {
    if (!sceneId) return;
    try {
      await markerApi.remove(sceneId, marker.id);
      message.success('标注已删除');
      void loadMarkers(sceneId);
    } catch (error) {
      message.error(`删除标注失败：${String(error)}`);
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
            <Button size="small" disabled={!sceneId} onClick={openMarkerCreate}>
              标注热点
            </Button>
            <Badge count={unresolvedCount} size="small">
              <Button size="small" disabled={!sceneId} onClick={openAlertDrawer}>
                告警中心
              </Button>
            </Badge>
            <Button size="small" disabled={!sceneId} onClick={openInsight}>
              AI 解读{selected ? `：${selected.name}` : '（整场景）'}
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
                { value: 'internal_api', label: '内网接口（只读白名单）' },
              ]}
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.sourceType !== next.sourceType}>
            {({ getFieldValue }) =>
              getFieldValue('sourceType') === 'internal_api' ? (
                <Form.Item
                  name="apiPath"
                  label="接口路径"
                  rules={[{ required: true, message: '请填写接口路径' }]}
                  extra="格式 /服务名/路径?query，须命中服务端白名单（各服务 /internal/ 只读端点）"
                >
                  <Input placeholder="/iot/internal/device/state?deviceId=1" />
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
      {/* 标注抽屉（M3） */}
      <Drawer
        title="标注 / 热点"
        width={520}
        open={markerDrawerOpen}
        onClose={() => {
          setMarkerDrawerOpen(false);
          setPicking(false);
        }}
        extra={
          <Space>
            <Button onClick={() => setMarkerDrawerOpen(false)}>关闭</Button>
            <Button type="primary" onClick={() => void submitMarker()}>
              {editingMarker ? '保存修改' : '创建标注'}
            </Button>
          </Space>
        }
      >
        <Form<MarkerFormValues> form={markerForm} layout="vertical">
          <Form.Item name="markerType" label="类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'label', label: '标签（文本说明）' },
                { value: 'hotspot', label: '热点（可跳转）' },
                { value: 'alarm', label: '告警定位' },
              ]}
            />
          </Form.Item>
          <Form.Item name="entityId" label="挂靠实体" extra="留空为场景级锚点">
            <Select
              allowClear
              placeholder="场景级锚点"
              options={flatEntities.map((item) => ({
                value: item.entity.id,
                label: `${item.entity.name}（${item.entity.entityCode}）`,
              }))}
            />
          </Form.Item>
          <Form.Item name="content" label="标注内容" extra="Sprite 文案，超 12 字截断">
            <Input placeholder="1号锅炉·超温告警" maxLength={1024} />
          </Form.Item>
          <Form.Item
            name="positionJson"
            label="三维坐标 JSON"
            rules={[
              {
                validator: (_, value: string | undefined) => {
                  if (!value) return Promise.resolve();
                  try {
                    const parsed = JSON.parse(value) as unknown;
                    const object = parsed as Record<string, unknown>;
                    if (
                      !parsed ||
                      typeof parsed !== 'object' ||
                      Array.isArray(parsed) ||
                      !Number.isFinite(Number(object.x)) ||
                      !Number.isFinite(Number(object.y)) ||
                      !Number.isFinite(Number(object.z))
                    ) {
                      return Promise.reject(new Error('需要含数字 x/y/z 的 JSON 对象'));
                    }
                    return Promise.resolve();
                  } catch {
                    return Promise.reject(new Error('不是合法的 JSON'));
                  }
                },
              },
            ]}
            extra='{"x":0,"y":2,"z":0}（也支持 [x,y,z]；填了才会在 3D 场景渲染）'
          >
            <Input.TextArea rows={2} placeholder='{"x":0,"y":2,"z":0}' />
            <Space size="small" style={{ marginTop: 6 }}>
              <Button size="small" type={picking ? 'primary' : 'default'} onClick={() => setPicking((v) => !v)}>
                {picking ? '退出拾取' : '拾取坐标（点击模型表面）'}
              </Button>
              {picking && <Tag color="processing">拾取中，Esc 取消</Tag>}
            </Space>
          </Form.Item>
          <Form.Item name="linkUrl" label="跳转地址" extra="热点点击时新窗口打开（可空）">
            <Input placeholder="https://… 或 /iot/devices" />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
        <div style={{ marginTop: 16 }}>
          <h4 style={{ marginBottom: 8 }}>场景标注（{markers.length}）</h4>
          {markers.map((marker) => (
            <div key={marker.id} className="twin-live-item">
              <Badge
                status={marker.enabled === 1 ? 'success' : 'default'}
                color={MARKER_COLORS[marker.markerType]}
              />
              <span className="twin-live-code">
                {marker.content ?? MARKER_LABELS[marker.markerType]}
              </span>
              <span className="twin-live-value">
                {MARKER_LABELS[marker.markerType]}
                {marker.entityName ? ` · ${marker.entityName}` : ' · 场景级'}
              </span>
              <Space size="small" style={{ float: 'right' }}>
                <Button size="small" type="link" onClick={() => openMarkerEdit(marker)}>
                  编辑
                </Button>
                <Popconfirm title="确认删除该标注？" onConfirm={() => void removeMarker(marker)}>
                  <Button size="small" type="link" danger>
                    删除
                  </Button>
                </Popconfirm>
              </Space>
            </div>
          ))}
        </div>
      </Drawer>

      {/* 告警中心抽屉（6.06 M2）：实时帧插入 + 历史/未恢复接口 */}
      <Drawer
        title={
          <Space>
            <span>告警中心</span>
            <Badge count={unresolvedCount} size="small" color="red" />
          </Space>
        }
        width={480}
        open={alertDrawerOpen}
        onClose={() => setAlertDrawerOpen(false)}
        extra={
          <Button onClick={() => void loadAlerts(alertFilter)}>刷新</Button>
        }
      >
        <Radio.Group
          value={alertFilter}
          optionType="button"
          buttonStyle="solid"
          style={{ marginBottom: 12 }}
          options={[
            { label: '未恢复', value: 'unresolved' },
            { label: '全部（近 100 条）', value: 'all' },
          ]}
          onChange={(event) => {
            const mode = event.target.value as 'unresolved' | 'all';
            setAlertFilter(mode);
            void loadAlerts(mode);
          }}
        />
        <List
          size="small"
          dataSource={alerts}
          locale={{ emptyText: '暂无告警' }}
          renderItem={(item) => {
            const meta = ALERT_LEVEL_META[item.level] ?? ALERT_LEVEL_META.normal;
            return (
              <List.Item>
                <div style={{ width: '100%' }}>
                  <Space wrap size={4}>
                    <Tag color={meta.color}>{meta.label}</Tag>
                    <span style={{ fontWeight: 600 }}>
                      {item.entityCode ?? item.entityId ?? '-'}
                    </span>
                    <span>.{item.stateKey}</span>
                    <span>= {item.value ?? '-'}</span>
                    {item.thresholds?.length ? (
                      <span className="twin-live-off">
                        阈值 [{item.thresholds.join(', ')}]
                      </span>
                    ) : null}
                  </Space>
                  <div className="twin-live-off">
                    {item.createTime ?? '-'}
                    {item.resolved
                      ? ` · 已恢复（${item.resolveTime ?? '-'}）`
                      : ' · 未恢复'}
                  </div>
                </div>
              </List.Item>
            );
          }}
        />
      </Drawer>

      <Modal
        title={selected ? `AI 解读：${selected.name}` : 'AI 解读（整场景概要）'}
        open={insightOpen}
        onCancel={() => !insightStreaming && setInsightOpen(false)}
        width={640}
        footer={
          <Space>
            <Button disabled={insightStreaming} onClick={() => setInsightOpen(false)}>
              关闭
            </Button>
            {insightStreaming ? (
              <Button danger onClick={stopInsight}>
                停止
              </Button>
            ) : (
              <Button type="primary" onClick={() => void askInsight()}>
                {insightText ? '重新解读' : '开始解读'}
              </Button>
            )}
          </Space>
        }
      >
        <Input
          placeholder="追问（可空，默认解读当前状态并给出处置建议）"
          value={insightQuestion}
          maxLength={500}
          showCount
          disabled={insightStreaming}
          onChange={(e) => setInsightQuestion(e.target.value)}
          onPressEnter={() => void askInsight()}
        />
        {(insightText || insightMeta) && (
          <div style={{ marginTop: 12 }}>
            {insightMeta && (
              <Space size="small" wrap style={{ marginBottom: 8 }}>
                {insightMeta.degraded && <Tag color="warning">降级回答（检索摘要）</Tag>}
                <Tag>{insightMeta.tokens} tokens</Tag>
                <Tag>{(insightMeta.elapsedMillis / 1000).toFixed(1)}s</Tag>
                {insightMeta.citations && insightMeta.citations.length > 0 && (
                  <Tag color="blue">{insightMeta.citations.length} 条引用</Tag>
                )}
              </Space>
            )}
            <div
              style={{
                whiteSpace: 'pre-wrap',
                maxHeight: 380,
                overflowY: 'auto',
                padding: 12,
                background: 'rgba(128,128,128,0.08)',
                borderRadius: 8,
                fontSize: 13,
                lineHeight: 1.7,
              }}
            >
              {insightText || ''}
              {insightStreaming && <span className="insight-caret">▍</span>}
            </div>
          </div>
        )}
        {!insightText && !insightStreaming && !insightMeta && (
          <div style={{ marginTop: 12, color: 'rgba(128,128,128,0.9)', fontSize: 12 }}>
            将把{selected ? '选中实体' : '整场景'}的实时绑定状态发给 AI 助手流式解读；内网令牌留在服务端，不经前端。
          </div>
        )}
        {insightStreaming && !insightText && (
          <div style={{ marginTop: 12, color: 'rgba(128,128,128,0.9)', fontSize: 12 }}>
            正在组装实时态上下文并调用 AI 助手，请稍候…
          </div>
        )}
      </Modal>
    </div>
  );
}
