/* ==========================================================================
 * 参数化人物（捏脸）—— 玩家 / NPC / 其他玩家共用
 *
 * 设计取舍：本项目暂不接入「照片 → 三维重建」的重资产模型（无 GPU 环境成本与
 * 质量都不可控），改为**程序化参数化角色**：体型 / 脸型 / 发式 / 胡须 / 配饰
 * 全部是参数，几何体按参数实时生成，风格统一、零下载、可无损重生成。
 * 照片只做「主色迁移」（见 profile.ts），真正的三维重建作为可插拔后端能力：
 * 将来接入 Tripo / 混元 3D 时，只要把 GLB 挂到 group 上替换 buildBody 即可。
 *
 * 约定：角色局部 +z 为正面（world 中 yaw 与 avatar.rotation.y 一致）。
 * ========================================================================== */

import * as THREE from 'three';

export type HairStyle = 0 | 1 | 2;
export type Accessory = 0 | 1 | 2;

export interface AvatarParams {
  name: string;
  /** 身高 0.9–1.15 */
  height: number;
  /** 体型 0.85–1.18 */
  build: number;
  /** 脸宽 0.9–1.12 */
  faceWidth: number;
  skin: string;
  robe: string;
  hair: string;
  hairStyle: HairStyle;
  beard: boolean;
  accessory: Accessory;
}

export const SKIN_TONES = ['#f2d7b8', '#e8c4a0', '#d9ac86', '#c08e63', '#9c6b45'];
export const ROBE_COLORS = [
  '#2b4a63',
  '#8f3a2c',
  '#3f6b57',
  '#5a4a7a',
  '#7a5a2e',
  '#7d8590',
  '#a3435a',
  '#2f5f78',
];
export const HAIR_COLORS = ['#15110f', '#241d19', '#41332a', '#6a5540'];

export const DEFAULT_AVATAR: AvatarParams = {
  name: '无名氏',
  height: 1,
  build: 1,
  faceWidth: 1,
  skin: SKIN_TONES[1],
  robe: ROBE_COLORS[0],
  hair: HAIR_COLORS[0],
  hairStyle: 0,
  beard: false,
  accessory: 1,
};

export interface AvatarHandle {
  group: THREE.Group;
  /** 每帧调用：dt 秒，speed 为当前移动速度（0 即站立） */
  update: (dt: number, speed: number) => void;
  setParams: (next: AvatarParams) => void;
  dispose: () => void;
}

const clamp01 = (v: number) => Math.min(1, Math.max(0, v));

function mat(color: string, roughness = 0.85, metalness = 0.03): THREE.MeshStandardMaterial {
  return new THREE.MeshStandardMaterial({ color, roughness, metalness });
}

/** 同色系明度偏移，用于衣袍暗部（裙摆 / 袖口） */
export function shade(color: string, dl: number): string {
  const c = new THREE.Color(color);
  const hsl = { h: 0, s: 0, l: 0 };
  c.getHSL(hsl);
  c.setHSL(hsl.h, clamp01(hsl.s + dl * 0.12), clamp01(hsl.l + dl));
  return `#${c.getHexString()}`;
}

function disposeTree(root: THREE.Object3D) {
  root.traverse((obj) => {
    const mesh = obj as THREE.Mesh;
    if (mesh.geometry) {
      mesh.geometry.dispose();
    }
    const material = mesh.material;
    const list = Array.isArray(material) ? material : material ? [material] : [];
    for (const m of list) {
      m.dispose();
    }
  });
}

interface Limbs {
  legL: THREE.Object3D;
  legR: THREE.Object3D;
  armL: THREE.Object3D;
  armR: THREE.Object3D;
  head: THREE.Object3D;
  torso: THREE.Object3D;
}

interface Built {
  root: THREE.Group;
  limbs: Limbs;
}

/** 生成一个人物：所有几何按参数现算，重建成本约 1ms */
function buildBody(p: AvatarParams): Built {
  const B = p.build;
  const F = p.faceWidth;
  const root = new THREE.Group();

  const robeMat = mat(p.robe, 0.84);
  const robeDark = mat(shade(p.robe, -0.1), 0.88);
  const skinMat = mat(p.skin, 0.7);
  const hairMat = mat(p.hair, 0.92);
  const goldMat = mat('#c9a13a', 0.4, 0.6);
  const bootMat = mat('#26211c', 0.92);
  const darkMat = mat('#201814', 0.9);

  /* ---- 腿 ---- */
  const legGeo = new THREE.CapsuleGeometry(0.145, 0.48, 4, 10);
  const bootGeo = new THREE.BoxGeometry(0.24, 0.12, 0.32);

  const legL = new THREE.Group();
  legL.position.set(-0.19 * B, 0.88, 0);
  const legMeshL = new THREE.Mesh(legGeo, robeDark);
  legMeshL.position.y = -0.42;
  legMeshL.castShadow = true;
  legL.add(legMeshL);
  const bootL = new THREE.Mesh(bootGeo, bootMat);
  bootL.position.set(0, -0.8, 0.05);
  legL.add(bootL);

  const legR = new THREE.Group();
  legR.position.set(0.19 * B, 0.88, 0);
  const legMeshR = new THREE.Mesh(legGeo, robeDark);
  legMeshR.position.y = -0.42;
  legMeshR.castShadow = true;
  legR.add(legMeshR);
  const bootR = new THREE.Mesh(bootGeo, bootMat);
  bootR.position.set(0, -0.8, 0.05);
  legR.add(bootR);
  root.add(legL, legR);

  /* ---- 下裳（裙摆） ---- */
  const skirt = new THREE.Mesh(
    new THREE.CylinderGeometry(0.42 * B, 0.62 * B, 0.94, 16, 1, true),
    robeMat,
  );
  skirt.position.y = 1.02;
  skirt.castShadow = true;
  root.add(skirt);

  // 下摆压边
  const hem = new THREE.Mesh(new THREE.TorusGeometry(0.6 * B, 0.04, 6, 20), goldMat);
  hem.rotation.x = Math.PI / 2;
  hem.position.y = 0.56;
  root.add(hem);

  /* ---- 腰带 + 佩饰 ---- */
  const belt = new THREE.Mesh(new THREE.CylinderGeometry(0.4 * B, 0.4 * B, 0.14, 16), goldMat);
  belt.position.y = 1.44;
  root.add(belt);

  if (p.accessory === 1) {
    const jade = new THREE.Mesh(
      new THREE.TorusGeometry(0.07, 0.022, 8, 14),
      mat('#8fd9c8', 0.35, 0.1),
    );
    jade.position.set(0.32 * B, 1.3, 0.12);
    jade.rotation.x = Math.PI / 2;
    root.add(jade);
    const tassel = new THREE.Mesh(
      new THREE.CylinderGeometry(0.012, 0.012, 0.22, 6),
      mat('#a3435a', 0.9),
    );
    tassel.position.set(0.34 * B, 1.2, 0.14);
    root.add(tassel);
  }

  /* ---- 上身 ---- */
  const torso = new THREE.Group();
  const torsoMesh = new THREE.Mesh(new THREE.CapsuleGeometry(0.3 * B, 0.4, 6, 14), robeMat);
  torsoMesh.position.y = 1.58;
  torsoMesh.castShadow = true;
  torso.add(torsoMesh);

  // 交领（V 字衣领）
  for (const s of [-1, 1]) {
    const lapel = new THREE.Mesh(new THREE.BoxGeometry(0.4, 0.09, 0.05), robeDark);
    lapel.position.set(s * 0.09, 1.86, 0.26);
    lapel.rotation.z = s * 0.62;
    torso.add(lapel);
  }
  root.add(torso);

  /* ---- 手臂 ---- */
  const armGeo = new THREE.CapsuleGeometry(0.095, 0.4, 4, 10);
  const sleeveGeo = new THREE.CylinderGeometry(0.175, 0.105, 0.4, 12);
  const handGeo = new THREE.SphereGeometry(0.085, 10, 8);

  const armL = new THREE.Group();
  armL.position.set(-0.42 * B, 1.96, 0);
  const sleeveL = new THREE.Mesh(sleeveGeo, robeMat);
  sleeveL.position.y = -0.16;
  sleeveL.castShadow = true;
  const foreL = new THREE.Mesh(armGeo, robeDark);
  foreL.position.y = -0.44;
  const handL = new THREE.Mesh(handGeo, skinMat);
  handL.position.y = -0.66;
  armL.add(sleeveL, foreL, handL);

  const armR = new THREE.Group();
  armR.position.set(0.42 * B, 1.96, 0);
  const sleeveR = new THREE.Mesh(sleeveGeo, robeMat);
  sleeveR.position.y = -0.16;
  sleeveR.castShadow = true;
  const foreR = new THREE.Mesh(armGeo, robeDark);
  foreR.position.y = -0.44;
  const handR = new THREE.Mesh(handGeo, skinMat);
  handR.position.y = -0.66;
  armR.add(sleeveR, foreR, handR);
  root.add(armL, armR);

  /* ---- 颈 + 头 ---- */
  const neck = new THREE.Mesh(new THREE.CylinderGeometry(0.095, 0.105, 0.16, 10), skinMat);
  neck.position.y = 2.16;
  root.add(neck);

  const head = new THREE.Group();
  head.position.y = 2.5;

  const skull = new THREE.Mesh(new THREE.SphereGeometry(0.28, 24, 18), skinMat);
  skull.scale.set(F, 1.06, 0.96);
  skull.castShadow = true;
  head.add(skull);

  // 眼
  for (const s of [-1, 1]) {
    const eye = new THREE.Mesh(new THREE.SphereGeometry(0.03, 10, 8), darkMat);
    eye.position.set(s * 0.078 * F, 0.02, 0.252);
    head.add(eye);
    const brow = new THREE.Mesh(new THREE.BoxGeometry(0.1, 0.018, 0.02), hairMat);
    brow.position.set(s * 0.085 * F, 0.095, 0.252);
    brow.rotation.z = s * 0.12;
    head.add(brow);
  }

  // 鼻 + 唇
  const nose = new THREE.Mesh(new THREE.ConeGeometry(0.032, 0.09, 8), skinMat);
  nose.position.set(0, -0.03, 0.262);
  nose.rotation.x = Math.PI / 2;
  head.add(nose);
  const lip = new THREE.Mesh(new THREE.BoxGeometry(0.09, 0.016, 0.02), mat('#a05a52', 0.8));
  lip.position.set(0, -0.11, 0.248);
  head.add(lip);

  // 发（三种）：发帽只盖到额头以上，不能糊住脸
  const capTheta = p.hairStyle === 1 ? 0.29 : 0.33;
  const cap = new THREE.Mesh(
    new THREE.SphereGeometry(0.292, 24, 18, 0, Math.PI * 2, 0, Math.PI * capTheta),
    hairMat,
  );
  cap.scale.set(F, 1.05, 0.98);
  cap.position.y = 0.02;
  head.add(cap);

  if (p.hairStyle === 0) {
    // 束发：脑后发髻 + 金簪
    const bun = new THREE.Mesh(new THREE.SphereGeometry(0.13, 14, 12), hairMat);
    bun.position.set(0, 0.27, -0.09);
    head.add(bun);
    const pin = new THREE.Mesh(new THREE.CylinderGeometry(0.016, 0.016, 0.42, 6), goldMat);
    pin.position.set(0, 0.29, -0.09);
    pin.rotation.z = Math.PI / 2;
    head.add(pin);
  } else if (p.hairStyle === 1) {
    // 长发：披肩 + 两侧垂发
    const backHair = new THREE.Mesh(new THREE.CapsuleGeometry(0.16, 0.46, 4, 10), hairMat);
    backHair.position.set(0, -0.26, -0.19);
    backHair.scale.set(1.15, 1, 0.55);
    head.add(backHair);
    for (const s of [-1, 1]) {
      const strand = new THREE.Mesh(new THREE.CapsuleGeometry(0.055, 0.4, 4, 8), hairMat);
      strand.position.set(s * 0.25 * F, -0.14, 0.01);
      head.add(strand);
    }
  } else {
    // 冠：金冠 + 顶珠
    const crown = new THREE.Mesh(new THREE.CylinderGeometry(0.19, 0.23, 0.17, 12), goldMat);
    crown.position.y = 0.28;
    head.add(crown);
    const bead = new THREE.Mesh(new THREE.SphereGeometry(0.065, 12, 10), mat('#e8e2d0', 0.4, 0.2));
    bead.position.y = 0.4;
    head.add(bead);
  }

  if (p.beard) {
    const beard = new THREE.Mesh(new THREE.ConeGeometry(0.085, 0.2, 10), hairMat);
    beard.position.set(0, -0.3, 0.15);
    beard.rotation.x = Math.PI;
    head.add(beard);
  }

  root.add(head);

  /* ---- 背剑 ---- */
  if (p.accessory === 2) {
    const sword = new THREE.Group();
    sword.position.set(-0.22 * B, 1.36, -0.22);
    sword.rotation.set(-0.12, 0, 0.34);
    const scabbard = new THREE.Mesh(new THREE.BoxGeometry(0.075, 0.98, 0.11), darkMat);
    scabbard.castShadow = true;
    sword.add(scabbard);
    const guard = new THREE.Mesh(new THREE.BoxGeometry(0.16, 0.04, 0.12), goldMat);
    guard.position.y = 0.52;
    sword.add(guard);
    const hilt = new THREE.Mesh(
      new THREE.CylinderGeometry(0.032, 0.032, 0.24, 8),
      mat('#4a3524', 0.85),
    );
    hilt.position.y = 0.66;
    sword.add(hilt);
    root.add(sword);
  }

  // 整体身高 / 体型缩放（横向跟随 build，纵向跟随 height）
  root.scale.set(1 + (B - 1) * 0.55, p.height, 1 + (B - 1) * 0.55);

  return { root, limbs: { legL, legR, armL, armR, head, torso } };
}

export function createAvatar(params: AvatarParams): AvatarHandle {
  const group = new THREE.Group();
  let current: AvatarParams = { ...params };
  let built: Built | null = null;
  let t = 0;
  let phase = 0;

  const rebuild = () => {
    if (built) {
      group.remove(built.root);
      disposeTree(built.root);
    }
    built = buildBody(current);
    group.add(built.root);
  };
  rebuild();

  return {
    group,
    update(dt, speed) {
      if (!built) {
        return;
      }
      t += dt;
      phase += dt * (2.2 + speed * 0.62);
      const swing = Math.min(1, speed / 8);
      const { legL, legR, armL, armR, head, torso } = built.limbs;

      legL.rotation.x = Math.sin(phase) * 0.62 * swing;
      legR.rotation.x = -Math.sin(phase) * 0.62 * swing;
      armL.rotation.x = -Math.sin(phase) * 0.42 * swing;
      armR.rotation.x = Math.sin(phase) * 0.42 * swing;
      // 站立时手臂微微外张，避免与身体穿模
      armL.rotation.z = 0.08 + (1 - swing) * 0.03;
      armR.rotation.z = -0.08 - (1 - swing) * 0.03;

      // 呼吸 + 步频起伏
      const bob =
        Math.abs(Math.sin(phase)) * 0.07 * swing + Math.sin(t * 1.4) * 0.012 * (1 - swing);
      built.root.position.y = bob;
      torso.scale.y = 1 + Math.sin(t * 1.4) * 0.012 * (1 - swing);

      // 静止时轻轻张望
      head.rotation.y = Math.sin(t * 0.45) * 0.16 * (1 - swing);
      head.rotation.x = Math.sin(t * 0.7) * 0.04;
    },
    setParams(next) {
      current = { ...next };
      rebuild();
    },
    dispose() {
      if (built) {
        group.remove(built.root);
        disposeTree(built.root);
        built = null;
      }
    },
  };
}

/* ==========================================================================
 * 面板预览：独立的小渲染器（独立 WebGL context，与主场景互不影响）
 * ========================================================================== */

export interface AvatarPreview {
  setParams: (p: AvatarParams) => void;
  resize: () => void;
  dispose: () => void;
}

export function createAvatarPreview(
  canvas: HTMLCanvasElement,
  initial: AvatarParams,
): AvatarPreview {
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.toneMapping = THREE.ACESFilmicToneMapping;

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(34, 1, 0.1, 60);
  camera.position.set(0, 1.62, 5.1);
  camera.lookAt(0, 1.45, 0);

  scene.add(new THREE.HemisphereLight(0xcfe4ff, 0x2a2418, 1.1));
  const key = new THREE.DirectionalLight(0xfff0cf, 2.0);
  key.position.set(2.4, 4.4, 3.2);
  scene.add(key);
  const rim = new THREE.DirectionalLight(0x7fd8ff, 0.7);
  rim.position.set(-3, 2.4, -2.6);
  scene.add(rim);

  // 承接地台：让脚底不悬空
  const dais = new THREE.Mesh(
    new THREE.CircleGeometry(1.5, 48),
    new THREE.MeshBasicMaterial({ color: 0x0c1a26, transparent: true, opacity: 0.65 }),
  );
  dais.rotation.x = -Math.PI / 2;
  scene.add(dais);

  const avatar = createAvatar(initial);
  scene.add(avatar.group);

  let rafId = 0;
  let last = performance.now();
  let t = 0;

  const resize = () => {
    const w = canvas.clientWidth || 240;
    const h = canvas.clientHeight || 320;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  };
  resize();

  const loop = () => {
    rafId = requestAnimationFrame(loop);
    const now = performance.now();
    const dt = Math.min((now - last) / 1000, 0.05);
    last = now;
    t += dt;
    avatar.group.rotation.y = Math.sin(t * 0.4) * 0.5;
    avatar.update(dt, 0);
    renderer.render(scene, camera);
  };
  loop();

  return {
    setParams: (p) => avatar.setParams(p),
    resize,
    dispose() {
      cancelAnimationFrame(rafId);
      avatar.dispose();
      scene.traverse((obj) => {
        const mesh = obj as THREE.Mesh;
        if (mesh.geometry) {
          mesh.geometry.dispose();
        }
        const material = mesh.material;
        const list = Array.isArray(material) ? material : material ? [material] : [];
        for (const m of list) {
          m.dispose();
        }
      });
      renderer.dispose();
    },
  };
}
