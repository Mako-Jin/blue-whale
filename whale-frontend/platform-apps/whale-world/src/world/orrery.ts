import * as THREE from 'three';

/**
 * 观星台（S7/T7.7）的浑天仪：把孪生场景的星标真的嵌进城里。
 *
 * <p><b>为什么这一坊的特项是"3D 场景嵌入"而不是又一块屏风。</b>孪生平台本身
 * 就是三维的：场景里每个标注都有 {@code positionJson}（x/y/z），它们之间的
 * 相对位置本身就是信息——哪台设备离哪台近、告警是否聚在同一侧。屏风上排一张表
 * 只能读到"有几个星标"，读不出<b>它们摆成什么形状</b>。所以本殿在台前立一座
 * 浑天仪：三环为骨、星标为点，按场景真实坐标等比缩进球体，红的告警、青的热点、
 * 金的标签。</p>
 *
 * <p><b>读哪座场景。</b>只读<b>已发布</b>的那一座（取第一座）。草稿还在改、
 * 停用的不参与运行；把草稿画到浑天仪上，等于让人看着一张没生效的星图做判断。
 * 场景与星标分两次取（先 {@code /twin/v1/scenes/page?status=published}，
 * 再 {@code /twin/v1/scenes/{id}/markers}）——星标接口必须带 sceneId，
 * 没有"全平台星标"这种读法。</p>
 *
 * <p><b>取不到数就空盘并在探针里写明原因。</b>{@link OrreryHandle.probe} 的
 * {@code mode} 分四档：{@code live}（画出了星标）、{@code empty}（没有已发布场景
 * 或场景里没有启用的星标）、{@code denied}（没凭据或没登录）、{@code error}
 * （接口失败）。e2e 无法"看"，只能读这个字段、{@code points} 与 {@code spin}
 * （转动角度，用来证明它真的在转而不是一张静图）。</p>
 *
 * @author BlueWhale
 */

/** 浑天仪的底半径（米）：整个仪器落在一个 3.4 米见方的石台上 */
export const ORRERY_RADIUS = 1.7;

/** 球心高度（米）：底座 + 立柱之上 */
const CORE_Y = 2.1;

/** 星标铺开的球半径（米）：比三环略小，点不会骑在环上 */
const SKY_RADIUS = 1.15;

/** 三环半径（米） */
const RING_RADIUS = 1.36;

/** 星标点半径（米） */
const DOT_RADIUS = 0.085;

/** 转动速度（弧度/秒）：慢到能看清星位，快到一眼看出在转 */
const SPIN_SPEED = 0.22;

/** 标注类型 → 颜色：标签金 / 热点青 / 告警红 */
const COLOR_LABEL = 0xf2c14a;

const COLOR_HOTSPOT = 0x6fe3ff;

const COLOR_ALARM = 0xff6b4a;

/** 铜环与石台的颜色 */
const COLOR_BRONZE = 0x9c7a3c;

const COLOR_STONE = 0x9a9284;

/** 星标显示模式 */
export type OrreryMode = 'live' | 'empty' | 'denied' | 'error';

/** 一座已发布场景（只取展示要用的字段） */
export interface OrreryScene {
  /** 场景 ID（字符串化） */
  id: string;
  /** 场景编码 */
  code: string;
  /** 场景名称 */
  name: string;
}

/** 一个星标（只取画图要用的字段） */
export interface OrreryMarker {
  /** 类型：label / hotspot / alarm */
  markerType: string;
  /** 是否启用：1 启用 / 0 停用 */
  enabled: number;
  /** 三维坐标 JSON（数组或 {x,y,z}） */
  positionJson: string | null;
}

/** 浑天仪句柄 */
export interface OrreryHandle {
  /** 整座仪器（挂到场景即可） */
  group: THREE.Group;
  /**
   * 换一座场景与它的星标
   *
   * @param scene 已发布场景
   * @param markers 星标（只画 enabled=1 的）
   */
  setScene(scene: OrreryScene, markers: OrreryMarker[]): void;
  /**
   * 标记为"没有数据"的某一档，并清空星点
   *
   * @param mode 模式（live 不接受，走 setScene）
   * @param note 说明文字（写进探针）
   */
  setMode(mode: OrreryMode, note?: string): void;
  /**
   * 逐帧推进：三环与星点一起慢转
   *
   * @param dt 帧间隔（秒）
   */
  update(dt: number): void;
  /**
   * 状态探针（e2e 无法"看"，只能读状态）
   *
   * @returns 模式、场景、点数、转动角度与世界坐标
   */
  probe(): {
    mode: OrreryMode;
    sceneId: string;
    sceneCode: string;
    sceneName: string;
    markers: number;
    points: number;
    labels: number;
    hotspots: number;
    alarms: number;
    radius: number;
    spin: number;
    coreColor: string;
    note: string;
    x: number;
    y: number;
    z: number;
  };
  /** 释放：销毁几何、材质与纹理 */
  dispose(): void;
}

/**
 * 解析标注坐标：接口里可能是 {@code [x,y,z]} 数组，也可能是 {@code {x,y,z}} 对象。
 *
 * @param json 坐标 JSON
 * @returns 三维坐标；解析不了返回 null
 */
function parsePosition(json: string | null): [number, number, number] | null {
  if (!json) {
    return null;
  }
  try {
    const parsed = JSON.parse(json) as unknown;
    if (Array.isArray(parsed) && parsed.length === 3 && parsed.every((n) => typeof n === 'number')) {
      return [parsed[0] as number, parsed[1] as number, parsed[2] as number];
    }
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      const object = parsed as Record<string, unknown>;
      const x = Number(object.x);
      const y = Number(object.y);
      const z = Number(object.z);
      if ([x, y, z].every((n) => Number.isFinite(n))) {
        return [x, y, z];
      }
    }
    return null;
  } catch {
    return null;
  }
}

/**
 * 造一张圆形的辉光贴图，给每个星点做外晕。
 *
 * <p>软渲染（e2e 的 swiftshader）下 Sprite 的加法混合仍可用，比给每颗星配
 * 一盏点光源便宜得多——一台浑天仪上可能有几十颗星。</p>
 *
 * @returns 辉光纹理
 */
function createGlowTexture(): THREE.CanvasTexture {
  const size = 64;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  if (ctx) {
    const gradient = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2);
    gradient.addColorStop(0, 'rgba(255,255,255,0.95)');
    gradient.addColorStop(0.35, 'rgba(255,255,255,0.42)');
    gradient.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, size, size);
  }
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

/**
 * 建一座浑天仪。
 *
 * @returns 浑天仪句柄
 */
export function createOrrery(): OrreryHandle {
  const group = new THREE.Group();
  group.name = 'guanxing-orrery';

  /* ---- 石台与立柱：仪器要有个落脚的实体，否则悬在地面上像 bug ---- */
  const stoneMaterial = new THREE.MeshStandardMaterial({ color: COLOR_STONE, roughness: 0.92, metalness: 0.05 });
  const baseGeometry = new THREE.CylinderGeometry(ORRERY_RADIUS * 0.92, ORRERY_RADIUS, 0.22, 40);
  const base = new THREE.Mesh(baseGeometry, stoneMaterial);
  base.position.y = 0.11;
  group.add(base);

  const pedestalGeometry = new THREE.CylinderGeometry(0.16, 0.3, CORE_Y - 0.22, 16);
  const pedestal = new THREE.Mesh(pedestalGeometry, stoneMaterial);
  pedestal.position.y = 0.22 + (CORE_Y - 0.22) / 2;
  group.add(pedestal);

  /* ---- 三环与星点放在一个会转的"天空组"里：石台不动、天在转 ---- */
  const sky = new THREE.Group();
  sky.position.y = CORE_Y;
  group.add(sky);

  const bronzeMaterial = new THREE.MeshStandardMaterial({
    color: COLOR_BRONZE,
    roughness: 0.38,
    metalness: 0.68,
    emissive: 0x2a1f0c,
    emissiveIntensity: 0.6,
  });
  const ringGeometry = new THREE.TorusGeometry(RING_RADIUS, 0.032, 10, 72);
  const ringA = new THREE.Mesh(ringGeometry, bronzeMaterial);
  const ringB = new THREE.Mesh(ringGeometry, bronzeMaterial);
  ringB.rotation.y = Math.PI / 2;
  const ringC = new THREE.Mesh(ringGeometry, bronzeMaterial);
  ringC.rotation.x = Math.PI / 2;
  const ringD = new THREE.Mesh(ringGeometry, bronzeMaterial);
  ringD.rotation.set(Math.PI / 2.6, Math.PI / 5, 0);
  sky.add(ringA, ringB, ringC, ringD);

  /* ---- 天心：告警时变红，平时青。它是这一屏唯一"用颜色说话"的地方 ---- */
  const coreMaterial = new THREE.MeshStandardMaterial({
    color: 0x8fd0e8,
    emissive: new THREE.Color(COLOR_HOTSPOT),
    emissiveIntensity: 1.35,
    roughness: 0.25,
    metalness: 0.1,
  });
  const coreGeometry = new THREE.SphereGeometry(0.2, 24, 18);
  const core = new THREE.Mesh(coreGeometry, coreMaterial);
  sky.add(core);

  /* ---- 天心外晕：给球心套一团加法混合的辉光 ----
     第一版是一道向上的竖光柱，本意是"夜里也认得出"；从俯视角看它只剩一条
     细白线，视觉上像穿模。Sprite 永远面向相机，任何角度都是一团光，且只要一次绘制。 */
  const glowTexture = createGlowTexture();
  const coreGlowMaterial = new THREE.SpriteMaterial({
    map: glowTexture,
    color: COLOR_HOTSPOT,
    transparent: true,
    opacity: 0.55,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    toneMapped: false,
  });
  const coreGlow = new THREE.Sprite(coreGlowMaterial);
  coreGlow.scale.setScalar(1.5);
  sky.add(coreGlow);

  /* ---- 星点：几何与材质共享，几十颗星也只有几个 draw call ---- */
  const dotGeometry = new THREE.SphereGeometry(DOT_RADIUS, 10, 8);
  const dotMaterials: Record<string, THREE.MeshBasicMaterial> = {
    label: new THREE.MeshBasicMaterial({ color: COLOR_LABEL, toneMapped: false }),
    hotspot: new THREE.MeshBasicMaterial({ color: COLOR_HOTSPOT, toneMapped: false }),
    alarm: new THREE.MeshBasicMaterial({ color: COLOR_ALARM, toneMapped: false }),
  };
  const glowMaterials: Record<string, THREE.SpriteMaterial> = {
    label: new THREE.SpriteMaterial({ map: glowTexture, color: COLOR_LABEL, transparent: true,
      opacity: 0.55, blending: THREE.AdditiveBlending, depthWrite: false, toneMapped: false }),
    hotspot: new THREE.SpriteMaterial({ map: glowTexture, color: COLOR_HOTSPOT, transparent: true,
      opacity: 0.55, blending: THREE.AdditiveBlending, depthWrite: false, toneMapped: false }),
    alarm: new THREE.SpriteMaterial({ map: glowTexture, color: COLOR_ALARM, transparent: true,
      opacity: 0.7, blending: THREE.AdditiveBlending, depthWrite: false, toneMapped: false }),
  };
  const skyPoints = new THREE.Group();
  sky.add(skyPoints);

  let mode: OrreryMode = 'empty';
  let note = '浑天仪未接';
  let scene: OrreryScene | null = null;
  let markerCount = 0;
  let points = 0;
  let labels = 0;
  let hotspots = 0;
  let alarms = 0;
  let spin = 0;
  let disposed = false;

  /**
   * 清空星点（保留几何与材质，等着下一批数据）。
   */
  function clearPoints(): void {
    /* 只摘不改材质：几何与材质是共享的，逐颗 dispose 会把还在地上的星一起弄坏。 */
    for (const child of [...skyPoints.children]) {
      skyPoints.remove(child);
    }
    points = 0;
    labels = 0;
    hotspots = 0;
    alarms = 0;
  }

  /**
   * 按类型归一到三档：未知类型按标签画（宁可多一颗星，也不要静默丢掉数据）。
   *
   * @param markerType 接口给的类型
   * @returns label / hotspot / alarm
   */
  function toneOf(markerType: string): 'label' | 'hotspot' | 'alarm' {
    if (markerType === 'alarm') {
      return 'alarm';
    }
    return markerType === 'hotspot' ? 'hotspot' : 'label';
  }

  return {
    group,
    setScene(next: OrreryScene, markers: OrreryMarker[]): void {
      if (disposed) {
        return;
      }
      scene = next;
      markerCount = markers.length;
      clearPoints();
      const usable: { tone: 'label' | 'hotspot' | 'alarm'; position: [number, number, number] }[] = [];
      for (const marker of markers) {
        if (marker.enabled !== 1) {
          continue;
        }
        const position = parsePosition(marker.positionJson);
        if (!position) {
          continue;
        }
        usable.push({ tone: toneOf(marker.markerType), position });
      }
      if (usable.length === 0) {
        mode = 'empty';
        note = '场景里没有启用的星标';
        coreMaterial.emissive.setHex(COLOR_HOTSPOT);
        coreGlowMaterial.color.setHex(COLOR_HOTSPOT);
        return;
      }
      /* 包围盒 → 球心与缩放：星标按<b>相对位置</b>摆，绝对坐标（米/毫米/任意）
         不参与判断，否则一个用毫米的场景会把所有点挤成一个亮点。 */
      let minX = Infinity; let minY = Infinity; let minZ = Infinity;
      let maxX = -Infinity; let maxY = -Infinity; let maxZ = -Infinity;
      for (const item of usable) {
        minX = Math.min(minX, item.position[0]);
        minY = Math.min(minY, item.position[1]);
        minZ = Math.min(minZ, item.position[2]);
        maxX = Math.max(maxX, item.position[0]);
        maxY = Math.max(maxY, item.position[1]);
        maxZ = Math.max(maxZ, item.position[2]);
      }
      const cx = (minX + maxX) / 2;
      const cy = (minY + maxY) / 2;
      const cz = (minZ + maxZ) / 2;
      const span = Math.max(maxX - minX, maxY - minY, maxZ - minZ);
      const scale = span > 1e-4 ? (SKY_RADIUS * 2) / span : 0;
      for (const item of usable) {
        const x = (item.position[0] - cx) * scale;
        const y = (item.position[1] - cy) * scale;
        const z = (item.position[2] - cz) * scale;
        const dot = new THREE.Mesh(dotGeometry, dotMaterials[item.tone]);
        dot.position.set(x, y, z);
        skyPoints.add(dot);
        const glow = new THREE.Sprite(glowMaterials[item.tone]);
        glow.position.set(x, y, z);
        glow.scale.setScalar(item.tone === 'alarm' ? 0.62 : 0.46);
        skyPoints.add(glow);
        points++;
        if (item.tone === 'alarm') {
          alarms++;
        } else if (item.tone === 'hotspot') {
          hotspots++;
        } else {
          labels++;
        }
      }
      mode = 'live';
      note = '';
      /* 有告警就把天心烧红：这一眼比读表快，也是"红点聚在哪一侧"的入口 */
      const toneColor = alarms > 0 ? COLOR_ALARM : COLOR_HOTSPOT;
      coreMaterial.emissive.setHex(toneColor);
      coreGlowMaterial.color.setHex(toneColor);
    },
    setMode(next: OrreryMode, reason?: string): void {
      if (disposed) {
        return;
      }
      mode = next === 'live' ? 'empty' : next;
      note = reason ?? (next === 'denied' ? '无此殿凭据' : '浑天仪未接通');
      scene = null;
      markerCount = 0;
      clearPoints();
      coreMaterial.emissive.setHex(COLOR_HOTSPOT);
      coreGlowMaterial.color.setHex(COLOR_HOTSPOT);
    },
    update(dt: number): void {
      if (disposed) {
        return;
      }
      spin = (spin + dt * SPIN_SPEED) % (Math.PI * 2);
      sky.rotation.y = spin;
      /* 天心的呼吸：幅度很小，只为让"还活着"这件事可见 */
      coreMaterial.emissiveIntensity = 1.15 + Math.sin(spin * 3) * 0.2;
    },
    probe() {
      const world = new THREE.Vector3();
      group.getWorldPosition(world);
      return {
        mode,
        sceneId: scene?.id ?? '',
        sceneCode: scene?.code ?? '',
        sceneName: scene?.name ?? '',
        markers: markerCount,
        points,
        labels,
        hotspots,
        alarms,
        radius: ORRERY_RADIUS,
        spin: Number(spin.toFixed(3)),
        coreColor: `#${coreMaterial.emissive.getHexString()}`,
        note,
        x: Number(world.x.toFixed(2)),
        y: Number(group.position.y.toFixed(2)),
        z: Number(world.z.toFixed(2)),
      };
    },
    dispose(): void {
      disposed = true;
      clearPoints();
      group.remove(sky, base, pedestal);
      baseGeometry.dispose();
      pedestalGeometry.dispose();
      stoneMaterial.dispose();
      ringGeometry.dispose();
      bronzeMaterial.dispose();
      coreGeometry.dispose();
      coreMaterial.dispose();
      coreGlowMaterial.dispose();
      dotGeometry.dispose();
      for (const material of Object.values(dotMaterials)) {
        material.dispose();
      }
      for (const material of Object.values(glowMaterials)) {
        material.dispose();
      }
      glowTexture.dispose();
    },
  };
}

/**
 * 一次 GET 取数的公共壳子：带 Bearer、带超时、把业务 code 也当失败。
 *
 * @param path 接口路径
 * @param options 令牌与超时
 * @returns 解析后的 data 或错误
 */
async function getJson(
  path: string,
  options: { token: string | null; timeoutMillis?: number },
): Promise<{ ok: boolean; status: number; data: unknown; error: string }> {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), options.timeoutMillis ?? 6000);
  try {
    const response = await fetch(path, {
      method: 'GET',
      headers: { Authorization: `Bearer ${options.token}`, Accept: 'application/json' },
      credentials: 'omit',
      signal: controller.signal,
    });
    if (response.status === 401 || response.status === 403) {
      return { ok: false, status: response.status, data: null, error: '无此殿凭据' };
    }
    if (!response.ok) {
      return { ok: false, status: response.status, data: null, error: `HTTP ${response.status}` };
    }
    const payload: unknown = await response.json();
    const body = (payload ?? {}) as { code?: number; message?: string; data?: unknown };
    if (typeof body.code === 'number' && body.code !== 0) {
      return { ok: false, status: 200, data: null, error: String(body.message ?? '业务拒绝') };
    }
    return { ok: true, status: 200, data: body.data ?? null, error: '' };
  } catch (error) {
    const reason = controller.signal.aborted
      ? '问询超时'
      : (error instanceof Error && error.message ? error.message : '未接通').slice(0, 28);
    return { ok: false, status: 0, data: null, error: reason };
  } finally {
    window.clearTimeout(timer);
  }
}

/**
 * 取第一座已发布场景。
 *
 * <p>分页参数是 {@code current/size}（与平台统一口径一致，不是舆图司的 page/size）。
 * 只取一条：浑天仪只演一座场景，取多了也没有第二个盘放。</p>
 *
 * @param options 取数参数
 * @returns 场景；没有已发布场景时 scene 为 null、ok 为 true
 */
export async function fetchPublishedScene(options: {
  token: string | null;
  permission?: string;
  permissions?: () => string[] | null;
  listPath: string;
}): Promise<{ ok: boolean; status: number; scene: OrreryScene | null; error: string }> {
  if (!options.token) {
    return { ok: false, status: 401, scene: null, error: '未登录' };
  }
  const perms = options.permissions?.();
  if (options.permission && perms && perms.length > 0 && !perms.includes(options.permission)) {
    return { ok: false, status: 403, scene: null, error: '无此殿凭据' };
  }
  const result = await getJson(`${options.listPath}?status=published&current=1&size=1`, options);
  if (!result.ok) {
    return { ok: false, status: result.status, scene: null, error: result.error };
  }
  const page = (result.data ?? {}) as { records?: unknown };
  const records = Array.isArray(page.records) ? page.records : Array.isArray(result.data) ? result.data : [];
  const first = records[0] as Record<string, unknown> | undefined;
  if (!first) {
    return { ok: true, status: 200, scene: null, error: '' };
  }
  return {
    ok: true,
    status: 200,
    scene: {
      id: String(first.id ?? ''),
      code: String(first.code ?? ''),
      name: String(first.name ?? '未名场景'),
    },
    error: '',
  };
}

/**
 * 取一座场景的星标。
 *
 * @param sceneId 场景 ID
 * @param options 取数参数
 * @returns 星标列表
 */
export async function fetchSceneMarkers(
  sceneId: string,
  options: { token: string | null; timeoutMillis?: number },
): Promise<{ ok: boolean; status: number; markers: OrreryMarker[]; error: string }> {
  if (!options.token) {
    return { ok: false, status: 401, markers: [], error: '未登录' };
  }
  const result = await getJson(
    `/twin/v1/scenes/${encodeURIComponent(sceneId)}/markers`, options);
  if (!result.ok) {
    return { ok: false, status: result.status, markers: [], error: result.error };
  }
  const rows = Array.isArray(result.data)
    ? (result.data as unknown[])
    : Array.isArray((result.data as { records?: unknown } | null)?.records)
      ? ((result.data as { records: unknown[] }).records)
      : [];
  const markers = rows
    .map((raw) => {
      if (!raw || typeof raw !== 'object') {
        return null;
      }
      const row = raw as Record<string, unknown>;
      return {
        markerType: typeof row.markerType === 'string' ? row.markerType : 'label',
        enabled: Number(row.enabled ?? 0) === 1 ? 1 : 0,
        positionJson: typeof row.positionJson === 'string' ? row.positionJson : null,
      } satisfies OrreryMarker;
    })
    .filter((item): item is OrreryMarker => item !== null);
  return { ok: true, status: 200, markers, error: '' };
}
