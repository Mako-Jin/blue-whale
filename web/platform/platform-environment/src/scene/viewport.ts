/**
 * 视口计算。
 *
 * 单独抽成纯函数是有原因的：**这几行里的每个坑都只在真实运行时才会以
 * "画面糊 / 画面变形 / 投影矩阵变 NaN"的形式出现**，抽出来就能用单元测试钉住。
 */

export type Viewport = {
  /** CSS 像素宽（整数） */
  width: number;
  /** CSS 像素高（整数） */
  height: number;
  /** 实际用于绘制的像素比，已按上限收敛 */
  pixelRatio: number;
};

/** 设备像素比上限。4K + 200% 缩放的屏幕能到 3，按原值渲染等于白白多画一倍像素。 */
export const DEFAULT_MAX_PIXEL_RATIO = 2;

/**
 * 由容器尺寸算出 three.js 需要的视口参数。
 *
 * 三条硬约束：
 *
 * 1. **不能为 0。** 容器在布局尚未完成（或被 `display: none` 隐藏）时尺寸是 0，
 *    而 three.js 会用宽高算投影矩阵，除以 0 之后矩阵全是 `NaN`，之后即使尺寸恢复也
 *    恢复不过来 —— 表现为整块画面再也渲染不出来。
 * 2. **必须是整数。** 小数会让 canvas 的 backing store 尺寸被浏览器取整，
 *    与 `setSize` 记下的值不一致，画面轻微糊。
 * 3. **像素比要有上限。** 高 DPR 屏幕上不收敛会直接把帧率打下去。
 */
export function resolveViewport(
  rect: { width: number; height: number },
  devicePixelRatio: number,
  maxPixelRatio: number = DEFAULT_MAX_PIXEL_RATIO,
): Viewport {
  const width = Math.max(1, Math.floor(rect.width));
  const height = Math.max(1, Math.floor(rect.height));
  // devicePixelRatio 在部分环境（SSR / 测试替身 / 某些嵌入 WebView）是 undefined 或 NaN
  const ratio = Number.isFinite(devicePixelRatio) ? devicePixelRatio : 1;

  return {
    width,
    height,
    pixelRatio: Math.min(Math.max(ratio, 1), maxPixelRatio),
  };
}
