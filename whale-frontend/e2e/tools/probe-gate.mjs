// 城门机位探针：从相机向画面若干像素发射线，报告命中的对象名与位置
import { chromium } from '@playwright/test';
const browser = await chromium.launch({ channel: 'msedge', args: ['--use-angle=d3d11','--ignore-gpu-blocklist','--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
page.on('pageerror', e => console.log('PAGEERROR', e.message));
await page.goto('http://127.0.0.1:8126/', { waitUntil: 'load', timeout: 60000 });
await page.waitForTimeout(6000);
await page.getByRole('button', { name: '白天', exact: true }).click();
await page.getByRole('button', { name: '城门', exact: true }).click();
await page.waitForTimeout(200);
let p1 = await page.evaluate(() => window.__bwScene.camera.position.toArray().map(v=>v.toFixed(2)).join(','));
console.log('t+0.2s:', p1);
await page.waitForTimeout(800);
p1 = await page.evaluate(() => window.__bwScene.camera.position.toArray().map(v=>v.toFixed(2)).join(','));
console.log('t+1.0s:', p1);
await page.waitForTimeout(1200);
const out = await page.evaluate(async () => {
  const { scene, camera } = window.__bwScene;
  const three = await import('/node_modules/.vite/deps/three.js');
  const ray = new three.Raycaster();
  const res = [];
  for (const [nx, ny] of [[0.85, 0.5], [0.72, 0.4], [0.9, 0.2], [0.5, 0.5]]) {
    ray.setFromCamera(new three.Vector2(nx, ny), camera);
    const hits = ray.intersectObjects(scene.children, true);
    const h = hits[0];
    if (h) {
      let name = h.object.name || '(anon)';
      let p = h.object;
      while (!name.startsWith('settlement') && p.parent && !p.parent.name.includes('settlement')) { p = p.parent; name = p.name || name; }
      res.push({ nx, ny, name, mat: h.object.material?.name ?? '', dist: h.distance.toFixed(1), point: h.point.toArray().map(v=>v.toFixed(1)) });
    } else res.push({ nx, ny, name: '(miss)' });
  }
  const cam = camera.position.toArray().map(v=>v.toFixed(1));
  const tgt = (() => { const d = new three.Vector3(); camera.getWorldDirection(d); return d.toArray().map(v=>v.toFixed(2)); })();
  return { res, cam, dir: tgt };
});
console.log(JSON.stringify(out, null, 1));
await browser.close();
