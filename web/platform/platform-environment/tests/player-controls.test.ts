import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { PerspectiveCamera, Scene } from 'three';
import { createPlayerControls, type PlayerControlsConfig } from '@/scene/player/controls';
import type { SceneContext } from '@/scene';

/**
 * 输入装配的契约。
 *
 * 这里刻意**不建 WebGL**：控制器只用相机的位姿和几张 DOM 事件，
 * 与渲染无关。于是"按键走位、右键设点、失焦停手、销毁解绑"这些
 * 真正会出 bug 的地方可以全在 jsdom 里跑。
 *
 * jsdom 的 `getBoundingClientRect` 返回全 0，射线取点会除出 NaN ——
 * 所以下面把画布尺寸桩成 800×600。这也顺带证实了控制器里那道
 * "尺寸为 0 就返回 null" 的护栏是必要的。
 */

const CANVAS_WIDTH = 800;
const CANVAS_HEIGHT = 600;

function stubRect(canvas: HTMLCanvasElement): void {
  canvas.getBoundingClientRect = () =>
    ({
      left: 0,
      top: 0,
      width: CANVAS_WIDTH,
      height: CANVAS_HEIGHT,
      right: CANVAS_WIDTH,
      bottom: CANVAS_HEIGHT,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    }) as DOMRect;
}

/** 相机在出生点后方偏上，正对世界原点 —— 与场景里的初始机位同类。 */
function makeCamera(): PerspectiveCamera {
  const camera = new PerspectiveCamera(50, CANVAS_WIDTH / CANVAS_HEIGHT, 0.1, 400);
  camera.position.set(0, 10, 20);
  camera.lookAt(0, 0, 0);
  camera.updateMatrixWorld();
  return camera;
}

describe('createPlayerControls', () => {
  let canvas: HTMLCanvasElement;
  let camera: PerspectiveCamera;
  let scene: Scene;
  let config: PlayerControlsConfig;
  let context: SceneContext;

  beforeEach(() => {
    canvas = document.createElement('canvas');
    stubRect(canvas);
    document.body.appendChild(canvas);

    camera = makeCamera();
    scene = new Scene();
    config = {
      sceneId: 'ancient-architecture',
      scene,
      spawn: { x: 0, z: 5, heading: 0 },
      halfExtent: 20,
      obstacles: [],
      minimap: { groundSize: 40, obstacles: [{ x: 0, z: 0, width: 4, depth: 4 }] },
    };
    context = { camera, domElement: canvas };
  });

  afterEach(() => {
    canvas.remove();
  });

  it('把主人公加进场景图', () => {
    const controls = createPlayerControls(config, context);

    expect(scene.children.length).toBe(1);

    controls.dispose();
  });

  it('按 W 朝镜头深处走（相机在 +Z 侧，因此人物走向 -Z）', () => {
    const controls = createPlayerControls(config, context);

    window.dispatchEvent(new KeyboardEvent('keydown', { code: 'KeyW' }));
    controls.update(0, 0.5);

    expect(controls.snapshot().player.z).toBeLessThan(5);
    expect(controls.snapshot().player.x).toBeCloseTo(0, 6);

    controls.dispose();
  });

  it('方向键与 WASD 等价，并且拦掉浏览器默认的滚动', () => {
    const controls = createPlayerControls(config, context);

    const event = new KeyboardEvent('keydown', { code: 'ArrowUp', cancelable: true });
    window.dispatchEvent(event);
    controls.update(0, 0.5);

    expect(event.defaultPrevented).toBe(true);
    expect(controls.snapshot().player.z).toBeLessThan(5);

    controls.dispose();
  });

  it('松开按键后停下', () => {
    const controls = createPlayerControls(config, context);

    window.dispatchEvent(new KeyboardEvent('keydown', { code: 'KeyW' }));
    controls.update(0, 0.5);
    const moved = controls.snapshot().player.z;

    window.dispatchEvent(new KeyboardEvent('keyup', { code: 'KeyW' }));
    controls.update(0, 0.5);

    expect(controls.snapshot().player.z).toBeCloseTo(moved, 10);

    controls.dispose();
  });

  it('W 与 ↑ 同时按下不会被算成两次（按语义去重）', () => {
    const controls = createPlayerControls(config, context);

    window.dispatchEvent(new KeyboardEvent('keydown', { code: 'KeyW' }));
    window.dispatchEvent(new KeyboardEvent('keydown', { code: 'ArrowUp' }));
    controls.update(0, 1);

    // 一帧走 speed × 1，与只按一个键一致
    expect(controls.snapshot().player.z).toBeCloseTo(5 - 7, 6);

    controls.dispose();
  });

  it('窗口失焦会松开全部按键 —— 否则切走再回来人物会一直走', () => {
    const controls = createPlayerControls(config, context);

    window.dispatchEvent(new KeyboardEvent('keydown', { code: 'KeyW' }));
    controls.update(0, 0.5);
    const moved = controls.snapshot().player.z;

    window.dispatchEvent(new Event('blur'));
    controls.update(0, 0.5);

    expect(controls.snapshot().player.z).toBeCloseTo(moved, 10);

    controls.dispose();
  });

  it('右键把行动终点设在射线打到地面的位置，并屏蔽浏览器菜单', () => {
    const controls = createPlayerControls(config, context);
    camera.updateMatrixWorld();

    const event = new MouseEvent('contextmenu', {
      clientX: CANVAS_WIDTH / 2,
      clientY: CANVAS_HEIGHT / 2,
      bubbles: true,
      cancelable: true,
    });
    canvas.dispatchEvent(event);

    const snapshot = controls.snapshot();
    expect(event.defaultPrevented).toBe(true);
    expect(snapshot.hasDestination).toBe(true);
    // 屏幕正中对应的射线穿向 lookAt 的目标（世界原点）
    expect(snapshot.destination?.x).toBeCloseTo(0, 6);
    expect(snapshot.destination?.z).toBeCloseTo(0, 6);

    controls.dispose();
  });

  it('右键点向天空（射线朝上）不会设出终点', () => {
    const controls = createPlayerControls(config, context);
    camera.position.set(0, 10, 0);
    camera.lookAt(0, 30, 0);
    camera.updateMatrixWorld();

    canvas.dispatchEvent(
      new MouseEvent('contextmenu', {
        clientX: CANVAS_WIDTH / 2,
        clientY: CANVAS_HEIGHT / 2,
        bubbles: true,
        cancelable: true,
      }),
    );

    expect(controls.snapshot().hasDestination).toBe(false);

    controls.dispose();
  });

  it('画布还没布局（尺寸为 0）时射线取点返回空，而不是算出 NaN', () => {
    const controls = createPlayerControls(config, context);
    canvas.getBoundingClientRect = () => ({ width: 0, height: 0, left: 0, top: 0 }) as DOMRect;

    canvas.dispatchEvent(
      new MouseEvent('contextmenu', {
        clientX: 10,
        clientY: 10,
        bubbles: true,
        cancelable: true,
      }),
    );

    const snapshot = controls.snapshot();
    expect(snapshot.hasDestination).toBe(false);
    expect(Number.isFinite(snapshot.player.x)).toBe(true);

    controls.dispose();
  });

  it('快照如实带出场景 id、小地图地物与相机距离', () => {
    const controls = createPlayerControls(config, context);

    const snapshot = controls.snapshot();
    expect(snapshot.sceneId).toBe('ancient-architecture');
    expect(snapshot.map.groundSize).toBe(40);
    expect(snapshot.map.obstacles).toHaveLength(1);
    // 相机在 (0,10,20)、人物在 (0,*,5)：距离必须是个正数，小地图的扇形靠它定长
    expect(snapshot.cameraDistance).toBeGreaterThan(0);

    controls.dispose();
  });

  it('focus 给的是人物位置 + 胸口高度', () => {
    const controls = createPlayerControls(config, context);

    const [x, y, z] = controls.focus()!;
    expect(x).toBeCloseTo(0, 6);
    expect(z).toBeCloseTo(5, 6);
    expect(y).toBeGreaterThan(1);

    controls.dispose();
  });

  it('dispose 解绑监听并把人物移出场景（不留幽灵人物）', () => {
    const controls = createPlayerControls(config, context);
    expect(scene.children.length).toBe(1);

    controls.dispose();
    expect(scene.children.length).toBe(0);

    // 键盘已解绑：再按键也不该产生位移
    const before = controls.snapshot().player.z;
    window.dispatchEvent(new KeyboardEvent('keydown', { code: 'KeyW' }));
    controls.update(0, 0.5);
    expect(controls.snapshot().player.z).toBeCloseTo(before, 10);
  });

  it('输入框里的 WASD 不驱动人物（那里是在打字）', () => {
    const controls = createPlayerControls(config, context);
    const input = document.createElement('input');
    document.body.appendChild(input);

    input.dispatchEvent(new KeyboardEvent('keydown', { code: 'KeyW', bubbles: true }));
    controls.update(0, 0.5);

    expect(controls.snapshot().player.z).toBeCloseTo(5, 6);

    input.remove();
    controls.dispose();
  });
});
