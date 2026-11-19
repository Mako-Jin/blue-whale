import * as THREE from 'three';

/**
 * 天空穹顶。
 *
 * <p>用 ShaderMaterial 而非顶点色球：昼夜切换只需改 uniform，不必重建几何。
 * 末尾手动 include `tonemapping_fragment` / `colorspace_fragment`——ShaderMaterial
 * 不会自动补这两段，漏掉的话天空会比场景亮一档（且随曝光设置错位）。</p>
 */

export interface SkyState {
  /** 天顶色 */
  top: THREE.Color;
  /** 地平线色 */
  horizon: THREE.Color;
  /** 地平线以下（远山/雾）色 */
  ground: THREE.Color;
  sunDir: THREE.Vector3;
  sunColor: THREE.Color;
  /** 0..1：太阳强度，夜里趋 0，太阳圆盘随之消失 */
  sunIntensity: number;
  /** 0..1：地平线雾带浓度 */
  haze: number;
  /** 0..1：云量（0 = 晴空）。缺省 0——env 占位图与旧调用方不用关心云 */
  cloud?: number;
  /** 云的受光色（时段决定：白天白、黄昏暖金、夜晚暗蓝） */
  cloudLight?: number;
  /** 云的背光/底面色 */
  cloudShade?: number;
}

const VERT = /* glsl */ `
varying vec3 vDir;
void main() {
  vDir = position;
  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`;

const FRAG = /* glsl */ `
uniform vec3 uTop;
uniform vec3 uHorizon;
uniform vec3 uGround;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform float uSunIntensity;
uniform float uHaze;
uniform float uTime;
uniform float uCloud;
uniform vec3 uCloudLight;
uniform vec3 uCloudShade;
varying vec3 vDir;

// 值噪声 + fbm：云的形状来源。哈希要确定性——同一帧内重复采样同一处
// 必须同值，否则云会碎成雪花噪点
float hash21(vec2 p) {
  p = fract(p * vec2(123.34, 345.45));
  p += dot(p, p + 34.345);
  return fract(p.x * p.y);
}
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
float fbm(vec2 p) {
  float v = 0.0;
  float amp = 0.5;
  for (int i = 0; i < 4; i++) {
    v += amp * vnoise(p);
    p = p * 2.03 + vec2(11.7, 7.3);
    amp *= 0.5;
  }
  return v;
}

void main() {
  vec3 d = normalize(vDir);
  float up = max(d.y, 0.0);

  // 地平线暖带只占低仰角约 7° 以内，再往上是天顶色。
  // 第一版用 pow(up,0.42) 过渡，相机仰角低时满屏橙色、看不到天蓝，整幅图一片暖褐。
  float hz = smoothstep(0.0, 0.12, d.y);
  vec3 col = mix(uHorizon, uTop, pow(hz, 0.5));
  col = mix(uGround, col, smoothstep(-0.18, 0.01, d.y));

  float sd = max(dot(d, normalize(uSunDir)), 0.0);
  // 日晕 + 日轮
  col += uSunColor * pow(sd, 8.0) * 0.30 * uSunIntensity;
  col += uSunColor * pow(sd, 220.0) * 2.4 * uSunIntensity;

  /*
   * 云层：方向向量投到虚拟云平面（高度归一 1.0），fbm 阈值出形状。
   *
   * <p>纯渐变天空在全景机位里上三分之一是空的。云不建几何——投影 + fbm
   * 是零三角面的云；domain warp（fbm 采样坐标再被一层 fbm 扰动）把
   * "分形雪花"感扭成积云的团絮感。</p>
   */
  if (uCloud > 0.005 && d.y > 0.012) {
    vec2 cuv = d.xz / (d.y + 0.16) * 1.1;
    vec2 drift = vec2(-0.0105, 0.0052) * uTime; // 慢速西风
    vec2 q = vec2(fbm(cuv * 0.55 + drift * 0.7), fbm(cuv * 0.55 + 5.2 - drift * 0.5));
    float f = fbm(cuv * 1.35 + q * 1.15 + drift);
    float cov = mix(0.66, 0.32, uCloud); // 云量越大阈值越低
    // 聚簇掩码：低频 fbm 决定哪些天区有云——没有它，云均匀铺满整片天，
    // 读作"一层雾"而不是"几朵云"
    float mask = smoothstep(0.30, 0.58, fbm(cuv * 0.28 + drift * 0.35 + 9.4));
    float den = smoothstep(cov, cov + 0.20, f) * mix(0.55, 1.0, mask);
    // 团絮内部的明暗：噪声当"受光细节"，**厚处（den 高）压向背光色**——
    // 云底比云顶厚、受光少，白天看到的是白顶灰底；没有这一项，
    // 亮地平线前的云全是白色，直接被雾色吃掉
    float detail = fbm(cuv * 3.2 + q * 1.4 - drift * 1.9);
    float lit = clamp(detail * 1.15 + 0.05 - den * 0.62, 0.0, 1.0);
    vec3 cloudCol = mix(uCloudShade, uCloudLight, lit);
    // 贴地平线淡出：低仰角处云被雾吃掉，硬边会穿帮
    float hf = smoothstep(0.008, 0.07, d.y);
    col = mix(col, cloudCol, den * hf);
  }

  // 地平线附近的暖雾带（黄昏时最明显）
  float band = exp(-abs(d.y) * 9.0) * uHaze;
  col += uHorizon * band * 0.32;

  gl_FragColor = vec4(col, 1.0);
  #include <tonemapping_fragment>
  #include <colorspace_fragment>
}
`;

export interface Sky {
  mesh: THREE.Mesh;
  update: (state: SkyState) => void;
  /** 每帧推进云的漂移（秒）。env 里的天空快照不需要调 */
  tick: (elapsed: number) => void;
}

export function createSky(radius = 900): Sky {
  const uniforms = {
    uTop: { value: new THREE.Color(0x2a5b8f) },
    uHorizon: { value: new THREE.Color(0xf0a860) },
    uGround: { value: new THREE.Color(0x2b2f33) },
    uSunDir: { value: new THREE.Vector3(0, 0.3, -1).normalize() },
    uSunColor: { value: new THREE.Color(0xffd9a0) },
    uSunIntensity: { value: 1 },
    uHaze: { value: 0.6 },
    uTime: { value: 0 },
    uCloud: { value: 0 },
    uCloudLight: { value: new THREE.Color(0xffffff) },
    uCloudShade: { value: new THREE.Color(0x9aa4b0) },
  };

  const mat = new THREE.ShaderMaterial({
    uniforms,
    vertexShader: VERT,
    fragmentShader: FRAG,
    side: THREE.BackSide,
    depthWrite: false,
    fog: false,
  });

  const mesh = new THREE.Mesh(new THREE.SphereGeometry(radius, 48, 32), mat);
  mesh.name = 'sky';
  mesh.renderOrder = -10;
  mesh.frustumCulled = false;

  return {
    mesh,
    update(state) {
      uniforms.uTop.value.copy(state.top);
      uniforms.uHorizon.value.copy(state.horizon);
      uniforms.uGround.value.copy(state.ground);
      uniforms.uSunDir.value.copy(state.sunDir);
      uniforms.uSunColor.value.copy(state.sunColor);
      uniforms.uSunIntensity.value = state.sunIntensity;
      uniforms.uHaze.value = state.haze;
      uniforms.uCloud.value = state.cloud ?? 0;
      uniforms.uCloudLight.value.set(state.cloudLight ?? 0xffffff);
      uniforms.uCloudShade.value.set(state.cloudShade ?? 0x9aa4b0);
    },
    tick(elapsed) {
      uniforms.uTime.value = elapsed;
    },
  };
}
