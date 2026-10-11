import {
  PMREMGenerator,
  PerspectiveCamera,
  Scene,
  Timer,
  type Material,
  type WebGLRenderTarget,
} from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { disposeSubtree } from './common';
import {
  advanceEnvironment,
  sampleEnvironment,
  type EnvironmentSample,
  type EnvironmentState,
} from './env';
import { createRenderer } from './renderer';
import { resolveViewport, type Viewport } from './viewport';
import type {
  SceneAtmosphere,
  SceneBuild,
  SceneControls,
  SceneContext,
  SceneDefinition,
  SceneId,
  SceneInteractionSnapshot,
} from './types';
import type { SceneOptions } from './options';

/**
 * 舞台：把场景图、渲染器、相机控制器和一个动画循环装在一起，对外只暴露
 * `start()`、`loadScene()`、一组开关和一个 `dispose()`。
 *
 * React 组件只需要管两件事：**挂载时建、卸载时毁**。所有和帧、和 GPU 打交道的
 * 细节都封在这里，组件不会因为多写一个 effect 就漏掉一次释放。
 */

export type StageStats = {
  fps: number;
  /** 绘制缓冲尺寸（CSS 像素） */
  width: number;
  height: number;
  pixelRatio: number;
  /** 每帧的绘制调用数 —— 判断"场景重不重"最直接的数字 */
  drawCalls: number;
  triangles: number;
};

/**
 * 舞台的运行时诊断。
 *
 * `geometries` / `textures` 是**当前真正待在 GPU 上的资源数**（three 内部维护）。
 * 它们的价值在于：切场景之后这两个数应当**回落**而不是累加。
 * 「资源释放干净了没有」平时只能靠看画面正不正常来猜，
 * 有了这两个计数，它就变成一条可断言的数字 —— 端到端脚本正是靠它证明
 * 反复切场景不会把显存吃光。
 */
export type StageDiagnostics = {
  sceneId: SceneId;
  /**
   * 累计**渲染**帧数（只数真正画出画面的帧，场景加载中的空转帧不算）。
   *
   * 存在的理由是自动验证脚本需要一个**帧对齐**的等待原语：`setEnvironment()` 是
   * 同步改光和雾的，但画面要等下一帧才重绘 —— 脚本若按墙钟 `sleep` 个几百毫秒就去截图，
   * 截到的是上一帧。实测软件渲染（swiftshader）下单帧能到 2 秒，任何写死的等待都会失效。
   * 有了这个计数，脚本就能说"等两帧"而不是"等 650 毫秒"，在快机器上自然变快。
   */
  frames: number;
  /** 当前上传到 GPU 的几何体数量 */
  geometries: number;
  /** 当前上传到 GPU 的纹理数量 */
  textures: number;
  /** 已编译的着色器程序数量 */
  programs: number;
  drawCalls: number;
  triangles: number;
};

export type StageOptions = {
  /** 初始场景 */
  scene: SceneDefinition;
  /** 初始渲染选项 */
  options: SceneOptions;
  /** 初始环境（时刻、地点、时间流逝倍速） */
  environment: EnvironmentState;
  onStats?: (stats: StageStats) => void;
  /** 统计回调的最小间隔（毫秒），默认 500 */
  statsInterval?: number;
};

export type Stage = {
  start(): void;
  /**
   * 切换场景。
   *
   * 内部只在**场景图**这一层做替换：渲染器、画布、相机、相机控制器、
   * 环境贴图全部复用。重建它们的代价不只是性能 —— 重建相机意味着要改
   * `OrbitControls.object` 并复位它的内部球坐标状态，那是一条只会引入偶发 bug 的路。
   */
  loadScene(scene: SceneDefinition, options: SceneOptions): void;
  /**
   * 设定环境基准。
   *
   * 传入的是**基准**而不是"当前值"：`flow > 0` 时舞台会从这一刻起自己往前推进。
   * 界面拨动滑块时传的就是新基准 —— 于是"拨到 18:00 然后让它走"这句话
   * 在代码里就是两步，没有歧义。
   */
  setEnvironment(state: EnvironmentState): void;
  /**
   * 读取**当前**环境状态。
   *
   * 时间流逝时它与最近一次 `setEnvironment` 的入参不同 —— 界面要显示"现在几点"
   * 就必须读它，而不是读自己手里的那份基准。与小地图读 `interaction()` 是同一个套路：
   * 走读取入口，不走 React 状态。
   */
  environment(): EnvironmentState;
  /** 当前场景 id。界面用它高亮当前选中的卡片 */
  readonly sceneId: SceneId;
  /** 取一次运行时诊断（GPU 资源计数、绘制统计） */
  diagnostics(): StageDiagnostics;
  /**
   * 当前场景的交互快照（主人公位置、相机朝向、小地图地物）。
   *
   * **无交互场景返回 `null`** —— 调用方据此决定是否显示小地图，
   * 而不是去猜"这个场景有没有主人公"。
   */
  interaction(): SceneInteractionSnapshot | null;
  /** 相机绕场景自动环绕 */
  setAutoRotate(enabled: boolean): void;
  setShadows(enabled: boolean): void;
  setWireframe(enabled: boolean): void;
  setGridVisible(visible: boolean): void;
  /** 停止循环并释放全部 GPU 资源。**调用后实例不可再用。** */
  dispose(): void;
};

const DEFAULT_STATS_INTERVAL = 500;

/**
 * 重烘环境贴图的**高度角阈值**（弧度，约 1.2°）。
 *
 * 环境贴图承载的是"此刻的天光"。太阳没怎么动就没必要重算 ——
 * `PMREMGenerator.fromScene` 是一次完整的卷积，比渲染一帧贵得多。
 * 1.2° 大致是肉眼看不出天光变化的下限。
 */
const IBL_ALTITUDE_STEP = 0.021;

/**
 * 两次重烘之间的**最小间隔**（毫秒）。
 *
 * 拖动时刻滑块时高度角连续变化，光靠阈值挡不住 —— 拖一次可能触发几十次卷积，
 * 手感会明显发涩。这个间隔把重烘压到每秒两三次，拖动时依然跟得上。
 */
const IBL_MIN_INTERVAL = 320;

/**
 * 画布类名。
 *
 * 画布由本模块创建（原因见 `createStage`），但它的样式属于组件 ——
 * 两边靠这个常量对齐，改名时 `components/SceneView.less` 要一起改。
 */
const CANVAS_CLASS = 'scene-view__canvas';

export function createStage(host: HTMLElement, stageOptions: StageOptions): Stage {
  const statsInterval = stageOptions.statsInterval ?? DEFAULT_STATS_INTERVAL;

  /**
   * 画布在这里创建，**不接受外部传入**。
   *
   * 起因是 WebGL 的一条硬约束：`dispose()` 里的 `forceContextLoss()` 会把这张
   * canvas 的上下文**永久**标记为 lost，此后 `getShaderPrecisionFormat()` 一律返回
   * `null`，在同一个 canvas 上再 new 一个 `WebGLRenderer` 会直接抛
   * `Cannot read properties of null (reading 'precision')`（three 在
   * `WebGLCapabilities` 里读这个值）。
   *
   * 而"同一个 canvas 被复用"在 React 下是常态：开发期 StrictMode 会**双跑 effect**
   * （建 → 毁 → 建），组件若用 JSX 持有一张固定的 `<canvas>`，第二次就撞上这个错，
   * 表现是"一进页面就显示无法创建 WebGL 上下文"。把画布的创建收进舞台、
   * 让画布与上下文同生共死，这种复用就不可能发生。
   */
  const canvas = host.ownerDocument.createElement('canvas');
  canvas.className = CANVAS_CLASS;
  host.appendChild(canvas);

  const { renderer, resize, dispose: disposeRenderer } = createRenderer(canvas);

  /**
   * 环境贴图（IBL）。
   *
   * 没有它，`MeshStandardMaterial` 的金属部分没有东西可反射，会渲染成一团死黑 ——
   * 这是"用了 PBR 材质但看起来比 Phong 还差"最常见的原因。
   *
   * 它有两个来源：
   * - **兜底**：three 自带的程序化室内环境 `RoomEnvironment`。没有大气能力的场景
   *   （室内、太空）一直用它，切场景时复用同一份；
   * - **天穹**：有大气能力的场景（室外），由 `maybeBakeEnvironment()` 按当前时刻重烘 ——
   *   黄昏时建筑被染成金色，靠的就是这一步。
   *
   * ⚠️ 与"只生成一次"的旧做法不同：`pmrem` **不能**用完就 dispose 了，
   * 重烘还得靠它。它的释放搬到了 `dispose()` 里。
   */
  const pmrem = new PMREMGenerator(renderer);
  const roomEnvironment = new RoomEnvironment();
  let environmentTarget: WebGLRenderTarget = pmrem.fromScene(roomEnvironment, 0.04);
  // `RoomEnvironment` 不在场景图里，traverse 找不到，必须单独释放
  disposeSubtree(roomEnvironment);

  /**
   * 重烘用的临时场景：**只放天穹**。
   *
   * 若把整棵场景图烘进去，建筑会把自己的颜色当环境光反射回来 ——
   * 于是"环境光照亮建筑"变成"建筑照亮自己"，画面会莫名其妙地发亮。
   * 复用同一个实例，免得每次重烘都新建一个 `Scene` 再等 GC。
   */
  const captureScene = new Scene();

  /**
   * 远裁剪面给到 400。
   *
   * 太空场景的星点场半径接近 100、行星在 70 开外，默认的 200 会把它们**整片切掉**
   * 而且不报任何错 —— 表现只是"星星突然少了一半"。近裁剪面维持 0.1
   * （飞船贴到最近距离时要看舷窗），近远比 4000 对数深度下没有问题。
   */
  const camera = new PerspectiveCamera(50, 1, 0.1, 400);

  const orbit = new OrbitControls(camera, canvas);
  orbit.enableDamping = true;
  orbit.dampingFactor = 0.06;
  orbit.autoRotateSpeed = 0.6;
  // 限制俯仰角上限，相机就不会钻到地面以下 —— 从地底看场景没有意义，还容易迷向
  orbit.maxPolarAngle = Math.PI / 2 - 0.03;

  let currentBuild: SceneBuild | null = null;
  let currentControls: SceneControls | null = null;
  let currentAtmosphere: SceneAtmosphere | null = null;
  let currentSceneId: SceneId = stageOptions.scene.meta.id;

  /**
   * 环境状态，以及它算出的画面指令。
   *
   * `sample` 是**缓存**：环境没变时不必每帧重算太阳位置。时间流逝时才推进
   * （`advanceEnvironment` 同样是纯函数，所以"推进"与"直接给定"得到的结果逐值一致）。
   */
  let environmentState: EnvironmentState = stageOptions.environment;
  let environmentSample: EnvironmentSample = sampleEnvironment(environmentState);
  /** 上次烘环境贴图时太阳的高度角。`NaN` = 还没烘过，下一次检查必烘 */
  let bakedAltitude = Number.NaN;
  let lastBakeAt = 0;

  let currentViewport: Viewport | null = null;

  /** 按容器实际尺寸同步相机与绘制缓冲。尺寸没变就直接返回，避免无意义的每帧重算。 */
  function applyViewport(): void {
    const next = resolveViewport(host.getBoundingClientRect(), window.devicePixelRatio);

    if (
      currentViewport &&
      currentViewport.width === next.width &&
      currentViewport.height === next.height &&
      currentViewport.pixelRatio === next.pixelRatio
    ) {
      return;
    }
    currentViewport = next;

    camera.aspect = next.width / next.height;
    camera.updateProjectionMatrix();
    resize(next);
  }

  /**
   * 开关阴影会让所有材质的着色器变体失效，必须显式要求重新编译。
   * 漏掉这一步的表现是"关掉阴影后画面变暗/花屏"，而不是没变化。
   */
  function compileMaterials(): void {
    currentBuild?.scene.traverse((child) => {
      const holder = child as { material?: Material | Material[] };
      if (!holder.material) {
        return;
      }
      const materials = Array.isArray(holder.material) ? holder.material : [holder.material];
      materials.forEach((material) => {
        material.needsUpdate = true;
      });
    });
  }

  /** 安装一个场景：建新的、装旧的、把相机搬到该场景的取景位。 */
  function installScene(definition: SceneDefinition, options: SceneOptions): void {
    // 先释放旧场景。**顺序不能反**：新场景若先建，峰值显存会是两份场景之和。
    // 交互控制器与大气同样要先拆 —— 它们都挂在旧场景图上，晚拆就会去操作一棵已清空的树
    currentControls?.dispose();
    currentControls = null;
    currentAtmosphere?.dispose();
    currentAtmosphere = null;
    currentBuild?.dispose();

    const build = definition.create();
    build.scene.environment = environmentTarget.texture;
    build.scene.environmentIntensity = definition.meta.environmentIntensity;
    currentBuild = build;
    currentSceneId = definition.meta.id;

    const context: SceneContext = { camera, domElement: canvas };
    currentControls = build.createControls?.(context) ?? null;
    // 大气是**可选能力**：室内与太空场景不实现它，这里自然就是 null，不需要特判
    currentAtmosphere = build.createAtmosphere?.() ?? null;

    /**
     * 有自带控制器的场景，右键归"设置行动终点"，因此关掉轨道平移。
     * 左键旋转与滚轮缩放**保留** —— 那是相机调节，控制器不接管，
     * 用户对"放大缩小视角"的预期就是滚轮。
     */
    orbit.enablePan = !currentControls;

    const [positionX, positionY, positionZ] = definition.meta.camera.position;
    const [targetX, targetY, targetZ] = definition.meta.camera.target;
    camera.position.set(positionX, positionY, positionZ);
    orbit.target.set(targetX, targetY, targetZ);
    orbit.minDistance = definition.meta.distance[0];
    orbit.maxDistance = definition.meta.distance[1];
    orbit.update();

    // 选项要**在场景图就位之后**应用：线框与网格都是作用在具体物体上的
    applyOptions(options, build);

    /**
     * 环境要在场景图与大气都就位之后立刻应用一次，并**强制重烘**环境贴图。
     *
     * 不这么做的话，新场景的头几帧会顶着上一个场景的环境贴图（甚至是没有天穹的
     * 室内环境），表现是"刚切进来那一瞬间颜色不对"。对"古建筑 ↔ 太空"这类
     * 明暗差着量级的来回切换尤其明显。
     */
    bakedAltitude = Number.NaN;
    applyEnvironmentLook();
    maybeBakeEnvironment(true);
  }

  /**
   * 把相机焦点同步到交互控制器的目标点。
   *
   * 关键在"**整体平移**"：把相机与 `orbit.target` 一起挪同样的位移，
   * 镜头与主人公的相对位置（环绕姿态、距离）就原样保留，看起来是镜头跟着人走。
   * 只改 `target` 不动相机的话，镜头会原地转头 —— 人一走出画面就只剩个俯视小点。
   */
  function syncFocus(): void {
    const focus = currentControls?.focus();
    if (!focus) {
      return;
    }
    const deltaX = focus[0] - orbit.target.x;
    const deltaY = focus[1] - orbit.target.y;
    const deltaZ = focus[2] - orbit.target.z;
    orbit.target.set(focus[0], focus[1], focus[2]);
    camera.position.x += deltaX;
    camera.position.y += deltaY;
    camera.position.z += deltaZ;
  }

  function applyOptions(options: SceneOptions, build: SceneBuild | null = currentBuild): void {
    if (!build) {
      return;
    }
    orbit.autoRotate = options.autoRotate;
    renderer.shadowMap.enabled = options.shadows;
    renderer.shadowMap.needsUpdate = true;
    build.setWireframe(options.wireframe);
    build.setGridVisible(options.showGrid);
    compileMaterials();
  }

  /**
   * 把当前环境指令送到大气与渲染器上。
   *
   * **刻意不碰环境贴图** —— 那是 `maybeBakeEnvironment()` 的事。两者分开，
   * 是因为它们的开销差着两个数量级：改一下光色是几次赋值，重烘一次是完整卷积。
   * 混在一个函数里，迟早会有人为了"让光跟上"而顺手把卷积也放进每帧路径。
   */
  function applyEnvironmentLook(): void {
    currentAtmosphere?.update(environmentSample.look);
    renderer.toneMappingExposure = environmentSample.look.exposure;
  }

  /**
   * 检查该不该重烘环境贴图。**每帧调用**，代价只是一次比较。
   *
   * 为什么不挂在"环境变化"的事件上：节流窗口可能把最后一次变化挡在门外
   * （拖滑块时最常见），而那次变化之后再没有新事件了 —— 只有下一帧再来问一次，
   * 才不会把天光永久停在旧值上。
   */
  function maybeBakeEnvironment(force: boolean): void {
    const source = currentAtmosphere?.captureSource();
    if (!source || !currentBuild) {
      return;
    }

    const altitude = environmentSample.solar.altitude;
    const moved =
      !Number.isFinite(bakedAltitude) || Math.abs(altitude - bakedAltitude) >= IBL_ALTITUDE_STEP;
    const now = performance.now();
    if (!force && !(moved && now - lastBakeAt >= IBL_MIN_INTERVAL)) {
      return;
    }

    // 天穹只在烘的这一刻属于临时场景，烘完立刻还回去
    captureScene.add(source);
    const next = pmrem.fromScene(captureScene, 0.04);
    captureScene.remove(source);

    currentBuild.scene.environment = next.texture;
    // 旧目标必须显式释放：GPU 资源不随 GC 回收，攒着就是漏显存
    environmentTarget.dispose();
    environmentTarget = next;
    bakedAltitude = altitude;
    lastBakeAt = now;
  }

  installScene(stageOptions.scene, stageOptions.options);

  /**
   * 观察**容器**而不是 window。
   *
   * 子应用被基座加载时，窗口大小没变但内容区可能因为基座布局变化而变窄/变高，
   * 只听 window.resize 会漏掉这种变化，画面被拉伸。ResizeObserver 两种情形都覆盖。
   */
  const hasResizeObserver = typeof ResizeObserver !== 'undefined';
  const observer = hasResizeObserver ? new ResizeObserver(() => applyViewport()) : null;
  observer?.observe(host);
  // 没有 ResizeObserver 的环境（老浏览器 / 测试替身）退回监听 window
  if (!hasResizeObserver) {
    window.addEventListener('resize', applyViewport);
  }

  /**
   * `Timer` 是 three 核心里的计时器，r183 起取代 `Clock`（后者已弃用）。
   * 用它而不是自己拿 `performance.now()` 相减，是因为它多管两件容易漏的事：
   *
   * 1. `update()` 每帧推进一次内部状态，`getDelta()` / `getElapsed()` 在同一帧内
   *    **可以反复读且值一致** —— 自己算的话，多个消费者各读一次时间就会互相干扰；
   * 2. `connect(document)` 借 Page Visibility API 兜住"切到别的标签页再切回来"：
   *    不做这件事，回来那一帧的 delta 是整段挂起时长，物体会瞬移一大截。
   *
   * 精度是秒，与 `Clock` 一致，所以 `orbit.update(delta)` 的语义不变。
   */
  const timer = new Timer();
  timer.connect(host.ownerDocument);

  let rafId = 0;
  let running = false;
  let framesInWindow = 0;
  let statsWindowStart = 0;
  /** 累计渲染帧数，暴露给 `diagnostics()` 供脚本做帧对齐等待。计数点必须在 `render()` 之后 */
  let renderedFrames = 0;

  function frame(timestamp: number): void {
    // 先排下一帧：这样即便本帧抛错也不会把循环掐死
    rafId = requestAnimationFrame(frame);

    timer.update(timestamp);
    const build = currentBuild;
    if (!build) {
      return;
    }

    /**
     * 时间流逝由**舞台**推进，而不是 React 的 state。
     *
     * "时间流逝"本质是播放，播放属于帧循环 —— 让 React 以 60Hz `setState` 驱动它，
     * 等于拿重渲染当节拍器。舞台推进之后，界面通过 `environment()` 每帧读回，
     * 与小地图读 `interaction()` 是同一个套路。
     * `flow` 为 0（默认）时 `advanceEnvironment` 原样返回，这里只是一次比较。
     */
    if (environmentState.flow > 0) {
      const advanced = advanceEnvironment(environmentState, timer.getDelta());
      if (advanced !== environmentState) {
        environmentState = advanced;
        environmentSample = sampleEnvironment(advanced);
        applyEnvironmentLook();
      }
    }
    // 上一帧可能被节流挡掉了一次重烘，这里补上（静止时只花一次比较）
    maybeBakeEnvironment(false);

    build.update(timer.getElapsed());
    // 大气推进的是"风"这类连续量（云被吹走），与环境参数无关
    currentAtmosphere?.advance(timer.getElapsed());
    // 主人公先走，镜头再跟 —— 反过来的话画面永远慢一帧
    currentControls?.update(timer.getElapsed(), timer.getDelta());
    syncFocus();
    // 阻尼与自动环绕都依赖 deltaTime，传进去才能做到"掉帧时速度不变"
    orbit.update(timer.getDelta());
    renderer.render(build.scene, camera);

    renderedFrames += 1;
    framesInWindow += 1;
    const windowElapsed = timestamp - statsWindowStart;
    if (windowElapsed >= statsInterval) {
      stageOptions.onStats?.({
        fps: Math.round((framesInWindow * 1000) / windowElapsed),
        width: currentViewport?.width ?? 0,
        height: currentViewport?.height ?? 0,
        pixelRatio: currentViewport?.pixelRatio ?? 1,
        drawCalls: renderer.info.render.calls,
        triangles: renderer.info.render.triangles,
      });
      framesInWindow = 0;
      statsWindowStart = timestamp;
    }
  }

  return {
    get sceneId(): SceneId {
      return currentSceneId;
    },

    diagnostics(): StageDiagnostics {
      return {
        sceneId: currentSceneId,
        frames: renderedFrames,
        // `info.memory` 是 three 维护的"当前在 GPU 上的资源数"，
        // 不是"累计创建过多少"—— 所以它才是判断释放是否干净的可靠依据
        geometries: renderer.info.memory.geometries,
        textures: renderer.info.memory.textures,
        programs: renderer.info.programs?.length ?? 0,
        drawCalls: renderer.info.render.calls,
        triangles: renderer.info.render.triangles,
      };
    },

    interaction(): SceneInteractionSnapshot | null {
      return currentControls?.snapshot() ?? null;
    },

    start(): void {
      if (running) {
        return;
      }
      running = true;
      // 先量一次尺寸再起循环：容器可能已经有尺寸，等 ResizeObserver 首次回调太被动
      applyViewport();
      statsWindowStart = performance.now();
      rafId = requestAnimationFrame(frame);
    },

    loadScene(scene: SceneDefinition, options: SceneOptions): void {
      installScene(scene, options);
      // 场景刚建好，尺寸可能与上一次不同（比如基座布局变了），顺手校一次
      applyViewport();
    },

    setEnvironment(state: EnvironmentState): void {
      environmentState = state;
      environmentSample = sampleEnvironment(state);
      // 光和雾立刻跟上（便宜）；环境贴图交给节流，下一帧补烘
      applyEnvironmentLook();
      maybeBakeEnvironment(false);
    },

    environment(): EnvironmentState {
      return environmentState;
    },

    setAutoRotate(enabled: boolean): void {
      orbit.autoRotate = enabled;
    },

    setShadows(enabled: boolean): void {
      renderer.shadowMap.enabled = enabled;
      renderer.shadowMap.needsUpdate = true;
      compileMaterials();
    },

    setWireframe(enabled: boolean): void {
      currentBuild?.setWireframe(enabled);
    },

    setGridVisible(visible: boolean): void {
      currentBuild?.setGridVisible(visible);
    },

    dispose(): void {
      running = false;
      cancelAnimationFrame(rafId);
      observer?.disconnect();
      window.removeEventListener('resize', applyViewport);
      // 断开 Page Visibility 监听：不解开的话每次挂载都会在 document 上多留一个监听器
      timer.dispose();

      // 先拆交互控制器与大气：两者都挂在场景图上，晚拆就会在已清空的树上做无用工
      currentControls?.dispose();
      currentControls = null;
      currentAtmosphere?.dispose();
      currentAtmosphere = null;
      orbit.dispose();
      environmentTarget.dispose();
      // 临时场景里可能还留着天穹的引用，清掉以免它被误认为还需要
      captureScene.clear();
      // 重烘环境贴图靠它，整个生命周期里只建这一个实例，所以在这里才释放
      pmrem.dispose();
      currentBuild?.dispose();
      currentBuild = null;
      disposeRenderer();
      // 画布随上下文一起丢：留着它只会诱使后来者复用一个已经 lost 的 canvas
      canvas.remove();
    },
  };
}
