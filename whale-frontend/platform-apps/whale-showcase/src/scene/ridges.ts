import * as THREE from 'three';

/**
 * 远山剪影（两层环形山脊）。
 *
 * <p>地形平面只有 620m 宽，地平线处是一条平滑的丘陵边——全景机位里
 * "世界到那里就结束了"。真实景观的地平线是<b>多层山脊</b>：近山深、远山浅，
 * 中间隔着大气。两层 Basic 剪影（不参与光照，颜色即轮廓），吃场景雾
 * 自动做大气透视，按时段换色——黄昏是紫灰叠粉雾，白天是青蓝叠灰绿。</p>
 *
 * <p>高度剖面在<b>圆环上采样二维噪声</b>（单位圆 × 频率）——天然无缝闭合，
 * 不用处理角度环绕。山脊感来自 ridged 变换（1−|2n−1|），把圆丘翻成尖峰。</p>
 */

function hash2(x: number, y: number): number {
  const n = Math.sin(x * 127.1 + y * 311.7) * 43758.5453;
  return n - Math.floor(n);
}

function vnoise(x: number, y: number): number {
  const xi = Math.floor(x);
  const yi = Math.floor(y);
  const xf = x - xi;
  const yf = y - yi;
  const u = xf * xf * (3 - 2 * xf);
  const v = yf * yf * (3 - 2 * yf);
  const a = hash2(xi, yi);
  const b = hash2(xi + 1, yi);
  const c = hash2(xi, yi + 1);
  const d = hash2(xi + 1, yi + 1);
  return (a + (b - a) * u + (c - a + (d - c - b + a) * u) * v) * 0.5 + 0.25;
}

function fbm(x: number, y: number, oct: number): number {
  let sum = 0;
  let amp = 0.5;
  let norm = 0;
  for (let i = 0; i < oct; i += 1) {
    sum += vnoise(x, y) * amp;
    norm += amp;
    x = x * 2.03 + 7.3;
    y = y * 2.01 - 4.1;
    amp *= 0.5;
  }
  return sum / norm;
}

/** 环形山脊高度：单位圆上采样，天然周期闭合 */
function ridgeHeight(angle: number, seed: number, hMin: number, hMax: number): number {
  const cx = Math.cos(angle);
  const sz = Math.sin(angle);
  // broad：山群的大起伏（哪一段高哪一段低）
  const broad = fbm(cx * 2.1 + seed, sz * 2.1 - seed * 0.7, 3);
  // ridged：把噪声翻成山脊尖峰
  const rf = 1 - Math.abs(2 * fbm(cx * 3.1 + seed * 1.7, sz * 3.1 + seed, 3) - 1);
  // 细齿：小频率噪声让脊线不圆滑
  const jag = fbm(cx * 12 + seed * 3.1, sz * 12 - seed * 2.3, 2);
  const t = Math.min(1, Math.max(0, 0.3 * broad + 0.72 * Math.pow(rf, 1.5) + (jag - 0.5) * 0.26));
  return hMin + (hMax - hMin) * t;
}

function buildLayer(
  radius: number,
  seed: number,
  hMin: number,
  hMax: number,
  baseShade: number,
  topShade: number,
): THREE.Mesh {
  const N = 180;
  const positions: number[] = [];
  const colors: number[] = [];
  const indices: number[] = [];
  for (let i = 0; i <= N; i += 1) {
    const a = (i / N) * Math.PI * 2;
    const x = Math.cos(a) * radius;
    const z = Math.sin(a) * radius;
    const h = ridgeHeight(a, seed, hMin, hMax);
    positions.push(x, -8, z, x, h, z);
    // 根部略深、脊线略亮：剪影也有一点点体积
    colors.push(baseShade, baseShade, baseShade, topShade, topShade, topShade);
  }
  for (let i = 0; i < N; i += 1) {
    const b = i * 2;
    indices.push(b, b + 1, b + 2, b + 1, b + 3, b + 2);
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3));
  geo.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3));
  geo.setIndex(indices);
  const mat = new THREE.MeshBasicMaterial({
    vertexColors: true,
    // 不吃场景雾：365m/555m 处雾已冲掉 47%~78% 的本色，山脊会退化成一条
    // "雾墙"。大气透视直接烘进时段色（远层比近层浅一档），对比可控。
    fog: false,
    side: THREE.DoubleSide, // 相机在环内，正面朝内即可见；双面防剔除边界闪
  });
  const mesh = new THREE.Mesh(geo, mat);
  mesh.frustumCulled = false; // 环绕全场景，包围盒剔除没意义
  mesh.matrixAutoUpdate = false;
  return mesh;
}

export interface FarRidges {
  group: THREE.Group;
  /** 按时段换两层颜色（近层深、远层浅） */
  setColors: (near: THREE.ColorRepresentation, far: THREE.ColorRepresentation) => void;
  dispose: () => void;
}

export function createFarRidges(): FarRidges {
  const group = new THREE.Group();
  group.name = 'far-ridges';

  // 峰顶仰角约束：overview 机位 (y=21) 俯视 15.8°、FOV 40°，画面上缘仅 +4.2°；
  // 且环以原点为心、机位偏北 52m，北向山距被拉近到 288/428m——峰顶必须压在
  // 36m/46m（仰角 ≈3°/3.7°）以下，否则两层山体填满地平线以上全部天空，
  // 云与晚霞被整个吃掉（第一版 52/85m 实测整幅天变成一面紫墙）。
  const nearLayer = buildLayer(340, 11.7, 2, 36, 0.8, 1.1);
  const farLayer = buildLayer(480, 4.9, 14, 46, 0.9, 1.04);
  group.add(nearLayer, farLayer);

  return {
    group,
    setColors(near, far) {
      (nearLayer.material as THREE.MeshBasicMaterial).color.set(near);
      (farLayer.material as THREE.MeshBasicMaterial).color.set(far);
    },
    dispose() {
      nearLayer.geometry.dispose();
      (nearLayer.material as THREE.Material).dispose();
      farLayer.geometry.dispose();
      (farLayer.material as THREE.Material).dispose();
    },
  };
}
