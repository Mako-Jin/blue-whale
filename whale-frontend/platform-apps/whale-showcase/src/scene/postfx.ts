import * as THREE from 'three';
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js';
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js';
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js';
import { SMAAPass } from 'three/examples/jsm/postprocessing/SMAAPass.js';
import { ShaderPass } from 'three/examples/jsm/postprocessing/ShaderPass.js';
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js';

/**
 * 后期处理链。
 *
 * <p>顺序是<b>有讲究</b>的，写错任意一环都会得到"过曝/发灰/双次色调映射"：</p>
 * <pre>
 *   RenderPass      →  线性 HDR 半浮点缓冲（此处绝不色调映射）
 *   UnrealBloomPass →  在线性空间提亮超阈值部分（灯笼、日轮、水面碎金）
 *   OutputPass      →  统一做 ACES 色调映射 + sRGB 编码（只做一次）
 *   GradePass       →  在显示空间做暗部染色 / 对比 / 暗角 / 颗粒
 *   SMAAPass        →  最后抗锯齿（放在调色之后，否则锐化过的边缘再被磨一遍）
 * </pre>
 *
 * <p>关键事实：three 的 `toneMapping` <b>只在渲染到画布时</b>生效
 * （`WebGLRenderer` 内部的 `currentRenderTarget === null` 判断），渲染到 RenderTarget 时
 * 材质里的 `tonemapping_fragment` 是空操作。所以整条链里只有 OutputPass 会色调映射，
 * 不会出现双重映射。这也意味着自定义 ShaderMaterial 里照常写 `#include &lt;tonemapping_fragment&gt;`
 * 是安全的。</p>
 */

const GradeShader = {
  uniforms: {
    tDiffuse: { value: null as THREE.Texture | null },
    uTime: { value: 0 },
    uAspect: { value: 1 },
    uVignette: { value: 0.9 },
    uGrain: { value: 0.028 },
    uSat: { value: 1.06 },
    uContrast: { value: 1.05 },
    /** 暗部染色：黄昏偏冷紫，夜里偏蓝 */
    uShadowTint: { value: new THREE.Color(0x1a2440) },
    uShadowAmount: { value: 0.22 },
    /** 高光染色：暖金 */
    uHighTint: { value: new THREE.Color(0xffd2a0) },
    uHighAmount: { value: 0.1 },
  },
  vertexShader: /* glsl */ `
varying vec2 vUv;
void main() {
  vUv = uv;
  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}
`,
  fragmentShader: /* glsl */ `
uniform sampler2D tDiffuse;
uniform float uTime;
uniform float uAspect;
uniform float uVignette;
uniform float uGrain;
uniform float uSat;
uniform float uContrast;
uniform vec3 uShadowTint;
uniform float uShadowAmount;
uniform vec3 uHighTint;
uniform float uHighAmount;
varying vec2 vUv;

float hash(vec2 p) {
  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
  vec3 col = texture2D(tDiffuse, vUv).rgb;

  float lum = dot(col, vec3(0.2126, 0.7152, 0.0722));

  // 分离色调：暗部往冷里压、高光往暖里提，比整体加滤镜更有"胶片感"
  col = mix(col, col * uShadowTint * 2.4, (1.0 - smoothstep(0.0, 0.42, lum)) * uShadowAmount);
  col = mix(col, col * uHighTint * 1.15, smoothstep(0.55, 1.0, lum) * uHighAmount);

  // 对比（围绕中灰 0.5）与饱和度
  col = (col - 0.5) * uContrast + 0.5;
  float g = dot(col, vec3(0.2126, 0.7152, 0.0722));
  col = mix(vec3(g), col, uSat);

  // 暗角：椭圆，按宽高比修正，否则宽屏两侧会先黑
  vec2 d = (vUv - 0.5) * vec2(uAspect, 1.0);
  float vig = 1.0 - smoothstep(0.42, 0.95, length(d) * 1.32) * uVignette;
  col *= vig;

  // 颗粒：静态噪点会被看成"脏"，必须随时间抖动
  float n = hash(vUv * vec2(1920.0, 1080.0) + fract(uTime) * 137.0) - 0.5;
  col += n * uGrain * (0.35 + 0.65 * (1.0 - lum));

  gl_FragColor = vec4(max(col, 0.0), 1.0);
}
`,
};

export interface PostFx {
  render: (dt: number) => void;
  setSize: (w: number, h: number) => void;
  /** 0 关闭；黄昏 0.32 左右、夜里 0.7 才有灯火溢光 */
  setBloom: (strength: number) => void;
  /** 改暗部染色（不同时段风味不同） */
  setTint: (shadow: THREE.Color, high: THREE.Color, shadowAmount: number) => void;
  dispose: () => void;
}

export function createPostFx(
  renderer: THREE.WebGLRenderer,
  scene: THREE.Scene,
  camera: THREE.Camera,
  width: number,
  height: number,
): PostFx {
  const composer = new EffectComposer(renderer);
  composer.setSize(width, height);

  composer.addPass(new RenderPass(scene, camera));

  const bloom = new UnrealBloomPass(new THREE.Vector2(width, height), 0.34, 0.72, 0.92);
  composer.addPass(bloom);

  composer.addPass(new OutputPass());

  const grade = new ShaderPass(GradeShader);
  grade.uniforms.uAspect.value = width / Math.max(height, 1);
  composer.addPass(grade);

  const smaa = new SMAAPass(width, height);
  composer.addPass(smaa);

  return {
    render(dt) {
      grade.uniforms.uTime.value += dt;
      composer.render(dt);
    },
    setSize(w, h) {
      composer.setSize(w, h);
      bloom.setSize(w, h);
      grade.uniforms.uAspect.value = w / Math.max(h, 1);
    },
    setBloom(strength) {
      bloom.strength = strength;
    },
    setTint(shadow, high, shadowAmount) {
      (grade.uniforms.uShadowTint.value as THREE.Color).copy(shadow);
      (grade.uniforms.uHighTint.value as THREE.Color).copy(high);
      grade.uniforms.uShadowAmount.value = shadowAmount;
    },
    dispose() {
      composer.dispose();
      smaa.dispose?.();
    },
  };
}
