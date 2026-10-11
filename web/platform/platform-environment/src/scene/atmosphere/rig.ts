import {
  AmbientLight,
  Color,
  DirectionalLight,
  Fog,
  Vector3,
  type ColorRepresentation,
  type Object3D,
  type Scene,
  type ShaderMaterial,
} from 'three';
import { Sky } from 'three/addons/objects/Sky.js';
import type { EnvironmentLook } from '../env';
import type { SceneAtmosphere } from '../types';

/**
 * 大气装配：把 `EnvironmentLook`（纯数字）翻译成场景里的**天穹、光与雾**。
 *
 * 它是**场景的可选能力**，不是舞台的全局设施 —— 与 `createControls?()` 同级。
 * 理由是这层生产的是几何体与材质，而"这个场景长什么样"属于场景自己：
 * 太空里没有天穹，室内只有灯没有太阳。做成全局设施的话，每个场景都得写一遍
 * `if (不是太空)`，而那种判断迟早会漏一处。
 *
 * 三件事在这里收口：
 * 1. **主光**：太阳在地平线上时指向太阳，落下之后指向反日点（近似满月）；
 * 2. **天穹**：three 的 `Sky`（Preetham 散射）—— 日落泛红是它算出来的，不是调出来的；
 * 3. **雾与背景**：两者**必须同色**，否则"远处融进背景"这句话就不成立。
 */

/**
 * 天穹的尺度。
 *
 * 用官方的 450000：`Sky` 的顶点着色器里有一行 `gl_Position.z = gl_Position.w`，
 * 把天穹强制压到远平面上 —— 于是它**不受相机远裁剪面影响**，无论相机 far 是 400 还是 400000。
 * 老实给一个"刚好包住场景"的半径也行，但那要求半径随相机 far 走，平白多一处要同步的耦合。
 */
const SKY_SCALE = 450000;

/**
 * 主光的最低仰角。
 *
 * 光贴着地平线打时，阴影正交相机的视锥会退化成一条线 —— 表现是**阴影整片消失且不报错**，
 * 这是"阴影开关坏了"这类误判最常见的来源。抬到 5° 之后黄昏的长影子照样在，只是不再退化。
 */
const MIN_KEY_ELEVATION = 0.09;

/**
 * 云的时间缩放。
 *
 * `Sky` 里云的漂移量是 `time × cloudSpeed`，而 cloudSpeed 的量级是 `2e-5` ——
 * 直接喂真实秒数，云几小时才挪一个像素。乘上这个系数之后，
 * "一眼看得出云在走"与"不至于像快进"之间才有一个能用的挡位。
 */
const WIND_TIME_SCALE = 45;

/** 一期的云量：晴天疏云。二期交给天气状态机（见 ADR-0016） */
const CLEAR_SKY_COVERAGE = 0.16;

export type AtmosphereRigConfig = {
  /** 天穹、雾、灯都装在这棵场景图上 */
  scene: Scene;
  /** 主光。由场景创建（只有它知道阴影该罩多大），rig 只驱动方向、颜色与强度 */
  keyLight: DirectionalLight;
  /** 环境光。同上：由场景创建，rig 只驱动 */
  ambient: AmbientLight;
  /** 场景基色（`SceneMeta.background`）。rig 把它按权重混向天光 */
  baseBackground: ColorRepresentation;
  /** 雾的基准起止。**给了才做能见度联动** —— 各场景的雾距基调差着数量级 */
  fog?: { near: number; far: number };
  /** 主光到原点的距离。默认 60 */
  keyDistance?: number;
  /** 是否加天穹。室内与太空传 `false`（它们只借光与雾的联动） */
  sky?: boolean;
  /** 云量 0~1。不传用晴天疏云 */
  cloudCoverage?: number;
};

export function createAtmosphereRig(config: AtmosphereRigConfig): SceneAtmosphere {
  const skyEnabled = config.sky ?? true;
  const keyDistance = config.keyDistance ?? 60;
  const cloudCoverage = config.cloudCoverage ?? CLEAR_SKY_COVERAGE;

  const baseBackground = new Color(config.baseBackground);
  const background = new Color(config.baseBackground);
  config.scene.background = background;
  if (config.fog && config.scene.fog instanceof Fog) {
    config.scene.fog.color.copy(background);
  }

  /**
   * 主天穹与"捕获天穹"是两个实例。
   *
   * 为什么要两份：烘环境贴图（`PMREMGenerator.fromScene`）需要一个**只含天穹**的场景 ——
   * 若把主天穹移出去烘、烘完再放回来，就多出一段"天穹暂时不在场景里"的脆弱窗口。
   * 两份各占一个 BoxGeometry 加一个共享着色器，代价可以忽略。
   */
  function createSky(): Sky | null {
    if (!skyEnabled) {
      return null;
    }
    const mesh = new Sky();
    mesh.scale.setScalar(SKY_SCALE);
    // 天穹不该参与阴影：它是背景，把影子投在它身上只是白白多渲染一遍
    mesh.castShadow = false;
    mesh.receiveShadow = false;
    return mesh;
  }

  const sky = createSky();
  if (sky) {
    config.scene.add(sky);
  }
  const captureSky = createSky();

  /** 真实太阳方向（天穹用）。夜间的天穹仍按真实太阳位置着色 —— 那才是夜色 */
  const sunDirection = new Vector3();
  /** 主光方向。夜间取反成"月亮"，与天穹不是同一个方向 */
  const keyDirection = new Vector3();
  const horizon = new Color();

  function applySky(target: Sky | null, look: EnvironmentLook): void {
    if (!target) {
      return;
    }
    const uniforms = (target.material as ShaderMaterial).uniforms;
    uniforms.turbidity.value = look.sky.turbidity;
    uniforms.rayleigh.value = look.sky.rayleigh;
    uniforms.mieCoefficient.value = look.sky.mieCoefficient;
    uniforms.mieDirectionalG.value = look.sky.mieDirectionalG;
    // 云量是**场景级常量**而不是逐帧变量：它属于天气，不属于时刻
    uniforms.cloudCoverage.value = cloudCoverage;
    uniforms.sunPosition.value.copy(sunDirection);
  }

  return {
    advance(elapsed: number): void {
      // 云随"风"漂移。这一帧的云位置只由 elapsed 决定 —— 仍然是纯函数
      const drift = elapsed * WIND_TIME_SCALE;
      if (sky) {
        (sky.material as ShaderMaterial).uniforms.time.value = drift;
      }
      if (captureSky) {
        (captureSky.material as ShaderMaterial).uniforms.time.value = drift;
      }
    },

    update(look: EnvironmentLook): void {
      const [directionX, directionY, directionZ] = look.keyDirection;
      sunDirection.set(directionX, directionY, directionZ);

      /**
       * 夜间把主光指向**反日点**（近似满月：月亮在太阳对面）。
       *
       * 太阳落到地平线下之后，它的直射光已被地球挡住 —— 再照原方向打光，
       * 光就从地底往上照，比没有光还假。取反之后方位角仍跟着天走（月亮也东升西落），
       * 而且**影子方向在夜里自然翻到另一边**，与"太阳在地平线下"是自洽的。
       * 切换点放在 -4°（民用暮光之后），那时主光强度已降到 0.16，翻方向看不出来。
       */
      keyDirection.copy(sunDirection);
      if (look.night) {
        keyDirection.negate();
      }
      if (keyDirection.y < MIN_KEY_ELEVATION) {
        keyDirection.y = MIN_KEY_ELEVATION;
      }
      keyDirection.normalize();

      config.keyLight.color.setHex(look.keyColor);
      config.keyLight.intensity = look.keyIntensity;
      config.keyLight.position.copy(keyDirection).setLength(keyDistance);

      config.ambient.color.setHex(look.ambientColor);
      config.ambient.intensity = look.ambientIntensity;

      /**
       * 背景 = 场景基色 **混向** 天光。
       *
       * 取"混合"而不是"直接用天光色"：场景基色是每个场景的气质（古建筑偏紫灰、
       * 丛林偏墨绿），天光色是现实（清晨偏冷、黄昏偏橙）。权重由 `palette` 给出，
       * 于是场景基色在任何时刻都还认得出来 —— 这正是"现实映射"与"场景调性"的折中。
       */
      horizon.setHex(look.horizonColor);
      background.copy(baseBackground).lerp(horizon, look.backgroundWeight);

      if (config.fog && config.scene.fog instanceof Fog) {
        config.scene.fog.color.copy(background);
        config.scene.fog.near = config.fog.near * look.fogNearScale;
        config.scene.fog.far = config.fog.far * look.fogFarScale;
      }

      applySky(sky, look);
      applySky(captureSky, look);
    },

    captureSource(): Object3D | null {
      /**
       * 烘焙前关掉**捕获天穹**的太阳圆盘（`showSunDisc = 0`，three 官方就是为
       * "生成环境贴图"准备的这个开关）。
       *
       * 圆盘不关的话，它作为 HDR 超亮斑被烘进环境贴图，PMREM 卷积会把整张贴的
       * 能量拉爆：玻璃幕墙这类镜面材质反射出来的就是一整面过曝的白
       * （实测写字楼正午整面泛白，幕墙细节全丢）。直射本来就归主光管，
       * 环境贴图只该装散射 —— 关圆盘正好只去掉直射那一份，天空散射原样保留。
       *
       * 注意别用"把 `sunPosition` 压到地平线下"来藏太阳：那会连**散射分布**一起
       * 改掉，烘出来的环境贴图整体变成暮色调（太阳位置的语义是"几点了"，
       * 不是"圆盘要不要"）。圆盘归 `showSunDisc` 管，散射归 `sunPosition` 管，
       * 两个旋钮各管各的。
       *
       * 另外，关掉圆盘解决的是**直射污染**；镜面材质的过曝还得靠场景把
       * `environmentIntensity` 对着真实 IBL 重标（写字楼 0.85 → 0.45）——
       * 天空散射本身的 HDR 亮度远高于兜底的 RoomEnvironment，
       * 那才是镜面泛白的大头。
       */
      if (captureSky) {
        const uniforms = (captureSky.material as ShaderMaterial).uniforms;
        uniforms.showSunDisc.value = 0;
      }
      return captureSky;
    },

    dispose(): void {
      if (sky) {
        config.scene.remove(sky);
        sky.geometry.dispose();
        (sky.material as ShaderMaterial).dispose();
      }
      if (captureSky) {
        captureSky.geometry.dispose();
        (captureSky.material as ShaderMaterial).dispose();
      }
    },
  };
}
