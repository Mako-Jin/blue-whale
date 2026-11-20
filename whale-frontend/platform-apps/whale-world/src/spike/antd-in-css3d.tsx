/**
 * T0.5 · antd 业务页面挂入 CSS3D 面板
 *
 * <p>要回答的不是"能不能渲染"，而是三个具体问题：
 * ① antd 的 cssinjs 样式在 3D 变换容器里会不会错乱；
 * ② Table / Input 这类复杂组件是否正常工作；
 * ③ **弹层（Select 下拉）会不会飞出去** —— 这是最要命的：antd 默认把弹层挂到 body，
 *    一旦脱离 3D 变换，下拉就会出现在屏幕某个固定角落，而不是跟着面板走。</p>
 *
 * <p>用 ?fix=1 打开 getPopupContainer，把弹层锁进面板内部，作为对照。</p>
 */

import { useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Button, Input, Select, Table } from 'antd';
import * as THREE from 'three';
import { CSS3DRenderer, CSS3DObject } from 'three/addons/renderers/CSS3DRenderer.js';

const FIXED = new URLSearchParams(window.location.search).get('fix') === '1';

const stage = document.getElementById('stage') as HTMLElement;
const css3dRoot = document.getElementById('css3d-root') as HTMLElement;
const hudPopup = document.getElementById('hud-popup') as HTMLElement;
const hudPos = document.getElementById('hud-pos') as HTMLElement;
const hudVerdict = document.getElementById('hud-verdict') as HTMLElement;

const width = window.innerWidth;
const height = window.innerHeight;

/* ---------------------------------------------------------- 3D 底座 */

const camera = new THREE.PerspectiveCamera(50, width / height, 0.1, 200);

// 相机横向偏移可调：用来做"面板动了，下拉跟不跟"的对照实验
const CAM_THETA = Number(new URLSearchParams(window.location.search).get('theta') ?? '0');
camera.position.set(Math.sin(CAM_THETA) * 6.3, 1.95, Math.cos(CAM_THETA) * 6.3);
camera.lookAt(0, 1.75, 0);

const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
renderer.setSize(width, height);
renderer.setClearColor(0x0b1016, 1);
stage.appendChild(renderer.domElement);

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x0b1016);
scene.fog = new THREE.Fog(0x0b1016, 16, 40);

const grid = new THREE.GridHelper(40, 40, 0x24485c, 0x132430);
grid.position.y = -0.4;
scene.add(grid);

scene.add(new THREE.AmbientLight(0x9fc4dd, 0.7));
const key = new THREE.DirectionalLight(0xffe6c0, 1.9);
key.position.set(5, 10, 7);
scene.add(key);

// 面板背后放一面墙，证明面板确实处在三维空间里（有透视、有遮挡感）
const backWall = new THREE.Mesh(
  new THREE.BoxGeometry(9, 5.6, 0.3),
  new THREE.MeshStandardMaterial({ color: 0x2a3a46, roughness: 0.9 }),
);
backWall.position.set(0, 2.1, -1.0);
scene.add(backWall);

/* ------------------------------------------------------- CSS3D 面板 */

const css3d = new CSS3DRenderer();
css3d.setSize(width, height);
css3dRoot.appendChild(css3d.domElement);

const css3dScene = new THREE.Scene();

const SHEET_SCALE = 0.0058;
const sheet = document.createElement('div');
sheet.className = 'sheet';

const panel = new CSS3DObject(sheet);
panel.position.set(0, 1.75, 0);
panel.scale.setScalar(SHEET_SCALE);
css3dScene.add(panel);

/* ----------------------------------------------------------- React */

const ROWS = [
  { key: '1', no: 'PO-2026-0117', item: '冷轧钢板 1.2mm', qty: 1200, status: '待审' },
  { key: '2', no: 'PO-2026-0118', item: '无氧铜线 2.5mm²', qty: 800, status: '已审' },
  { key: '3', no: 'PO-2026-0119', item: '不锈钢法兰 DN80', qty: 240, status: '待审' },
];

const COLUMNS = [
  { title: '单号', dataIndex: 'no', width: 180 },
  { title: '物料', dataIndex: 'item' },
  { title: '数量', dataIndex: 'qty', width: 100, align: 'right' as const },
  { title: '状态', dataIndex: 'status', width: 100 },
];

function Demo(): React.ReactElement {
  const popupHost = useRef<HTMLDivElement>(null);

  // 受控打开：autotest 需要下拉直接展开，便于截图判断位置
  const [open, setOpen] = useState(true);

  const selectProps = FIXED
    ? {
        getPopupContainer: () => popupHost.current ?? document.body,
      }
    : {};

  return (
    <div style={{ padding: '18px 20px 22px' }}>
      <div style={{ fontSize: 17, fontWeight: 600, marginBottom: 4 }}>
        ERP · 采购订单
      </div>
      <div style={{ fontSize: 12, color: '#8a7a5a', marginBottom: 14 }}>
        这张表是真实 antd 组件，渲染在三维面板的 DOM 里
      </div>

      <div style={{ display: 'flex', gap: 12, marginBottom: 14, alignItems: 'center' }}>
        <div ref={popupHost} style={{ position: 'relative' }}>
          <Select
            className="demo-select"
            style={{ width: 170 }}
            value="all"
            open={open}
            onDropdownVisibleChange={setOpen}
            options={[
              { value: 'all', label: '全部状态' },
              { value: 'pending', label: '待审' },
              { value: 'done', label: '已审' },
            ]}
            {...selectProps}
          />
        </div>
        <Input placeholder="搜索单号" style={{ width: 190 }} />
        <Button type="primary">查询</Button>
        <span style={{ fontSize: 12, color: FIXED ? '#2f7d4f' : '#b04a4a' }}>
          {FIXED ? '弹层已锁进面板' : '弹层默认挂 body'}
        </span>
      </div>

      <Table
        size="small"
        pagination={false}
        dataSource={ROWS}
        columns={COLUMNS}
      />
    </div>
  );
}

createRoot(sheet).render(<Demo />);

/* ---------------------------------------------------- 位置判定与循环 */

let lastCheck = 0;
let smooth = 0;

function checkPopup(): void {
  const sheetRect = sheet.getBoundingClientRect();
  const dd = document.querySelector('.ant-select-dropdown') as HTMLElement | null;

  if (!dd) {
    hudPos.textContent = '(未找到下拉)';
    return;
  }

  const r = dd.getBoundingClientRect();
  const cx = r.left + r.width / 2;
  const cy = r.top + r.height / 2;

  const inside =
    cx >= sheetRect.left && cx <= sheetRect.right &&
    cy >= sheetRect.top && cy <= sheetRect.bottom;

  hudPopup.textContent = FIXED ? '面板内（getPopupContainer）' : 'document.body';
  hudPos.textContent = `(${Math.round(cx)}, ${Math.round(cy)})`;
  hudVerdict.textContent = inside ? '在面板内 ✓' : '飞出面板 ✗';
  hudVerdict.className = inside ? 'v ok' : 'v bad';
  smooth = inside ? 1 : 0;
}
void smooth;

function animate(): void {
  requestAnimationFrame(animate);
  renderer.render(scene, camera);
  css3d.render(css3dScene, camera);

  const now = performance.now();
  if (now - lastCheck > 200) {
    lastCheck = now;
    checkPopup();
  }
}
animate();

window.addEventListener('resize', () => {
  const w = window.innerWidth;
  const h = window.innerHeight;
  camera.aspect = w / h;
  camera.updateProjectionMatrix();
  renderer.setSize(w, h);
  css3d.setSize(w, h);
});

// 自动验证入口：供自动化脚本读取
(window as unknown as { __t05?: unknown }).__t05 = {
  fixed: FIXED,
  check: checkPopup,
  sheetRect: () => sheet.getBoundingClientRect(),
  dropdownRect: () => document.querySelector('.ant-select-dropdown')?.getBoundingClientRect() ?? null,
};
