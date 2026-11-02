// FormRenderer 值转换纯函数测试：布局字段判定 / 选项映射 / validateJson 扩展解析 / 内外值双向转换
//
// T-07 第二批（前端单测基线）：覆盖表单设计器与填报页共用的值语义，
// 断言安全关键行为（switch 1/0 与 boolean 互转、雪花 ID 不丢精度、显式清空同步）。

import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';
import type { WhaleFormFieldConfig } from './types';
import {
  isLayoutWidget,
  resolveFieldExtras,
  toExternalValues,
  toInternalValues,
  toOptions,
} from './values';

/** 构造最小字段配置 */
function field(
  partial: Partial<WhaleFormFieldConfig> & { fieldLabel: string },
): WhaleFormFieldConfig {
  return partial;
}

describe('isLayoutWidget', () => {
  it('title / divider / text-note 为纯布局控件', () => {
    expect(isLayoutWidget('title')).toBe(true);
    expect(isLayoutWidget('divider')).toBe(true);
    expect(isLayoutWidget('text-note')).toBe(true);
  });

  it('数据控件与未声明控件不是布局字段', () => {
    expect(isLayoutWidget('text')).toBe(false);
    expect(isLayoutWidget('attachment')).toBe(false);
    expect(isLayoutWidget(undefined)).toBe(false);
  });
});

describe('toOptions', () => {
  it('无选项返回空数组', () => {
    expect(toOptions(field({ fieldLabel: '类型' }))).toEqual([]);
  });

  it('按声明顺序映射 label/value', () => {
    expect(
      toOptions(
        field({
          fieldLabel: '类型',
          options: [
            { optionLabel: '公开', optionValue: 'public', sortNo: 1 },
            { optionLabel: '自听', optionValue: 'private', sortNo: 2 },
          ],
        }),
      ),
    ).toEqual([
      { label: '公开', value: 'public' },
      { label: '自听', value: 'private' },
    ]);
  });
});

describe('resolveFieldExtras', () => {
  it('无 validateJson 时原样返回同一对象', () => {
    const original = field({ fieldLabel: '附件' });
    expect(resolveFieldExtras(original)).toBe(original);
  });

  it('非法 JSON / 非对象 / 数组均原样返回', () => {
    const broken = field({ fieldLabel: '附件', validateJson: '{bad json' });
    expect(resolveFieldExtras(broken)).toBe(broken);
    const scalar = field({ fieldLabel: '附件', validateJson: '123' });
    expect(resolveFieldExtras(scalar)).toBe(scalar);
    const array = field({ fieldLabel: '附件', validateJson: '[1,2]' });
    expect(resolveFieldExtras(array)).toBe(array);
  });

  it('从 validateJson 合并附件与子表扩展配置', () => {
    const merged = resolveFieldExtras(
      field({
        fieldLabel: '附件',
        widgetType: 'attachment',
        validateJson: JSON.stringify({
          multiple: true,
          accept: '.pdf,.docx',
          maxCount: 5,
          minRows: 1,
          maxRows: 10,
          children: [{ key: 'name', label: '名称', type: 'text' }],
        }),
      }),
    );
    expect(merged.multiple).toBe(true);
    expect(merged.accept).toBe('.pdf,.docx');
    expect(merged.maxCount).toBe(5);
    expect(merged.minRows).toBe(1);
    expect(merged.maxRows).toBe(10);
    expect(merged.children).toHaveLength(1);
  });

  it('字段自身声明的值优先于 validateJson', () => {
    const merged = resolveFieldExtras(
      field({
        fieldLabel: '附件',
        multiple: false,
        maxCount: 2,
        validateJson: JSON.stringify({ multiple: true, maxCount: 9 }),
      }),
    );
    expect(merged.multiple).toBe(false);
    expect(merged.maxCount).toBe(2);
  });
});

describe('toInternalValues', () => {
  const switchField = field({ fieldLabel: '开关', columnCode: 'enabled', widgetType: 'switch' });
  const dateField = field({ fieldLabel: '日期', columnCode: 'day', widgetType: 'date' });
  const checkboxField = field({ fieldLabel: '标签', columnCode: 'tags', widgetType: 'checkbox' });
  const attachmentField = field({
    fieldLabel: '附件',
    columnCode: 'files',
    widgetType: 'attachment',
  });
  const layoutField = field({ fieldLabel: '标题', columnCode: 'title', widgetType: 'title' });

  it('switch 的 1 / "1" / true 统一转 boolean', () => {
    expect(toInternalValues([switchField], { enabled: 1 }).enabled).toBe(true);
    expect(toInternalValues([switchField], { enabled: '1' }).enabled).toBe(true);
    expect(toInternalValues([switchField], { enabled: true }).enabled).toBe(true);
    expect(toInternalValues([switchField], { enabled: 0 }).enabled).toBe(false);
    expect(toInternalValues([switchField], { enabled: '0' }).enabled).toBe(false);
  });

  it('日期字符串转 dayjs 对象', () => {
    const internal = toInternalValues([dateField], { day: '2026-10-31' });
    expect(dayjs.isDayjs(internal.day)).toBe(true);
    expect((internal.day as dayjs.Dayjs).format('YYYY-MM-DD')).toBe('2026-10-31');
  });

  it('checkbox 字符串按逗号拆分为数组', () => {
    expect(toInternalValues([checkboxField], { tags: 'a,b' }).tags).toEqual(['a', 'b']);
    expect(toInternalValues([checkboxField], { tags: '' }).tags).toEqual([]);
  });

  it('附件 ID 统一转字符串，字符串形态的雪花 ID 原样保留', () => {
    const internal = toInternalValues([attachmentField], {
      files: [123, '9007199254740993', 'abc'],
    });
    expect(internal.files).toEqual(['123', '9007199254740993', 'abc']);
  });

  it('空值显式置 undefined，保证重置时同步清空', () => {
    const internal = toInternalValues([switchField, dateField], { enabled: null });
    expect(internal.enabled).toBeUndefined();
    expect(internal.day).toBeUndefined();
  });

  it('布局字段与缺少 columnCode 的字段不参与转换', () => {
    expect(toInternalValues([layoutField], { title: '不该出现' })).toEqual({});
    expect(toInternalValues([field({ fieldLabel: '无列' })], { any: 1 })).toEqual({});
  });
});

describe('toExternalValues', () => {
  const switchField = field({ fieldLabel: '开关', columnCode: 'enabled', widgetType: 'switch' });
  const textField = field({ fieldLabel: '名称', columnCode: 'name', widgetType: 'text' });

  it('switch 的 boolean 转回 1/0', () => {
    expect(toExternalValues([switchField], { enabled: true }).enabled).toBe(1);
    expect(toExternalValues([switchField], { enabled: false }).enabled).toBe(0);
  });

  it('非 switch 字段与空值保持原样', () => {
    expect(toExternalValues([textField], { name: '歌单' }).name).toBe('歌单');
    expect(toExternalValues([switchField], { enabled: undefined }).enabled).toBeUndefined();
    expect(toExternalValues([switchField], {}).enabled).toBeUndefined();
  });

  it('不修改传入对象（返回浅拷贝）', () => {
    const source: Record<string, unknown> = { enabled: true };
    const external = toExternalValues([switchField], source);
    expect(external.enabled).toBe(1);
    expect(source.enabled).toBe(true);
  });
});
