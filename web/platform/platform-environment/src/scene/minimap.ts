/**
 * 小地图的投影与几何。
 *
 * **纯函数，不碰 canvas、不碰 three。** 于是"北在哪边、东在哪边、比例对不对"
 * 这些最容易画反的东西都能在无 GPU 环境里断言 —— 画反了在真机上只表现为
 * "感觉怪怪的"，很难一眼看出。
 *
 * 坐标系约定（与 `player/motor.ts` 共用）：
 *
 * - 世界 **+X 向右（东）**、**+Z 向下（南）**、**-Z 向上（北）**；
 * - 角度 0 表示朝 +Z，方向向量为 `(sin a, cos a)`；
 * - 世界坐标 → 地图像素是**等比平移**，不做任何翻转：
 *   `mapX = center + x * scale`、`mapY = center + z * scale`。
 *
 * 因为方向向量在两个坐标系里形式相同（都是 `(sin, cos)`），
 * 朝向与视野扇形不需要任何角度补偿 —— 少一处补偿就少一处画反的机会。
 */

export type MapPoint = { x: number; y: number };

export type MinimapProjection = {
  /** 画布边长（CSS 像素） */
  size: number;
  /** 每世界单位对应多少像素 */
  scale: number;
  /** 世界坐标 → 画布像素 */
  toMap(worldX: number, worldZ: number): MapPoint;
  /** 世界长度 → 画布像素 */
  toPixels(worldLength: number): number;
};

/**
 * 建立投影。
 *
 * `marginRatio` 是**总留白比例**：0.12 表示边长上总共留 12% 的边距（每边 6%）。
 * 留一点边距，广场的四条边与压边石才不会正好压在圆角上。
 */
export function createMinimapProjection(
  size: number,
  groundSize: number,
  marginRatio = 0.12,
): MinimapProjection {
  const span = Math.max(1e-6, groundSize * (1 + marginRatio));
  const scale = size / span;
  const center = size / 2;

  return {
    size,
    scale,
    toMap(worldX: number, worldZ: number): MapPoint {
      return { x: center + worldX * scale, y: center + worldZ * scale };
    },
    toPixels(worldLength: number): number {
      return worldLength * scale;
    },
  };
}

/** 角度 → 地图上的单位方向向量。世界与地图同形，因此不需要翻转。 */
export function mapDirection(angle: number): MapPoint {
  return { x: Math.sin(angle), y: Math.cos(angle) };
}

/**
 * 视野扇形（相机在地面上"看"的范围）。
 *
 * 做成扇形而不是一条线，是因为一条线传达不出"视野宽度"，
 * 而扇形能把"镜头正对着哪片地面"直接画出来 —— 这正是小地图最有用的信息。
 */
export function viewWedgePolygon(
  origin: MapPoint,
  azimuth: number,
  halfAngle: number,
  radius: number,
  segments = 14,
): MapPoint[] {
  const points: MapPoint[] = [origin];
  for (let index = 0; index <= segments; index += 1) {
    const angle = azimuth - halfAngle + (halfAngle * 2 * index) / segments;
    const direction = mapDirection(angle);
    points.push({
      x: origin.x + direction.x * radius,
      y: origin.y + direction.y * radius,
    });
  }
  return points;
}

/** 三角形（人物朝向箭头）的三个顶点。`size` 是箭头长度（像素）。 */
export function arrowPolygon(origin: MapPoint, heading: number, size: number): MapPoint[] {
  const forward = mapDirection(heading);
  // 左法线：把前向量转 -90°，得到箭头的两个底角方向
  const left = { x: forward.y, y: -forward.x };
  const back = { x: origin.x - forward.x * size * 0.55, y: origin.y - forward.y * size * 0.55 };
  const halfWidth = size * 0.5;

  return [
    { x: origin.x + forward.x * size, y: origin.y + forward.y * size },
    { x: back.x + left.x * halfWidth, y: back.y + left.y * halfWidth },
    { x: back.x - left.x * halfWidth, y: back.y - left.y * halfWidth },
  ];
}
