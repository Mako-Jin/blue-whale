import { describe, expect, it } from 'vitest';
import { formatViewport, withThousands } from '@/format';

describe('withThousands', () => {
  it('给整数加千分位', () => {
    expect(withThousands(1234)).toBe('1,234');
    expect(withThousands(1234567)).toBe('1,234,567');
  });

  it('三位以内不加分隔符', () => {
    expect(withThousands(0)).toBe('0');
    expect(withThousands(999)).toBe('999');
  });

  it('四舍五入到整数（三角面数量不需要小数）', () => {
    expect(withThousands(1234.56)).toBe('1,235');
  });

  it('负数保留符号', () => {
    expect(withThousands(-1234)).toBe('-1,234');
  });

  it('非有限数显示占位符而不是 NaN', () => {
    expect(withThousands(Number.NaN)).toBe('—');
    expect(withThousands(Number.POSITIVE_INFINITY)).toBe('—');
  });
});

describe('formatViewport', () => {
  it('把尺寸与像素比拼成一行', () => {
    expect(formatViewport(1280, 720, 2)).toBe('1280×720 @2x');
  });
});
