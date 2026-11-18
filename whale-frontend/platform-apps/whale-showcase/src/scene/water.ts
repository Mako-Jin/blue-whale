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
uniform vec3 uTop;
uniform vec3 uHorizon;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform float uSunIntensity;
uniform vec3 uFogColor;
uniform float uFogDensity;
varying vec3 vWorld;

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

  vec3 N = normalize(vec3(-dhdx * 1.55 * atten, 1.0, -dhdz * 1.55 * atten));
  vec3 V = normalize(cameraPosition - vWorld);

  vec3 R = reflect(-V, N);
  vec3 refl = skyAt(R);

  // 菲涅尔：正视也保留一点反射（0.05 起步），否则俯视时是一块死水泥；
  // 但上限压到 0.82——满反射会让水面变成一面金色镜子，把整条河的形体吃掉
  float fres = pow(1.0 - clamp(dot(N, V), 0.0, 1.0), 4.0);
  fres = clamp(0.05 + fres * 0.9, 0.0, 0.82);

  // 反射色本身要压暗并去饱和：水体吸收掉了大部分能量，直接拿天空色当水面会过曝
  refl *= 0.72;
  refl = mix(vec3(dot(refl, vec3(0.2126, 0.7152, 0.0722))), refl, 0.85);

  vec3 col = mix(uDeep, refl, fres);

  // 太阳：一层宽反光打底 + 一层高次碎金。只留高次的话低太阳角下什么都没有
  vec3 L = normalize(uSunDir);
  vec3 H = normalize(V + L);
  float nh = max(dot(N, H), 0.0);
  // 宽反光系数必须很小：这个项是"整片水面都被太阳照亮"，给到 0.12 时白天整条河
  // 变成一片暖米色，看着像沙丘而不是水（白天全景实测）。碎金才是水面该有的东西。
  col += uSunColor * pow(nh, 26.0) * 0.045 * uSunIntensity;
  col += uSunColor * pow(nh, 420.0) * 3.2 * uSunIntensity * atten;

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
    uTop: { value: new THREE.Color(0x22386a) },
    uHorizon: { value: new THREE.Color(0xd98f52) },
    uSunDir: { value: new THREE.Vector3(0, 0.3, -1).normalize() },
    uSunColor: { value: new THREE.Color(0xffd9a0) },
    uSunIntensity: { value: 1 },
    uFogColor: { value: new THREE.Color(0xa8836a) },
    uFogDensity: { value: 0.0022 },
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
  };
}
