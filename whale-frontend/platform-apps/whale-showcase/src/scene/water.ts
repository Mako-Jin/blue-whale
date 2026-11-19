import * as THREE from 'three';
import { riverCenter, WATER_LEVEL } from './terrain';

/**
 * 河面。
 *
 * <p>几何是一条跟随 {@link riverCenter} 摆动的条带，宽度略大于河槽——多出来的部分被
 * 两岸地形挡住，不会看到生硬的直边。水面本身 <b>不受 FogExp2 影响</b>（自定义材质），
 * 所以雾要自己算：不接的话远处河面会从雾里"跳"出来，一眼假。</p>
 *
 * <p>着色关键：水面"像不像水"取决于<b>反射</b>，不是颜色深浅。这里踩过两次坑：</p>
 * <ol>
 *   <li>第一版只做 mix(深色, 浅色)，出来是一条水泥带；</li>
 *   <li>第二版加了正弦波扰动，三条相近频率的正弦在平面上拍出<b>规则编织纹</b>，
 *       配合黄昏的暖色反光，整条河看着像铺了木地板。</li>
 * </ol>
 * <p>现在用<b>二维值噪声</b>叠三层做波纹（不存在周期条纹），反射色直接复用天空
 * shader 的同一套映射，再补菲涅尔与太阳碎金——水面才有"活"的感觉。</p>
 */

const VERT = /* glsl */ `
varying vec3 vWorld;
void main() {
  vec4 wp = modelMatrix * vec4(position, 1.0);
  vWorld = wp.xyz;
  gl_Position = projectionMatrix * viewMatrix * wp;
}
`;

const FRAG = /* glsl */ `
uniform float uTime;
uniform vec3 uDeep;
uniform vec3 uShallow;
uniform vec3 uTop;
uniform vec3 uHorizon;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform float uSunIntensity;
uniform vec3 uFogColor;
uniform float uFogDensity;
uniform samplerCube uEnv;
uniform float uEnvWeight;
uniform vec3 uLampPos[4];
uniform float uLampPower[4];
uniform int uLampCount;
varying vec3 vWorld;

/**
 * 灯笼在水面的倒影光带。
 *
 * <p>为什么自己做而不是靠反射探针：灯笼在水面上方 2.7m，它在水下的镜像是 -2.7m，
 * 从相机射向镜像点的射线是<b>向下</b>的；而水面反射向量 R 在斜视时是<b>向上</b>的
 * （掠射角下 R 几乎水平朝远端）。两者根本不是同一个方向——探针里灯在 cube 的下半
 * 球，水面永远采不到。这是平面反射（Reflector）才能解决的事，而 Reflector 要每帧
 * 把 58 万面的场景再渲一遍，帧率会掉到 20 以下。</p>
 *
 * <p>所以按几何直接算：灯的镜像点与相机连线交水面于一点，那里就是观众看到的倒影
 * 位置。在这个点上做一个<b>沿视线方向拉长的椭圆光斑</b>——真实水面倒影正是这个形状：
 * 横向只有几十厘米宽，纵向顺着波纹拖出好几米。</p>
 */
vec3 lampGlow(vec2 p, vec2 wob) {
  vec3 acc = vec3(0.0);
  for (int i = 0; i < 4; i++) {
    if (i >= uLampCount) break;
    vec3 lp = uLampPos[i];
    vec3 mirror = vec3(lp.x, -lp.y, lp.z);
    vec3 dir = mirror - cameraPosition;
    if (dir.y >= -0.001) continue;
    float t = -cameraPosition.y / dir.y;
    vec2 hit = cameraPosition.xz + dir.xz * t;
    vec2 axisV = normalize(cameraPosition.xz - hit + vec2(1e-4));
    vec2 rel = (p + wob) - hit;
    float along = dot(rel, axisV);
    float across = dot(rel, vec2(-axisV.y, axisV.x));
    // 横向只有 40cm 宽（水面倒影是窄带，不是一片雾），纵向按 |along|^1.6 拖出光尾
    float g = exp(-across * across / 0.16) * exp(-pow(abs(along) / 3.0, 1.6));
    /*
     * 横纹调制：真实水面上的灯影不是一条均匀的光带，而是被水波<b>切成一道道横纹</b>，
     * 而且这些横纹还在缓缓流动。少了这一层，画出来就是一片"月光洒水面"的雾。
     */
    float ripple = 0.5 + 0.5 * sin(along * 3.4 - uTime * 2.1 + across * 1.7);
    acc += vec3(1.0, 0.55, 0.22) * g * (0.30 + 0.95 * ripple) * uLampPower[i];
  }
  return acc;
}

float hash21(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

// 值噪声（双线性 + 三次平滑）。用它而不是正弦，正弦叠加必然出现周期条纹
float vnoise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  float a = hash21(i);
  float b = hash21(i + vec2(1.0, 0.0));
  float c = hash21(i + vec2(0.0, 1.0));
  float d = hash21(i + vec2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

/**
 * 波纹高度场。q 把 x 压缩、z 拉长：涟漪沿河向（z）拉成流线，横向细碎，
 * 这样反射出来的高光是"一条条竖向碎金"而不是满屏方格。
 */
float surface(vec2 p, float t) {
  vec2 q = vec2(p.x * 1.10, p.y * 0.30);
  float n = vnoise(q * 1.20 + vec2(t * 0.10, t * 0.34));
  n += vnoise(q * 2.70 - vec2(t * 0.16, -t * 0.52)) * 0.52;
  n += vnoise(q * 6.10 + vec2(-t * 0.28, t * 0.74)) * 0.26;
  return n / 1.78;
}

/** 与 sky.ts 完全一致的映射：地平线暖带只吃低仰角，其余交给天顶色 */
vec3 skyAt(vec3 dir) {
  float hz = smoothstep(0.0, 0.12, max(dir.y, 0.0));
  return mix(uHorizon, uTop, pow(hz, 0.5));
}

/*
 * 河床剖面（与 terrain.ts 逐项对齐的解析版）。
 *
 * <p>河床不加噪声（岸上才加，河槽必须平），所以这段剖面在 shader 里可以
 * 精确复现——浅水色就是用它算的：深度 = 0 - 床高，贴岸趋 0、河心 2.4m。</p>
 *
 * <p>S26 起含远段弯河项，与 TS 版 riverCenter 逐项一致（GLSL smoothstep 要求
 * edge0&lt;edge1，用 1-smoothstep 反向等效）。改任一侧必须同步另一侧，
 * 否则远段浅水带与实际水线错位。</p>
 */
float riverCenterF(float z) {
  return (
    sin(z * 0.013) * 6.5 +
    sin(z * 0.0047 + 1.3) * 4.2 +
    sin(z * 0.031 + 1.05) * 9.0 * (1.0 - smoothstep(-150.0, -70.0, z))
  );
}

float bedHeight(vec2 p) {
  float d = abs(p.x - riverCenterF(p.y));
  return -2.4 + 2.75 * smoothstep(4.5, 9.0, d);
}

void main() {
  vec2 p = vWorld.xz;
  float dist = length(cameraPosition - vWorld);

  // 远处必须衰减法线扰动。不衰减的话每个像素都在两个噪声格子之间跳，
  // 远水面会变成一片闪烁的白噪点（白天实测像"碎冰/大理石"）。
  //
  // 线性项 0.045 不够：150m 外还剩 0.13，而碎金是 pow(nh,420)——法线抖 0.13 就够
  // 让整条远河跳成白点。这里加平方项，150m 处压到 0.03，远水面恢复成一片安静的反光。
  float atten = 1.0 / (1.0 + dist * 0.09 + dist * dist * 0.0008);

  float e = 0.42;
  float dhdx = (surface(p + vec2(e, 0.0), uTime) - surface(p - vec2(e, 0.0), uTime)) / (2.0 * e);
  float dhdz = (surface(p + vec2(0.0, e), uTime) - surface(p - vec2(0.0, e), uTime)) / (2.0 * e);

  /*
   * 三档法线，各管各的：
   *  N  全强度（1.55×）——太阳碎金与灯影摇曳，波纹的高频特征全靠它；
   *  Nr 六成强度——反射光线采样，防云纹被搅成油花；
   *  Ns 两成五强度——菲涅尔。菲涅尔对法线极敏感，全强度时它把噪声高度场的
   *     等高线直接画在水面上一条条蜿蜒细脉，就是"大理石纹蓝油漆"的来源。
   *     真实近景水面的反光是宽而柔和的明暗坡，不是等值线图。
   */
  vec3 Ns = normalize(vec3(-dhdx * 0.40 * atten, 1.0, -dhdz * 0.40 * atten));

  vec3 V = normalize(cameraPosition - vWorld);

  /*
   * 反射光线用"抹平"的法线（全强度法线的六成）。
   *
   * <p>S20 把云加进天空后，env 探针（同一套天空）把云也烘了进去——反射光线被波纹
   * 全强度搅动时，锐利的云纹在水面扭成满屏涡旋，码头机位实测像"大理石纹蓝油漆"。
   * 真实波面对高空云的反射是大幅柔和的明暗斑：粗糙表面平均了很大一片天空方向。</p>
   */
  vec3 Nr = normalize(vec3(-dhdx * 0.95 * atten, 1.0, -dhdz * 0.95 * atten));
  vec3 R = reflect(-V, Nr);
  vec3 N = normalize(vec3(-dhdx * 1.55 * atten, 1.0, -dhdz * 1.55 * atten));

  /*
   * 反射 = 解析天空 + 环境探针。
   *
   * <p>只用解析天空时，夜里天空近黑，整条河就是一块黑布——岸上二十盏灯笼在水面
   * 上一点影子都没有，"灯影摇红"这个最基本的夜景信息直接缺失。探针（CubeCamera
   * 烘一次场景）把岸上的灯、牌坊、城墙带进反射里。</p>
   *
   * <p>探针固定在河心一点，远处用它会露出视差错误（几百米外的水面反射着近处的灯），
   * 所以按距离淡回解析天空：50m 内以探针为主，100m 外几乎全是天空。</p>
   */
  float ew = uEnvWeight / (1.0 + dist * 0.022);
  /*
   * mip 偏移 3：PMREM 的高层 mip 就是预模糊的天空——水面是粗糙反射体，不是镜子。
   * 碎金与灯影用的是解析高光/光带，不受此影响；被抹掉的是"锐利云纹"这种
   * 本来就不该出现在波纹水面上的东西。
   */
  vec3 envR = textureCube(uEnv, R, 3.0).rgb;
  vec3 refl = mix(skyAt(R), envR, ew);

  // 菲涅尔：正视也保留一点反射（0.05 起步），否则俯视时是一块死水泥；
  // 但上限压到 0.82——满反射会让水面变成一面金色镜子，把整条河的形体吃掉
  // 用抹平法线 Ns：细节法线在这里会把噪声等高线画成大理石细脉
  float fres = pow(1.0 - clamp(dot(Ns, V), 0.0, 1.0), 4.0);
  fres = clamp(0.05 + fres * 0.9, 0.0, 0.82);

  // 反射色本身要压暗并去饱和：水体吸收掉了大部分能量，直接拿天空色当水面会过曝
  refl *= 0.72;
  refl = mix(vec3(dot(refl, vec3(0.2126, 0.7152, 0.0722))), refl, 0.85);

  vec3 col = mix(uDeep, refl, fres);

  /*
   * 浅水色：贴岸处河床透出——水体色向"沙石绿"靠、反射权重也压一截
   * （浅水的镜面感本来就弱）。深度带约 4m 宽（床高 0 在距河心 ~8.4m 处），
   * 是水线与岸之间的自然过渡，没有它近岸就是一条"深蓝硬边"。
   */
  float depth = clamp(-bedHeight(p), 0.0, 2.4);
  float shal = 1.0 - smoothstep(0.10, 1.35, depth);
  col = mix(mix(uDeep, uShallow, shal * 0.9), refl, fres * (1.0 - shal * 0.45));
  // 水线亮边：深度趋 0 处一层极窄的提亮——水膜贴在湿沙上的高光。
  // 按距离衰减：近岸（码头/河岸机位 3~10m）保持湿润反光；远景里一条均匀
  // 白描边贴着河道通到地平线，是"护渠"感的来源之一，100m 外只剩一成
  col += uHorizon * (1.0 - smoothstep(0.0, 0.10, depth)) * 0.18 / (1.0 + dist * 0.05);

  // 太阳：一层宽反光打底 + 一层高次碎金。只留高次的话低太阳角下什么都没有
  vec3 L = normalize(uSunDir);
  vec3 H = normalize(V + L);
  float nh = max(dot(N, H), 0.0);
  /*
   * 月光路（S27）：夜晚碎金 lobe 放宽 + 宽光带增强。pow 420 是日面碎金的
   * 宽度——月光路的观感是"宽而柔的一条光带"（波面把极窄镜像抹开），420
   * 在夜里几乎画不出路径。夜间（uSunIntensity<1.0，蓝调与白天/黄昏的太阳
   * 不受影响）放宽到 140、宽反光加倍；配合 night 预设月亮方位转到画面内
   * （azim 205），夜景水面才有"月下河"的信息。
   */
  float nightK = uSunIntensity < 1.0 ? 1.0 : 0.0;
  // 宽反光系数必须很小：这个项是"整片水面都被太阳照亮"，白天给到 0.12 时
  // 整条河变成一片暖米色，看着像沙丘而不是水（白天全景实测）。夜里**不加倍**
  // ——月亮方位与全景视线对齐后 0.10 的宽反光把整条河刷成银白，抢灯笼的戏
  col += uSunColor * pow(nh, 26.0) * 0.045 * uSunIntensity;
  col += uSunColor * pow(nh, mix(420.0, 320.0, nightK)) * mix(3.2, 2.3, nightK) * uSunIntensity * atten;

  // 灯影：越接近水面视角越明显（俯视时看不到倒影，这是菲涅尔决定的）
  col += lampGlow(p, N.xz * 0.85) * (0.3 + fres * 1.1);

  // 指数平方雾，与场景 FogExp2 同参数（自定义材质不吃 scene.fog，必须自己算）
  float fogFactor = 1.0 - exp(-uFogDensity * uFogDensity * dist * dist);
  col = mix(col, uFogColor, clamp(fogFactor, 0.0, 1.0));

  gl_FragColor = vec4(col, 1.0);
  #include <tonemapping_fragment>
  #include <colorspace_fragment>
}
`;

export interface Water {
  mesh: THREE.Mesh;
  update: (
    time: number,
    sunDir: THREE.Vector3,
    sunIntensity: number,
    fogColor: THREE.Color,
    fogDensity: number,
  ) => void;
  /** 反射用到的两种天空色：地平线（暖）与天顶（冷） */
  setSky: (horizon: THREE.Color, top: THREE.Color) => void;
  /**
   * 水体本色（非反射部分）。
   *
   * <p>俯视机位下水面几乎全是"水体色"：视线与水面夹角约 25°，菲涅尔只有 0.15，
   * 85% 的像素是这一项。所以它不能是一个固定值——黄昏的深墨绿放到正午就是
   * 一条黑河。白天必须抬亮，夜里再压回去。</p>
   */
  setDeep: (deep: THREE.Color) => void;
  /** 浅水色（贴岸河床透出），按时段给 */
  setShallow: (shallow: THREE.Color) => void;
  /** 场景环境探针（CubeCamera 烘出的立方图），用于把岸上的灯与建筑带进水面反射 */
  setEnv: (tex: THREE.CubeTexture | THREE.Texture) => void;
  /** 水面上要画倒影的灯（最多 4 盏）。power 0..1，按时段灯亮程度给 */
  setLamps: (positions: THREE.Vector3[], powers: number[]) => void;
}

export function createWater(length = 480, halfWidth = 12): Water {
  const geo = new THREE.PlaneGeometry(halfWidth * 2, length, 8, 220);
  geo.rotateX(-Math.PI / 2);

  // 顶点沿河道中心线平移：水面必须和地形河槽走同一条曲线，否则宽窄对不上会露出干河床
  const pos = geo.attributes.position as THREE.BufferAttribute;
  for (let i = 0; i < pos.count; i += 1) {
    const z = pos.getZ(i);
    pos.setX(i, pos.getX(i) + riverCenter(z));
    pos.setY(i, WATER_LEVEL);
  }
  pos.needsUpdate = true;
  geo.computeVertexNormals();

  const uniforms = {
    uTime: { value: 0 },
    uDeep: { value: new THREE.Color(0x0d2126) },
    uShallow: { value: new THREE.Color(0x35443a) },
    uTop: { value: new THREE.Color(0x22386a) },
    uHorizon: { value: new THREE.Color(0xd98f52) },
    uSunDir: { value: new THREE.Vector3(0, 0.3, -1).normalize() },
    uSunColor: { value: new THREE.Color(0xffd9a0) },
    uSunIntensity: { value: 1 },
    uFogColor: { value: new THREE.Color(0xa8836a) },
    uFogDensity: { value: 0.0022 },
    uEnv: { value: null as THREE.Texture | null },
    uEnvWeight: { value: 0 },
    uLampPos: { value: [new THREE.Vector3(), new THREE.Vector3(), new THREE.Vector3(), new THREE.Vector3()] },
    uLampPower: { value: [0, 0, 0, 0] },
    uLampCount: { value: 0 },
  };

  const mat = new THREE.ShaderMaterial({
    uniforms,
    vertexShader: VERT,
    fragmentShader: FRAG,
    // 不透明：半透明水面要参与排序，和岸线一交叠就出现"水盖住岸"的硬边
    transparent: false,
    depthWrite: true,
  });

  const mesh = new THREE.Mesh(geo, mat);
  mesh.name = 'water';
  mesh.receiveShadow = false;

  return {
    mesh,
    update(time, sunDir, sunIntensity, fogColor, fogDensity) {
      uniforms.uTime.value = time;
      uniforms.uSunDir.value.copy(sunDir);
      uniforms.uSunIntensity.value = sunIntensity;
      uniforms.uFogColor.value.copy(fogColor);
      uniforms.uFogDensity.value = fogDensity;
    },
    setSky(horizon, top) {
      uniforms.uHorizon.value.copy(horizon);
      uniforms.uTop.value.copy(top);
    },
    setDeep(deep) {
      uniforms.uDeep.value.copy(deep);
    },
    /** 浅水处河床透出的水体色（贴岸渐变用），按时段与 deep 一起给 */
    setShallow(shallow: THREE.Color) {
      uniforms.uShallow.value.copy(shallow);
    },
    setEnv(tex) {
      uniforms.uEnv.value = tex;
      uniforms.uEnvWeight.value = 1;
    },
    setLamps(positions, powers) {
      const n = Math.min(4, positions.length);
      for (let i = 0; i < n; i += 1) uniforms.uLampPos.value[i].copy(positions[i]);
      for (let i = 0; i < 4; i += 1) uniforms.uLampPower.value[i] = i < n ? powers[i] ?? 0 : 0;
      uniforms.uLampCount.value = n;
    },
  };
}
