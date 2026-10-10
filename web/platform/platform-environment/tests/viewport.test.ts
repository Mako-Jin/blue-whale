import { describe, expect, it } from 'vitest';
import { DEFAULT_MAX_PIXEL_RATIO, resolveViewport } from '@/scene/viewport';

describe('resolveViewport', () => {
  it('由容器尺寸算出整数视口', () => {
    expect(resolveViewport({ width: 800.6, height: 600.2 }, 1)).toEqual({
      width: 800,
      height: 600,
      pixelRatio: 1,
    });
  });

  it('尺寸为 0 时收敛到 1 —— 否则投影矩阵会变成 NaN 且再也恢复不过来', () => {
    expect(resolveViewport({ width: 0, height: 0 }, 2)).toEqual({
      width: 1,
      height: 1,
      pixelRatio: 2,
    });
  });

  it('负的尺寸同样兜住', () => {
    expect(resolveViewport({ width: -120, height: 40 }, 1).width).toBe(1);
  });

  it('像素比按上限收敛，避免高 DPR 屏把帧率打下去', () => {
    expect(resolveViewport({ width: 100, height: 100 }, 3).pixelRatio).toBe(
      DEFAULT_MAX_PIXEL_RATIO,
    );
    expect(resolveViewport({ width: 100, height: 100 }, 3, 1.5).pixelRatio).toBe(1.5);
  });

  it('像素比不可用时退回 1', () => {
    expect(resolveViewport({ width: 100, height: 100 }, Number.NaN).pixelRatio).toBe(1);
    expect(resolveViewport({ width: 100, height: 100 }, Number.POSITIVE_INFINITY).pixelRatio).toBe(
      1,
    );
  });

  it('像素比不足 1 时抬到 1', () => {
    expect(resolveViewport({ width: 100, height: 100 }, 0.5).pixelRatio).toBe(1);
  });
});
