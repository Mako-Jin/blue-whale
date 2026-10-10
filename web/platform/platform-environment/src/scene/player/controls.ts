import { Plane, Raycaster, Vector2, Vector3, type Scene } from 'three';
import { createPlayerAvatar } from './avatar';
import { createPlayerMotor, type Obstacle } from './motor';
import type { MinimapFeatures, SceneControls, SceneContext, SceneId } from '../types';

/**
 * 主人公的输入与装配。
 *
 * 把三件事接在一起：**移动内核**（`motor.ts`，纯逻辑）、**造型**（`avatar.ts`）、
 * 以及 DOM 事件。这里再往上就不再有任何算法 —— 算法都在内核里，
 * 这个文件只负责"把按键翻译成意图、把右键翻译成目标点、把位姿写进场景图"。
 *
 * 三处刻意的取舍：
 *
 * 1. **相机方位每帧现算**，不缓存。"上"必须始终是"朝画面深处"，
 *    相机一转，按键的世界方向就得跟着转。
 * 2. **右键用射线打到地面平面**，不是打到地面网格上。网格只有在打开
 *    "地面网格"开关时才存在，靠它取点会让功能随开关一起失效。
 * 3. **不接管左键与滚轮**：左键旋转、滚轮缩放交给舞台的轨道控制器，
 *    本控制器只借走键盘与右键。这样"放大缩小视角"不需要另写一套。
 */

const MOVE_KEYS: Record<string, 'up' | 'down' | 'left' | 'right'> = {
  KeyW: 'up',
  ArrowUp: 'up',
  KeyS: 'down',
  ArrowDown: 'down',
  KeyA: 'left',
  ArrowLeft: 'left',
  KeyD: 'right',
  ArrowRight: 'right',
};

const DEFAULTS = {
  radius: 0.5,
  speed: 7,
  turnSpeed: 8,
  arriveRadius: 0.4,
  /** 相机焦点的高度：大致是胸口。给 0 会变成贴着脚面看 */
  focusHeight: 1.35,
} as const;

export type PlayerControlsConfig = {
  sceneId: SceneId;
  /** 主人公被加进这棵场景图 */
  scene: Scene;
  spawn: { x: number; z: number; heading: number };
  /** 可行走正方形区域的半边长 */
  halfExtent: number;
  /** 不可通行的矩形（世界坐标） */
  obstacles: readonly Obstacle[];
  /** 小地图地物：与 `obstacles` 同源，只是换成了"中心 + 尺寸"的表达 */
  minimap: MinimapFeatures;
  radius?: number;
  speed?: number;
  turnSpeed?: number;
  arriveRadius?: number;
  focusHeight?: number;
};

/** 判断事件是否发生在文本输入上 —— 在那里 WASD 是打字，不该驱动人物。 */
function isTextEntry(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  return (
    target.tagName === 'INPUT' ||
    target.tagName === 'TEXTAREA' ||
    target.tagName === 'SELECT' ||
    target.isContentEditable
  );
}

export function createPlayerControls(
  config: PlayerControlsConfig,
  context: SceneContext,
): SceneControls {
  const { camera, domElement } = context;
  const view = domElement.ownerDocument.defaultView ?? window;

  const motor = createPlayerMotor({
    speed: config.speed ?? DEFAULTS.speed,
    turnSpeed: config.turnSpeed ?? DEFAULTS.turnSpeed,
    arriveRadius: config.arriveRadius ?? DEFAULTS.arriveRadius,
    halfExtent: config.halfExtent,
    radius: config.radius ?? DEFAULTS.radius,
    obstacles: config.obstacles,
    spawn: config.spawn,
  });

  const avatar = createPlayerAvatar();
  avatar.applyPose(motor.pose);
  config.scene.add(avatar.group);

  const raycaster = new Raycaster();
  const pointer = new Vector2();
  // 地面平面取 y = 0：场景的可行走面就在这个高度
  const groundPlane = new Plane(new Vector3(0, 1, 0), 0);
  const hitPoint = new Vector3();
  const lookDirection = new Vector3();
  // 复用同一个向量算相机距离，避免每帧新建对象给 GC 添活
  const focusPoint = new Vector3();
  const focusHeight = config.focusHeight ?? DEFAULTS.focusHeight;

  /** 当前朝向语义的按键集合。用语义而不是键码，W 与 ↑ 同时按下才不会被算成两次。 */
  const pressed = new Set<'up' | 'down' | 'left' | 'right'>();

  function syncIntent(): void {
    const forward = (pressed.has('up') ? 1 : 0) - (pressed.has('down') ? 1 : 0);
    const right = (pressed.has('right') ? 1 : 0) - (pressed.has('left') ? 1 : 0);
    motor.setIntent({ forward, right });
  }

  /** 相机的水平朝向（弧度）。约定方向向量为 `(sin a, cos a)`，与内核一致。 */
  function cameraAzimuth(): number {
    camera.getWorldDirection(lookDirection);
    return Math.atan2(lookDirection.x, lookDirection.z);
  }

  function pickGround(clientX: number, clientY: number): { x: number; z: number } | null {
    const rect = domElement.getBoundingClientRect();
    // 尺寸为 0 说明还没布局完（无头环境常见）。不挡住会除出 NaN 一路传进内核
    if (rect.width === 0 || rect.height === 0) {
      return null;
    }
    pointer.x = ((clientX - rect.left) / rect.width) * 2 - 1;
    pointer.y = -((clientY - rect.top) / rect.height) * 2 + 1;
    raycaster.setFromCamera(pointer, camera);
    if (!raycaster.ray.intersectPlane(groundPlane, hitPoint)) {
      return null; // 射线朝上，与地面平行或背离 —— 比如点了天空
    }
    return { x: hitPoint.x, z: hitPoint.z };
  }

  const onKeyDown = (event: KeyboardEvent): void => {
    const semantic = MOVE_KEYS[event.code];
    if (!semantic || isTextEntry(event.target)) {
      return;
    }
    // 方向键默认会滚动页面，必须挡住，否则按一下人物走一步、页面也跳一下
    if (event.code.startsWith('Arrow')) {
      event.preventDefault();
    }
    if (pressed.has(semantic)) {
      return;
    }
    pressed.add(semantic);
    syncIntent();
  };

  const onKeyUp = (event: KeyboardEvent): void => {
    const semantic = MOVE_KEYS[event.code];
    if (!semantic) {
      return;
    }
    pressed.delete(semantic);
    syncIntent();
  };

  /**
   * 失焦时清空按键。
   *
   * 不处理的话，按住 W 切走、松开、再切回来，人物会一直朝前走 ——
   * 因为 keyup 发生在别的窗口里，这里永远收不到。
   */
  const onBlur = (): void => {
    if (pressed.size === 0) {
      return;
    }
    pressed.clear();
    syncIntent();
  };

  const onContextMenu = (event: MouseEvent): void => {
    // 右键在这里是"设置行动终点"，不是"另存为"：必须屏蔽浏览器菜单
    event.preventDefault();
    const point = pickGround(event.clientX, event.clientY);
    if (point) {
      motor.setDestination(point.x, point.z);
    }
  };

  view.addEventListener('keydown', onKeyDown);
  view.addEventListener('keyup', onKeyUp);
  view.addEventListener('blur', onBlur);
  domElement.addEventListener('contextmenu', onContextMenu);

  return {
    update(_elapsed: number, delta: number): void {
      motor.update(delta, cameraAzimuth());
      avatar.applyPose(motor.pose);
    },

    focus(): readonly [number, number, number] {
      const pose = motor.pose;
      return [pose.x, focusHeight, pose.z];
    },

    snapshot() {
      const pose = motor.pose;
      const destination = motor.destination;
      /**
       * 相机到焦点的距离就当作"缩放量"。
       *
       * 相机绕着焦点转、焦点跟着人走（见 `stage.ts` 的 `syncFocus`），
       * 所以这个距离等于用户的滚轮缩放结果。不引入第二个真相源。
       */
      focusPoint.set(pose.x, focusHeight, pose.z);
      return {
        sceneId: config.sceneId,
        player: { x: pose.x, z: pose.z, heading: pose.heading },
        cameraAzimuth: cameraAzimuth(),
        cameraDistance: camera.position.distanceTo(focusPoint),
        hasDestination: destination !== null,
        destination: destination ? { x: destination.x, z: destination.z } : null,
        map: config.minimap,
      };
    },

    dispose(): void {
      view.removeEventListener('keydown', onKeyDown);
      view.removeEventListener('keyup', onKeyUp);
      view.removeEventListener('blur', onBlur);
      domElement.removeEventListener('contextmenu', onContextMenu);
      config.scene.remove(avatar.group);
      avatar.dispose();
    },
  };
}
