/**
 * 展示用的格式化。
 *
 * **刻意不用 `toLocaleString`**：它的输出取决于运行环境的 locale
 * （`1234.5` 在部分区域会变成 `1.234,5`），界面文案与测试断言都会跟着飘。
 * 这里要的是稳定的千分位，自己写三行更省事。
 */
export function withThousands(value: number): string {
  if (!Number.isFinite(value)) {
    return '—';
  }
  const rounded = Math.round(value);
  const sign = rounded < 0 ? '-' : '';
  return sign + String(Math.abs(rounded)).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

export function formatViewport(width: number, height: number, pixelRatio: number): string {
  return `${width}×${height} @${pixelRatio}x`;
}
