import * as THREE from 'three';
import { createSky, type SkyState } from './sky';

/**
 * 环境贴图（IBL）。
 *
 * <p>没有环境贴图的 `MeshStandardMaterial` 只能靠半球光近似漫射，物体表面<b>不存在
 * 任何反射信息</b>——石头、木、瓦、金属全是一条平色，怎么调光都是"塑料感"。这也是
 * 参考文章里质感好的主因之一（他们用 HDRI）。</p>
 *
 * <p>我们不需要外部 HDRI：把同一套天空 shader 渲进一个立方体再经 PMREM 预卷积，
 * 就得到与当前时段<b>严格一致</b>的环境光——黄昏时环境是暖橙配冷蓝天顶，切到夜晚
 * 自动变深蓝，不必额外准备四套 HDR。</p>
 *
 * <p>注意 `fromScene` 内部会把 `renderer.toneMapping` 临时置为 `NoToneMapping`，
 * 所以拿到的辐照度是线性的（这正是我们想要的），不要在外面再乘曝光。</p>
 */
export interface SceneEnv {
  texture: THREE.Texture;
  /** 重建并返回新贴图：调用方必须把返回值重新赋给 `scene.environment`（旧图已释放） */
  update: (state: SkyState) => THREE.Texture;
  dispose: () => void;
}

export function createSceneEnv(renderer: THREE.WebGLRenderer): SceneEnv {
  const pmrem = new THREE.PMREMGenerator(renderer);
  pmrem.compileCubemapShader();

  // 半径 50：必须落在 fromScene 的 near/far 之内，否则被裁剪成黑球
  const envSky = createSky(50);
  const envScene = new THREE.Scene();
  envScene.add(envSky.mesh);

  let target: THREE.WebGLRenderTarget | null = null;

  function rebuild(state: SkyState): THREE.Texture {
    envSky.update(state);
    const next = pmrem.fromScene(envScene, 0.035, 1, 220);
    target?.dispose();
    target = next;
    return next.texture;
  }

  // 先出一张占位，避免 scene.environment 为 null 时首帧材质是黑的
  const first = rebuild({
    top: new THREE.Color(0x22386a),
    horizon: new THREE.Color(0xd98f52),
    ground: new THREE.Color(0x2e2b26),
    sunDir: new THREE.Vector3(0, 0.3, -1).normalize(),
    sunColor: new THREE.Color(0xffb066),
    sunIntensity: 1,
    haze: 0.55,
  });

  return {
    texture: first,
    update(state) {
      return rebuild(state);
    },
    dispose() {
      target?.dispose();
      pmrem.dispose();
      envSky.mesh.geometry.dispose();
      (envSky.mesh.material as THREE.Material).dispose();
    },
  };
}
