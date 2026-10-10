/**
 * 主人公的移动内核。
 *
 * **纯逻辑，不碰 three、不碰 DOM。** 位置、朝向、目标点全在一个对象里，
 * 输入是"意图 + 相机方位 + 帧间隔"，输出是对状态的推进。这样做的理由很实际：
 * 移动算法里的 bug（转身抖动、到点不判、卡在障碍边缘）在真实浏览器里
 * 只能靠肉眼盯，而在纯函数里可以逐帧断言。
 *
 * 角度约定（整个三维应用统一）：
 *
 * - `heading` / `azimuth` 都是绕 Y 轴的弧度；
 * - **0 表示朝 +Z**，方向向量就是 `(sin a, cos a)`；
 * - 这个约定让"朝向 → 小地图上的箭头"不需要任何翻转：世界 +X 向右、+Z 向下，
 *   方向向量直接就是屏幕方向。
 * - 罗盘上把 **-Z 定为北**（与最小地图的"上"一致）。
 */

/** 轴对齐的不可通行矩形（世界坐标）。 */
export type Obstacle = {
  minX: number;
  maxX: number;
  minZ: number;
  maxZ: number;
};

/** 由中心与尺寸构造障碍矩形。 */
export function obstacleFromRect(x: number, z: number, width: number, depth: number): Obstacle {
  return {
    minX: x - width / 2,
    maxX: x + width / 2,
    minZ: z - depth / 2,
    maxZ: z + depth / 2,
  };
}

/** 主人公的位置与朝向。 */
export type PlayerPose = {
  x: number;
  z: number;
  /** 弧度。0 = 朝 +Z */
  heading: number;
};

/**
 * 移动意图。
 *
 * `forward` 与 `right` 都在 `[-1, 1]`，是**相机相对**的：
 * 按"上"必须朝着画面深处走，而不是朝着世界 +Z —— 相机转到哪边，
 * 按键的语义就得跟着转，否则背对画面按"上"会往后退。
 */
export type MoveIntent = {
  forward: number;
  right: number;
};

export type PlayerMotorConfig = {
  /** 步行速度（世界单位 / 秒） */
  speed: number;
  /** 转身角速度（弧度 / 秒） */
  turnSpeed: number;
  /** 距终点小于该值即视为到达 */
  arriveRadius: number;
  /** 可行走正方形区域的半边长（以原点为中心） */
  halfExtent: number;
  /** 主人公碰撞半径：既是边界内缩量，也是障碍的外扩量 */
  radius: number;
  obstacles: readonly Obstacle[];
  /** 出生点 */
  spawn: { x: number; z: number; heading: number };
};

export type PlayerMotor = {
  readonly pose: Readonly<PlayerPose>;
  readonly destination: Readonly<{ x: number; z: number }> | null;
  /** 设置移动意图（键盘）。会**取消**已设的行动终点 —— 手控优先 */
  setIntent(intent: MoveIntent): void;
  /** 设置行动终点（右键）。位置会被夹到可行走范围内 */
  setDestination(x: number, z: number): void;
  clearDestination(): void;
  /** 推进一帧。`cameraAzimuth` 是相机的水平朝向（弧度，0 = 朝 +Z） */
  update(delta: number, cameraAzimuth: number): void;
};

/** 把角度归一到 (-π, π]，用于取最短转向。 */
export function normalizeAngle(angle: number): number {
  const twoPi = Math.PI * 2;
  let value = angle % twoPi;
  if (value > Math.PI) {
    value -= twoPi;
  }
  if (value <= -Math.PI) {
    value += twoPi;
  }
  return value;
}

/**
 * 把点夹到可行走范围内，并推出全部障碍矩形。
 *
 * 推出策略是"**沿穿透最浅的一边**"：从矩形的哪一侧压进来得最少，就从哪一侧弹出去。
 * 这样贴着墙走时不会突然被弹到对面 —— 那是"选固定边推出"最常见的毛病。
 * 多个障碍用一次遍历依次处理；障碍之间不重叠时可以认为已收敛。
 */
export function resolveCollision(
  x: number,
  z: number,
  config: Pick<PlayerMotorConfig, 'halfExtent' | 'radius' | 'obstacles'>,
): { x: number; z: number } {
  const limit = Math.max(0, config.halfExtent - config.radius);
  let nextX = Math.min(limit, Math.max(-limit, x));
  let nextZ = Math.min(limit, Math.max(-limit, z));

  config.obstacles.forEach((obstacle) => {
    const minX = obstacle.minX - config.radius;
    const maxX = obstacle.maxX + config.radius;
    const minZ = obstacle.minZ - config.radius;
    const maxZ = obstacle.maxZ + config.radius;

    if (nextX <= minX || nextX >= maxX || nextZ <= minZ || nextZ >= maxZ) {
      return;
    }
    const toMinX = nextX - minX;
    const toMaxX = maxX - nextX;
    const toMinZ = nextZ - minZ;
    const toMaxZ = maxZ - nextZ;
    const shallowest = Math.min(toMinX, toMaxX, toMinZ, toMaxZ);

    if (shallowest === toMinX) {
      nextX = minX;
    } else if (shallowest === toMaxX) {
      nextX = maxX;
    } else if (shallowest === toMinZ) {
      nextZ = minZ;
    } else {
      nextZ = maxZ;
    }
  });

  return { x: nextX, z: nextZ };
}

export function createPlayerMotor(config: PlayerMotorConfig): PlayerMotor {
  const pose: PlayerPose = {
    x: config.spawn.x,
    z: config.spawn.z,
    heading: config.spawn.heading,
  };
  let intent: MoveIntent = { forward: 0, right: 0 };
  let destination: { x: number; z: number } | null = null;

  /** 按角速度把当前朝向转向 `target`，返回新朝向。 */
  function turnToward(target: number, delta: number): number {
    const diff = normalizeAngle(target - pose.heading);
    const maxStep = config.turnSpeed * delta;
    const step = Math.sign(diff) * Math.min(Math.abs(diff), maxStep);
    return pose.heading + step;
  }

  function applyIntent(delta: number, cameraAzimuth: number): boolean {
    const magnitude = Math.hypot(intent.forward, intent.right);
    if (magnitude === 0) {
      return false;
    }
    /**
     * 相机右向量 = `(-cos a, sin a)`。
     *
     * 推导：世界是右手系、Y 向上，`right = forward × up`。
     * 代入 `forward = (sin a, 0, cos a)`、`up = (0, 1, 0)` 就得到这个结果。
     * 用错符号的典型症状是"按 D 往左走"。
     */
    const forwardX = Math.sin(cameraAzimuth) * intent.forward;
    const forwardZ = Math.cos(cameraAzimuth) * intent.forward;
    const rightX = -Math.cos(cameraAzimuth) * intent.right;
    const rightZ = Math.sin(cameraAzimuth) * intent.right;

    const moveX = forwardX + rightX;
    const moveZ = forwardZ + rightZ;
    const length = Math.hypot(moveX, moveZ);
    if (length === 0) {
      return false;
    }
    const dirX = moveX / length;
    const dirZ = moveZ / length;

    pose.heading = turnToward(Math.atan2(dirX, dirZ), delta);
    const next = resolveCollision(
      pose.x + dirX * config.speed * delta,
      pose.z + dirZ * config.speed * delta,
      config,
    );
    pose.x = next.x;
    pose.z = next.z;
    return true;
  }

  function applyDestination(delta: number): void {
    if (!destination) {
      return;
    }
    const toX = destination.x - pose.x;
    const toZ = destination.z - pose.z;
    const distance = Math.hypot(toX, toZ);

    if (distance <= config.arriveRadius) {
      destination = null;
      return;
    }
    const dirX = toX / distance;
    const dirZ = toZ / distance;
    pose.heading = turnToward(Math.atan2(dirX, dirZ), delta);

    // 单帧位移不超过剩余距离：否则会在终点上来回抖动（越过 → 折回 → 再越过）
    const step = Math.min(config.speed * delta, distance);
    const next = resolveCollision(pose.x + dirX * step, pose.z + dirZ * step, config);

    // 撞上障碍后位置几乎没动，说明这条直线上过不去。不处理会永远贴着墙原地走
    if (Math.hypot(next.x - pose.x, next.z - pose.z) < step * 0.25) {
      destination = null;
      return;
    }
    pose.x = next.x;
    pose.z = next.z;

    if (Math.hypot(destination.x - pose.x, destination.z - pose.z) <= config.arriveRadius) {
      destination = null;
    }
  }

  return {
    get pose(): Readonly<PlayerPose> {
      return pose;
    },

    get destination(): Readonly<{ x: number; z: number }> | null {
      return destination;
    },

    setIntent(next: MoveIntent): void {
      intent = next;
      // 手控优先：一旦按了方向键，就去掉自动行走的目标点
      if (next.forward !== 0 || next.right !== 0) {
        destination = null;
      }
    },

    setDestination(x: number, z: number): void {
      const limited = resolveCollision(x, z, config);
      destination = { x: limited.x, z: limited.z };
      intent = { forward: 0, right: 0 };
    },

    clearDestination(): void {
      destination = null;
    },

    update(delta: number, cameraAzimuth: number): void {
      if (delta <= 0) {
        return;
      }
      // 有手控输入时不走目标点；两者同时存在会让位移方向互相抵消
      if (!applyIntent(delta, cameraAzimuth)) {
        applyDestination(delta);
      }
    },
  };
}
