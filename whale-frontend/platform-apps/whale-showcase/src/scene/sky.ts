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
varying vec3 vDir;

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
    },
  };
}
