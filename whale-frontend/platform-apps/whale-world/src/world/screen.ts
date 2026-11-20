/**
 * 屏风系统 —— 3D 城里的"真界面"。
 *
 * <p>城里有 31 座殿坊，每座都要能看数、能翻页、能筛选。做法不是把 2D 后台搬进
 * iframe（history 会打架、两套渲染树同时活着），而是<b>把界面本身放进 3D 空间</b>：
 * 一面立在殿前的屏风，屏风后面是真实 DOM。</p>
 *
 * <p>三层渲染，共用一台相机：</p>
 * <pre>
 *   WebGL 主画布  城池 / 建筑 / 地形 / 光效        —— 世界是真的
 *   CSS3D 层      屏风 / 卷轴 / 书案，挂真实 DOM   —— 界面是真的（输入法/滚动全原生）
 *   遮挡遮罩层    只画"会挡住屏风"的实体           —— 让柱子能切掉屏风（S0 T0.3）
 * </pre>
 *
 * <p>为什么屏风不做成贴图：贴图方案里文字是像素，缩放就糊，输入框是假的，
 * 表格不能滚。而 DOM 方案里字是矢量的、输入框是真输入框、滚动是真滚动，
 * 代价只有两条（下面各写一处）：IME 候选框定位、DOM 每帧重合成。</p>
 *
 * <p><b>尺寸约定</b>：屏风 element 一律按 {@link PX_PER_UNIT} px = 1 世界单位
 * 写内联宽高，再整体 scale 到世界尺寸。这样屏风里的字号是"物理字号"，
 * 相机推近时会像真实物件一样变大，而不是被浏览器按视口重新排版。</p>
 */

import * as THREE from 'three';
import { CSS3DObject, CSS3DRenderer } from 'three/addons/renderers/CSS3DRenderer.js';

/**
 * 每世界单位对应的屏风像素数。
 *
 * <p>200 是折中：100 时汉字在 3 米外开始糊，400 时一块大屏要 2000×1240 px，
 * 中低端机上光栅化代价明显。200 下大屏 1000×620，1080p 里占屏约 60%，
 * 文字仍是锐的。</p>
 */
const PX_PER_UNIT = 200;

/** 遮挡层编号。main 场景在 0，遮挡实体额外 enable 这一层 */
export const OCCLUDER_LAYER = 1;

/** 屏风三档规格（世界单位：宽 × 高） */
export const SCREEN_SIZES = {
  /** 小屏：告示、指标卡、单条提示 */
  small: { w: 2.2, h: 1.4 },
  /** 中屏：列表、KPI 看板、殿前总览（默认） */
  medium: { w: 3.4, h: 2.1 },
  /** 大屏：表单、详情、多列数据 */
  large: { w: 5.0, h: 3.1 },
} as const;

export type ScreenSize = keyof typeof SCREEN_SIZES;

/** 状态灯色（与后端服务健康度映射） */
export type ScreenTone = 'good' | 'warn' | 'bad' | 'idle';

/** 一面屏风的规格 */
export interface ScreenSpec {
  /** 唯一标识，同时作为 DOM 的 data-screen-id */
  id: string;
  /** 屏风中心的世界坐标 */
  position: [number, number, number];
  /**
   * 屏风面向的方位角（弧度）：0 表示法线朝 +Z，即站在 +Z 侧正对着看它。
   * 通常取"从殿中心指向城外"的方向，让玩家走近时正面看到。
   */
  facing?: number;
  size?: ScreenSize;
  title?: string;
  subtitle?: string;
  /** 题头右侧状态灯，默认 idle */
  tone?: ScreenTone;
  /** 是否允许进入（默认 true）。false 时只可远观，用于未接入的业务 */
  enterable?: boolean;
  /** 进入前校验：返回 false 则不进入（例如无权限） */
  canEnter?: () => boolean;
  /** 进入时相机到屏风的距离（米）。缺省按尺寸算到"占屏 82%" */
  enterDistance?: number;
  /** 内容挂载：拿到内容区元素，返回清理函数 */
  mount?: (body: HTMLElement) => void | (() => void);
}

/** 业务层拿到的屏风句柄 */
export interface ScreenHandle {
  id: string;
  /** 屏风根元素（3D 中的那块面） */
  el: HTMLElement;
  /** 内容区元素 */
  body: HTMLElement;
  setTitle: (title: string, subtitle?: string) => void;
  setTone: (tone: ScreenTone) => void;
  setEnterable: (on: boolean) => void;
  /** 进入这面屏风（等价于玩家点它） */
  enter: () => void;
  /** 退出到城中 */
  exit: () => void;
  isEngaged: () => boolean;
  destroy: () => void;
}

export interface ScreenLayerOptions {
  /** canvas 容器（#bww-scene），用来插 CSS3D 与遮挡画布 */
  host: HTMLElement;
  /** CSS3D 容器（#bww-css3d） */
  css3dRoot: HTMLElement;
  /** 遮挡画布容器（#bww-occluder） */
  occluderRoot: HTMLElement;
  /** 相机（与主场景共用） */
  camera: THREE.PerspectiveCamera;
  /** 主场景（遮挡 pass 要重画它） */
  scene: THREE.Scene;
  /** 进入/退出过渡完成回调，null 表示回到城中 */
  onEngagedChange?: (id: string | null) => void;
  /**
   * 取"跟随相机"的目标位姿：退出屏风时插值回这里。
   * 由 city.ts 提供——它才知道玩家在哪、镜头该给什么角度。
   */
  getFollowPose: () => { position: THREE.Vector3; target: THREE.Vector3 };
  /** 屏风内容滚动/输入时回调，供主循环暂停行走 */
  onDomFocus?: (focused: boolean) => void;
}

export interface ScreenLayerHandle {
  add: (spec: ScreenSpec) => ScreenHandle;
  get: (id: string) => ScreenHandle | null;
  remove: (id: string) => void;
  /** 每帧渲染（主场景 render 之后调用） */
  render: () => void;
  resize: (width: number, height: number) => void;
  /** 屏风层是否正在接管相机（过场中或已进入） */
  isControlling: () => boolean;
  /** 接管期间推进相机过场 */
  updateCamera: (dt: number) => void;
  /** 当前已进入的屏风 id */
  engagedId: () => string | null;
  /**
   * 找出离给定位置最近、且在半径内的可进入屏风。
   * 主循环每帧调它，用来决定"要不要显示按 E 进入的提示"。
   */
  nearest: (x: number, z: number, radius: number) => ScreenHandle | null;
  /** 注册遮挡实体：这些物体在屏风前面时会切掉屏风像素 */
  setOccluders: (objects: THREE.Object3D[]) => void;
  /** 退出任何已进入的屏风 */
  exit: () => void;
  dispose: () => void;
}

/** 过场时长（毫秒）。验收要求 ≤800ms，这里留 100ms 余量给低端机的帧抖动 */
const TRANSITION_MS = 680;

/** 进入屏风后屏风占屏幕的比例（按短边算） */
const ENTER_FILL = 0.82;

/** 缓动：两端慢中间快，像"走近灯下"而不是"瞬移" */
function easeInOutCubic(t: number): number {
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

interface Transition {
  fromPos: THREE.Vector3;
  fromQuat: THREE.Quaternion;
  toPos: THREE.Vector3;
  toQuat: THREE.Quaternion;
  startedAt: number;
  /** 过场结束后要进入的屏风；null 表示退出到城中 */
  targetId: string | null;
}

/** 单面屏风 */
class Screen {
  readonly id: string;
  readonly el: HTMLElement;
  readonly body: HTMLElement;
  /** 3D 对象；退出贴脸态时会换一个新的，原因见 {@link Screen#reattach3d} */
  object: CSS3DObject;

  private readonly titleEl: HTMLElement;
  private readonly subEl: HTMLElement;
  private readonly lamp: HTMLElement;
  private readonly foot: HTMLElement;
  private readonly cleanup: (() => void) | null;
  private readonly worldPosition: THREE.Vector3;
  private readonly worldFacing: number;
  private readonly spec: ScreenSpec;

  /** 是否已被搬到平面化层（编辑态/贴脸态） */
  planarized = false;

  constructor(spec: ScreenSpec) {
    this.spec = spec;
    this.id = spec.id;
    const size = SCREEN_SIZES[spec.size ?? 'medium'];
    this.worldPosition = new THREE.Vector3(...spec.position);
    this.worldFacing = spec.facing ?? 0;

    /* DOM 结构：外框 → 纸面 → 题头 / 内容 / 脚注。
       外框承担 3D 变换与阴影，纸面承担排版，两者分开是为了让
       "进入时纸面变亮"这类状态不用改一整套背景。 */
    const el = document.createElement('div');
    el.className = 'bws-screen';
    el.dataset.screenId = spec.id;
    el.dataset.size = spec.size ?? 'medium';
    el.dataset.enterable = String(spec.enterable !== false);
    const scale = 1 / PX_PER_UNIT;
    el.style.width = `${size.w * PX_PER_UNIT}px`;
    el.style.height = `${size.h * PX_PER_UNIT}px`;
    // CSS3DObject 的约定：element 像素尺寸 × scale = 世界单位尺寸
    el.style.transformOrigin = '50% 50%';
    /* 基准字号 18px / 200px 每单位 = 0.09 世界米高的字。
       中屏 element 宽 680px（3.4 单位 × 200），这一档下正文能排下 4 列 KPI；
       再大（24px）就只能排 3 列，屏风上的信息密度会低到需要频繁滚动。 */
    el.style.fontSize = '18px';

    const paper = document.createElement('div');
    paper.className = 'bws-paper';

    const head = document.createElement('div');
    head.className = 'bws-head';
    const titleEl = document.createElement('span');
    titleEl.className = 'bws-title';
    titleEl.textContent = spec.title ?? '';
    const subEl = document.createElement('i');
    subEl.className = 'bws-sub';
    subEl.textContent = spec.subtitle ?? '';
    const lamp = document.createElement('span');
    lamp.className = 'bws-lamp';
    lamp.dataset.tone = spec.tone ?? 'idle';
    head.append(titleEl, subEl, lamp);

    const body = document.createElement('div');
    body.className = 'bws-body';

    const foot = document.createElement('div');
    foot.className = 'bws-foot';

    paper.append(head, body, foot);
    el.appendChild(paper);

    this.el = el;
    this.body = body;
    this.titleEl = titleEl;
    this.subEl = subEl;
    this.lamp = lamp;
    this.foot = foot;

    this.cleanup = spec.mount?.(body) ?? null;

    const object = new CSS3DObject(el);
    object.position.copy(this.worldPosition);
    object.rotation.y = this.worldFacing;
    object.scale.setScalar(scale);
    this.object = object;
  }

  /** 屏风法线的水平分量（指向"正面看它的人"） */
  normal(): THREE.Vector3 {
    return new THREE.Vector3(Math.sin(this.worldFacing), 0, Math.cos(this.worldFacing));
  }

  /**
   * 换一个新的 3D 对象（贴脸态退回 3D 时用）。
   *
   * <p><b>为什么要换对象，而不是复用原来那个：</b>three 的 CSS3DRenderer 用
   * {@code WeakMap<object, {style}>} 缓存"上次写进 {@code element.style.transform}
   * 的字符串"，只有字符串变了才重写。屏风退出贴脸态后位置/旋转/缩放与进入前完全一样
   * （相机也被还原），算出来的样式字符串与缓存里那条逐字符相同 → 渲染器认定
   * "已经写过"直接跳过；而我们刚刚把 inline transform 清空了，于是元素停在
   * "无变换"状态：视觉上是一块巨大的纸面糊在镜头上，且再也点不动。
   * 新建对象等于让缓存必然失效 —— 比"改一下坐标再改回来"这种抖动干净得多。</p>
   */
  reattach3d(): void {
    const previous = this.object;
    const next = new CSS3DObject(this.el);
    next.position.copy(previous.position);
    next.rotation.copy(previous.rotation);
    next.scale.copy(previous.scale);
    next.visible = previous.visible;
    next.renderOrder = previous.renderOrder;
    next.layers.mask = previous.layers.mask;
    this.object = next;
  }

  center(): THREE.Vector3 {
    return this.worldPosition.clone();
  }

  sizeWorld(): { w: number; h: number } {
    return SCREEN_SIZES[this.spec.size ?? 'medium'];
  }

  /** 相机要站的位置：法线方向上，距离按"屏风占满 82% 短边"反算 */
  enterPose(camera: THREE.PerspectiveCamera): { position: THREE.Vector3; quaternion: THREE.Quaternion } {
    const { w, h } = this.sizeWorld();
    const vFov = (camera.fov * Math.PI) / 180;
    const tanV = Math.tan(vFov / 2);
    // 竖向受 fov 限制，横向受 aspect 限制，取更远的那个才不会切边
    const distV = h / 2 / tanV / ENTER_FILL;
    const distH = w / 2 / (tanV * camera.aspect) / ENTER_FILL;
    const dist = this.spec.enterDistance ?? Math.max(distV, distH);

    const position = this.center().add(this.normal().multiplyScalar(dist));
    // 屏风朝向固定，相机沿法线反推朝向即"正视屏风"
    const m = new THREE.Matrix4().lookAt(position, this.center(), new THREE.Vector3(0, 1, 0));
    const quaternion = new THREE.Quaternion().setFromRotationMatrix(m);
    return { position, quaternion };
  }

  setTitle(title: string, subtitle?: string): void {
    this.titleEl.textContent = title;
    if (subtitle !== undefined) {
      this.subEl.textContent = subtitle;
    }
  }

  /** 进入前的业务校验：无权限时只远观 */
  canEnter(): boolean {
    return this.spec.canEnter ? this.spec.canEnter() : true;
  }

  setTone(tone: ScreenTone): void {
    this.lamp.dataset.tone = tone;
  }

  setEnterable(on: boolean): void {
    this.el.dataset.enterable = String(on);
  }

  setFocus(on: boolean): void {
    this.el.dataset.focus = String(on);
    this.el.classList.toggle('is-active', on);
    this.foot.innerHTML = on
      ? '<span><kbd>Esc</kbd> 退出屏风 · 内容可滚动</span><span>CLOSE</span>'
      : '<span><kbd>E</kbd> 或点击进入</span><span>OPEN</span>';
  }

  /** 未进入时的脚注：给个"能进"的提示，而不是一块死板 */
  resetFoot(): void {
    if (this.spec.enterable === false) {
      this.foot.innerHTML = '<span>尚未接入</span><span>OFFLINE</span>';
      return;
    }
    this.setFocus(false);
  }

  destroy(): void {
    this.cleanup?.();
    this.el.remove();
  }
}

/**
 * 屏风内 antd 弹层的容器工厂（T2.12）。
 *
 * <p>antd 的 Select / DatePicker / Tooltip / Dropdown 默认把浮层挂到
 * `document.body`。在屏风里这是致命的：浮层一旦离开 3D 变换的祖先，
 * 相机一动它就与原控件脱节，漂在屏幕坐标上（S0 的 T0.5 用三张对照图确认过）。</p>
 *
 * <p>用法（S3 数据厅里统一这么包）：</p>
 * <pre>{`
 *   <ConfigProvider getPopupContainer={screenPopupContainer(screen.body)}>
 *     ...业务组件...
 *   </ConfigProvider>
 * `}</pre>
 *
 * <p>内容区 `.bws-body` 已设 `position: relative`，所以浮层会以它为定位基准，
 * 跟着屏风一起缩放、一起倾斜。</p>
 *
 * @param host 屏风内容区元素（{@link ScreenHandle.body}）
 * @returns 可直接传给 `getPopupContainer` 的函数
 */
export function screenPopupContainer(host: HTMLElement): () => HTMLElement {
  return () => host;
}

/**
 * 屏风渲染层。持有 CSS3D 渲染器、遮挡画布与相机过场状态。
 *
 * <p>一个实例管全城所有屏风：它们共用一个 CSS3D 场景、一台相机、一块遮挡画布。
 * 每面屏风只是场景里的一个 {@link CSS3DObject}，代价是"一个 DOM 子树 + 一次矩阵乘法"。</p>
 */
export function createScreenLayer(options: ScreenLayerOptions): ScreenLayerHandle {
  const { host, css3dRoot, occluderRoot, camera, scene } = options;

  const css3dScene = new THREE.Scene();
  const css3d = new CSS3DRenderer();
  css3dRoot.appendChild(css3d.domElement);

  /* 遮挡画布：独立 WebGL 上下文，只画 OCCLUDER_LAYER。
     不共用主 renderer 是因为主 renderer 画的是不透明背景，
     叠在屏风上会整块糊死（S0 踩过：忘了摘 scene.background，全屏变黑）。 */
  const occluder = new THREE.WebGLRenderer({ alpha: true, antialias: true });
  occluder.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
  occluder.setClearColor(0x000000, 0);
  occluderRoot.appendChild(occluder.domElement);

  const screens = new Map<string, Screen>();
  /** 进入屏风时压暗背景的遮罩：在过场一开始就淡入，比"到了才暗"自然 */
  const dimEl = document.getElementById('bww-screen-dim');
  /**
   * 平面化层：进入屏风后把屏风从 3D 容器搬到这里，用 2D 仿射定位。
   *
   * <p>为什么必须搬：IME 候选框的位置由浏览器按元素的 layout 位置算，而 CSS3D 里
   * 元素带 `matrix3d` 的祖先后，layout 坐标与视觉位置脱节，候选框会飘到别处。
   * 搬到没有 3D 祖先的层里、用 2D `translate + scale` 摆到同一个屏幕矩形上，
   * 视觉几乎无差别（此时屏风正对相机，透视退化成仿射），但输入法瞬间正常。</p>
   *
   * <p>这同时就是 T2.11 的"贴脸静室"：屏风铺满视野、内容可滚动可输入，
   * 长文与大表都在这里读。</p>
   */
  const planarRoot = document.getElementById('bww-planar');
  /** 按进入顺序记录可点击的屏风，供键盘 Tab 式循环（暂未用，留作导航） */
  let engaged: string | null = null;
  let transition: Transition | null = null;
  let disposed = false;
  /** 进入期间被临时改过层的灯，退出时还原 */
  const occluderObjects: THREE.Object3D[] = [];

  const width = () => host.clientWidth || window.innerWidth;
  const height = () => host.clientHeight || window.innerHeight;

  css3d.setSize(width(), height());
  occluder.setSize(width(), height());

  /* ---------------- 相机过场 ---------------- */

  /**
   * 把屏风从 3D 容器搬到平面化层，锁在它当前的屏幕矩形上。
   *
   * <p>关键在于用 `rect.width / element.offsetWidth` 反算缩放：屏风在 CSS 里是
   * 680×420 的布局盒，屏幕上被 3D 投影成 1181×729，这两个数的比值就是需要的
   * 2D scale。用 `getBoundingClientRect` 而不是复刻投影矩阵，是因为它天然包含了
   * devicePixelRatio、页面缩放、浏览器全屏等所有因素——自己算一定会漏。</p>
   */
  function planarize(screen: Screen): void {
    if (!planarRoot || screen.planarized) return;
    const rect = screen.el.getBoundingClientRect();
    const w = screen.el.offsetWidth || 1;
    const h = screen.el.offsetHeight || 1;

    /* 先摘出 CSS3D 场景，否则渲染器每帧会继续改写它的 transform，
       把它从 2D 定位里拽回去。 */
    css3dScene.remove(screen.object);
    planarRoot.appendChild(screen.el);

    screen.el.style.position = 'absolute';
    screen.el.style.left = '0';
    screen.el.style.top = '0';
    screen.el.style.transformOrigin = '0 0';
    screen.el.style.transform = `translate(${rect.left}px, ${rect.top}px) scale(${
      rect.width / w
    }, ${rect.height / h})`;

    /* 平面化层整层不吃事件，只有屏风自己吃——否则它会盖住整个视口的点击 */
    planarRoot.style.pointerEvents = 'none';
    screen.el.style.pointerEvents = 'auto';
    screen.planarized = true;
  }

  /** 还原回 3D：清掉内联样式，交回 CSS3DRenderer 每帧重算 */
  function unplanarize(screen: Screen): void {
    if (!planarRoot || !screen.planarized) return;
    screen.el.style.transform = '';
    screen.el.style.left = '';
    screen.el.style.top = '';
    screen.el.style.transformOrigin = '50% 50%';
    screen.el.style.pointerEvents = '';
    planarRoot.removeChild(screen.el);
    css3dScene.remove(screen.object);
    /* 必须换一个新对象再挂回场景：复用原对象会命中渲染器的样式缓存，
       导致 transform 永远不会被重写（详见 Screen#reattach3d 的说明）。 */
    screen.reattach3d();
    css3dScene.add(screen.object);
    screen.planarized = false;
  }

  /** 平面化状态下窗口尺寸变化不必重贴：
      2D transform 是绝对像素，屏风会稳稳停在原处（可能不再居中，但不会错乱）。
      真正需要重算的是"相机投影变了"——而屏风接管期间相机是锁死的。
      与其写一个必然出现"缩放套缩放"的 replanarize，不如让玩家退出重进。 */


  function beginTransition(targetId: string | null): void {
    const now = performance.now();
    const fromPos = camera.position.clone();
    const fromQuat = camera.quaternion.clone();

    let toPos: THREE.Vector3;
    let toQuat: THREE.Quaternion;

    if (targetId) {
      const screen = screens.get(targetId);
      if (!screen) return;
      const pose = screen.enterPose(camera);
      toPos = pose.position;
      toQuat = pose.quaternion;
    } else {
      const follow = options.getFollowPose();
      toPos = follow.position.clone();
      const m = new THREE.Matrix4().lookAt(follow.position, follow.target, new THREE.Vector3(0, 1, 0));
      toQuat = new THREE.Quaternion().setFromRotationMatrix(m);
      // 从屏风飞回跟随位时，若玩家站在屏风背后，直线插值会穿过屏风。
      // 抬一点高度绕过去，比硬穿自然（屏风高 2–3 米，抬 4 米够）。
      if (fromPos.distanceTo(toPos) > 3) {
        toPos.y = Math.max(toPos.y, fromPos.y + 1.2);
      }
    }

    transition = { fromPos, fromQuat, toPos, toQuat, startedAt: now, targetId };
    dimEl?.classList.toggle('is-on', Boolean(targetId));
  }

  function applyTransition(now: number): void {
    if (!transition) return;
    const t = Math.min(1, (now - transition.startedAt) / TRANSITION_MS);
    const k = easeInOutCubic(t);

    camera.position.lerpVectors(transition.fromPos, transition.toPos, k);
    camera.quaternion.slerpQuaternions(transition.fromQuat, transition.toQuat, k);

    if (t >= 1) {
      const target = transition.targetId;
      transition = null;
      if (target) {
        engaged = target;
        const current = screens.get(target);
        current?.setFocus(true);
        /* 相机已到位，此刻平面化：屏风正对相机，3D 透视退化成仿射，
           2D 贴上去与原画面几乎无差，但输入法从此正常（T2.10）。 */
        if (current) planarize(current);
        document.body.dataset.screenEngaged = target;
      } else {
        engaged = null;
        // 退出过场结束，把之前为了遮闪而隐藏的屏风放回来
        for (const s of screens.values()) {
          s.el.style.visibility = '';
        }
        delete document.body.dataset.screenEngaged;
      }
      options.onEngagedChange?.(target);
    }
  }

  /* ---------------- 交互 ---------------- */

  function enterScreen(screen: Screen): void {
    if (engaged === screen.id || transition?.targetId === screen.id) return;
    if (screen.el.dataset.enterable !== 'true') return;
    if (!screen.canEnter()) return;
    // 切到别的屏风：先复位它的视觉态与平面化，避免两个屏风同时亮着
    if (engaged) {
      const prev = screens.get(engaged);
      prev?.setFocus(false);
      if (prev) unplanarize(prev);
    }
    beginTransition(screen.id);
  }

  function exitScreen(): void {
    if (!engaged && !transition?.targetId) return;
    if (engaged) {
      const current = screens.get(engaged);
      current?.setFocus(false);
      if (current) {
        /* 相机马上要动，屏风必须先回到 3D 世界，否则它会钉在屏幕上不动。
           但搬回去的这一帧 CSS3DRenderer 还没重新算矩阵，会闪一下左上角——
           所以先藏起来，等过场结束再放回可见。 */
        current.el.style.visibility = 'hidden';
        unplanarize(current);
      }
    }
    beginTransition(null);
  }

  /* 滚轮仲裁：屏风内滚动不该带动相机变焦。
     相机滚轮监听挂在 window 的冒泡阶段（city.ts），所以这里在 document 的冒泡阶段
     截停即可——document 比 window 早，能拦住。

     为什么挂 document 而不是 css3dRoot：屏风进入贴脸态后会被搬到平面化层，
     已经不在 css3dRoot 子树里；挂在容器上就只能管"没进屏风"的那一半场景。 */
  function onWheel(event: WheelEvent): void {
    const target = event.target as HTMLElement | null;
    if (target && target.closest('.bws-screen')) {
      event.stopPropagation();
    }
  }

  /* 全局 Esc：屏风处于进入态时优先退出，不再传给城里的"停步"逻辑 */
  function onKeyDown(event: KeyboardEvent): void {
    if (event.key !== 'Escape') return;
    if (!engaged && !transition?.targetId) return;
    event.stopPropagation();
    event.preventDefault();
    exitScreen();
  }

  /* 点屏风 = 进入。用 click 而不是 pointerdown：
     玩家拖拽调整视角时手指可能从屏风上划过，click 不会被触发。 */
  function onScreenClick(event: MouseEvent, screen: Screen): void {
    event.stopPropagation();
    if (engaged === screen.id) return;
    enterScreen(screen);
  }

  /* 可编辑元素聚焦时通知外层：主循环据此暂停行走，也让 HUD 收起提示。
     监听挂 document 而不是 css3dRoot——屏风进入贴脸态后已经被搬到平面化层，
     不在 css3dRoot 子树里了，挂在容器上会漏掉全部输入事件。 */
  function editableTarget(event: FocusEvent): HTMLElement | null {
    const target = event.target as HTMLElement | null;
    if (!target) return null;
    // 只管屏风里的输入：城里其他面板（喊话、聊天）有自己的开合管理
    if (!target.closest('.bws-screen')) return null;
    const tag = target.tagName;
    const editable =
      tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable;
    return editable ? target : null;
  }

  function onFocusIn(event: FocusEvent): void {
    if (editableTarget(event)) options.onDomFocus?.(true);
  }

  function onFocusOut(event: FocusEvent): void {
    if (editableTarget(event)) options.onDomFocus?.(false);
  }

  window.addEventListener('keydown', onKeyDown, true);
  document.addEventListener('wheel', onWheel, { passive: false });
  document.addEventListener('focusin', onFocusIn);
  document.addEventListener('focusout', onFocusOut);

  /* ---------------- 对外 API ---------------- */

  /** 屏风与它的点击监听一一对应，销毁时按 id 取回解绑 */
  const clickHandlers = new Map<string, (e: MouseEvent) => void>();

  function disposeScreen(id: string): void {
    const screen = screens.get(id);
    if (!screen) return;
    const handler = clickHandlers.get(id);
    if (handler) {
      screen.el.removeEventListener('click', handler);
      clickHandlers.delete(id);
    }
    if (engaged === id) exitScreen();
    css3dScene.remove(screen.object);
    screens.delete(id);
    screen.destroy();
  }

  /** 句柄是屏风的轻量视图：同一面屏风无论取多少次，行为都一致 */
  function makeHandle(screen: Screen): ScreenHandle {
    return {
      id: screen.id,
      el: screen.el,
      body: screen.body,
      setTitle: (t, s) => screen.setTitle(t, s),
      setTone: (t) => screen.setTone(t),
      setEnterable: (on) => {
        screen.setEnterable(on);
        screen.resetFoot();
      },
      enter: () => enterScreen(screen),
      exit: exitScreen,
      isEngaged: () => engaged === screen.id,
      destroy: () => disposeScreen(screen.id),
    };
  }

  function add(spec: ScreenSpec): ScreenHandle {
    const screen = new Screen(spec);
    screens.set(spec.id, screen);
    css3dScene.add(screen.object);
    screen.resetFoot();

    const clickHandler = (e: MouseEvent) => onScreenClick(e, screen);
    screen.el.addEventListener('click', clickHandler);
    clickHandlers.set(spec.id, clickHandler);

    return makeHandle(screen);
  }

  function setOccluders(objects: THREE.Object3D[]): void {
    for (const obj of occluderObjects) {
      obj.traverse((child) => child.layers.disable(OCCLUDER_LAYER));
    }
    occluderObjects.length = 0;
    for (const obj of objects) {
      obj.traverse((child) => {
        child.layers.enable(OCCLUDER_LAYER);
      });
      occluderObjects.push(obj);
    }
    /* 遮挡 pass 用 camera.layers.set(1) 过滤，会把层 0 的光源一起滤掉，
       结果是所有遮挡实体变成纯黑剪影（S0 踩过）。这里把所有灯都补上层 1。 */
    scene.traverse((child) => {
      const light = child as THREE.Light;
      if (light.isLight) {
        light.layers.enable(OCCLUDER_LAYER);
      }
    });
  }

  function render(): void {
    if (disposed) return;
    camera.updateMatrixWorld();
    css3d.render(css3dScene, camera);

    /* 遮挡 pass：只在真的有屏风、且注册了遮挡实体时才画。
       全场景重画一遍代价接近翻倍，城里没屏风时完全不该付这个钱。

       try/finally 不是洁癖：camera.layers 被改成只剩遮挡层后，若 render 中途抛错
       而没有恢复，下一帧 CSS3DRenderer 的 layers.test 会全部失败，屏风会集体
       display:none——现场表现为"屏风凭空消失"，且再也不会回来。 */
    const hasOccluders = screens.size > 0 && occluderObjects.length > 0;
    if (hasOccluders) {
      const bg = scene.background;
      const mask = camera.layers.mask;
      try {
        scene.background = null;
        camera.layers.set(OCCLUDER_LAYER);
        occluder.render(scene, camera);
      } finally {
        camera.layers.mask = mask;
        scene.background = bg;
      }
    } else {
      occluder.clear();
    }
  }

  function resize(w: number, h: number): void {
    css3d.setSize(w, h);
    occluder.setSize(w, h);
  }

  return {
    add,
    get: (id) => {
      const screen = screens.get(id);
      return screen ? makeHandle(screen) : null;
    },
    remove: disposeScreen,
    render,
    resize,
    isControlling: () => Boolean(engaged) || Boolean(transition),
    /* 过场在渲染之前推进：主循环的顺序是 updateCamera → renderer.render → screens.render，
       若把插值放到 render 里，这一帧的角色/相机矩阵已经用旧位置算完了，会慢一帧。 */
    updateCamera: () => {
      applyTransition(performance.now());
    },
    engagedId: () => engaged,
    nearest: (x, z, radius) => {
      let best: Screen | null = null;
      let bestDist = radius;
      for (const screen of screens.values()) {
        if (screen.el.dataset.enterable !== 'true') continue;
        const c = screen.center();
        const d = Math.hypot(x - c.x, z - c.z);
        if (d < bestDist) {
          bestDist = d;
          best = screen;
        }
      }
      return best ? makeHandle(best) : null;
    },
    setOccluders,
    exit: exitScreen,
    dispose: () => {
      disposed = true;
      window.removeEventListener('keydown', onKeyDown, true);
      document.removeEventListener('wheel', onWheel, true);
      document.removeEventListener('focusin', onFocusIn);
      document.removeEventListener('focusout', onFocusOut);
      for (const screen of screens.values()) {
        if (screen.planarized) unplanarize(screen);
        css3dScene.remove(screen.object);
        screen.destroy();
      }
      screens.clear();
      css3d.domElement.remove();
      occluder.domElement.remove();
      occluder.dispose();
    },
  };
}
