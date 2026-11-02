import { describe, expect, it } from 'vitest';
import {
  evaluateLinkage,
  isFieldDisabled,
  isFieldRequired,
  isFieldVisible,
  type LinkageCondition,
  type LinkageRule,
} from './linkage';

/** 构造单条件规则 */
function ruleOf(
  action: LinkageRule['action'],
  operator: LinkageCondition['operator'],
  value?: string | number,
  logic?: LinkageRule['logic'],
): LinkageRule {
  return {
    action,
    logic,
    conditions: [{ field: 'type', operator, value }],
  };
}

describe('evaluateLinkage', () => {
  it('无规则返回 false', () => {
    expect(evaluateLinkage(undefined, { type: 'A' })).toBe(false);
    expect(evaluateLinkage(null, { type: 'A' })).toBe(false);
  });

  it('conditions 非数组或为空时不成立', () => {
    expect(
      evaluateLinkage({ action: 'show', conditions: undefined } as unknown as LinkageRule, {
        type: 'A',
      }),
    ).toBe(false);
    expect(evaluateLinkage({ action: 'show', conditions: [] }, { type: 'A' })).toBe(false);
  });

  it('eq / ne 按字符串相等比较', () => {
    expect(evaluateLinkage(ruleOf('show', 'eq', 'A'), { type: 'A' })).toBe(true);
    expect(evaluateLinkage(ruleOf('show', 'eq', 'A'), { type: 'B' })).toBe(false);
    expect(evaluateLinkage(ruleOf('show', 'ne', 'A'), { type: 'B' })).toBe(true);
    expect(evaluateLinkage(ruleOf('show', 'ne', 'A'), { type: 'A' })).toBe(false);
    // 数值与字符串按文本比较
    expect(evaluateLinkage(ruleOf('show', 'eq', '1'), { type: 1 })).toBe(true);
  });

  it('in / notIn 按逗号分隔值集合判断', () => {
    expect(evaluateLinkage(ruleOf('show', 'in', 'A,B'), { type: 'B' })).toBe(true);
    expect(evaluateLinkage(ruleOf('show', 'in', ' A , B '), { type: 'A' })).toBe(true);
    expect(evaluateLinkage(ruleOf('show', 'in', 'A,B'), { type: 'C' })).toBe(false);
    expect(evaluateLinkage(ruleOf('show', 'notIn', 'A,B'), { type: 'C' })).toBe(true);
    expect(evaluateLinkage(ruleOf('show', 'notIn', 'A,B'), { type: 'A' })).toBe(false);
  });

  it('gt / lt 按数值比较，任一非数字不成立', () => {
    expect(evaluateLinkage(ruleOf('show', 'gt', '100'), { type: 101 })).toBe(true);
    expect(evaluateLinkage(ruleOf('show', 'gt', '100'), { type: '101' })).toBe(true);
    expect(evaluateLinkage(ruleOf('show', 'gt', '100'), { type: 100 })).toBe(false);
    expect(evaluateLinkage(ruleOf('show', 'lt', '100'), { type: 99 })).toBe(true);
    expect(evaluateLinkage(ruleOf('show', 'lt', '100'), { type: '99' })).toBe(true);
    expect(evaluateLinkage(ruleOf('show', 'gt', '100'), { type: 'abc' })).toBe(false);
    expect(evaluateLinkage(ruleOf('show', 'lt', 'abc'), { type: 1 })).toBe(false);
    expect(evaluateLinkage(ruleOf('show', 'gt', '100'), { type: '' })).toBe(false);
  });

  it('empty / notEmpty 忽略 value，覆盖 null/undefined/空串/空数组', () => {
    const emptyRule = ruleOf('show', 'empty', 'ignored');
    const notEmptyRule = ruleOf('show', 'notEmpty', 'ignored');
    [null, undefined, '', []].forEach((current) => {
      expect(evaluateLinkage(emptyRule, { type: current })).toBe(true);
      expect(evaluateLinkage(notEmptyRule, { type: current })).toBe(false);
    });
    ['A', 0, [1], {}, false].forEach((current) => {
      expect(evaluateLinkage(emptyRule, { type: current })).toBe(false);
      expect(evaluateLinkage(notEmptyRule, { type: current })).toBe(true);
    });
  });

  it('未知操作符不成立', () => {
    const rule = {
      action: 'show',
      conditions: [{ field: 'type', operator: 'contains', value: 'A' }],
    } as unknown as LinkageRule;
    expect(evaluateLinkage(rule, { type: 'A' })).toBe(false);
  });

  it('values 缺少条件字段时视为 undefined', () => {
    expect(evaluateLinkage(ruleOf('show', 'eq', 'A'), {})).toBe(false);
    expect(evaluateLinkage(ruleOf('show', 'empty'), {})).toBe(true);
    expect(evaluateLinkage(ruleOf('show', 'notEmpty'), {})).toBe(false);
  });

  it('logic 缺省为 and，or 为任一成立', () => {
    const conditions: LinkageCondition[] = [
      { field: 'type', operator: 'eq', value: 'A' },
      { field: 'status', operator: 'eq', value: '1' },
    ];
    expect(evaluateLinkage({ action: 'show', conditions }, { type: 'A', status: '0' })).toBe(false);
    expect(
      evaluateLinkage({ action: 'show', logic: 'and', conditions }, { type: 'A', status: '1' }),
    ).toBe(true);
    expect(
      evaluateLinkage({ action: 'show', logic: 'or', conditions }, { type: 'A', status: '0' }),
    ).toBe(true);
    expect(
      evaluateLinkage({ action: 'show', logic: 'or', conditions }, { type: 'B', status: '0' }),
    ).toBe(false);
  });
});

describe('isFieldVisible', () => {
  it('无规则恒可见', () => {
    expect(isFieldVisible({ key: 'amount' }, { type: 'A' })).toBe(true);
  });

  it('show：条件成立显示，否则隐藏', () => {
    const field = { key: 'amount', linkage: ruleOf('show', 'eq', 'A') };
    expect(isFieldVisible(field, { type: 'A' })).toBe(true);
    expect(isFieldVisible(field, { type: 'B' })).toBe(false);
  });

  it('hide：条件成立隐藏，否则显示', () => {
    const field = { key: 'amount', linkage: ruleOf('hide', 'eq', 'A') };
    expect(isFieldVisible(field, { type: 'A' })).toBe(false);
    expect(isFieldVisible(field, { type: 'B' })).toBe(true);
  });

  it('require / disable 不影响可见性', () => {
    expect(
      isFieldVisible({ key: 'amount', linkage: ruleOf('require', 'eq', 'A') }, { type: 'A' }),
    ).toBe(true);
    expect(
      isFieldVisible({ key: 'amount', linkage: ruleOf('disable', 'eq', 'A') }, { type: 'A' }),
    ).toBe(true);
  });
});

describe('isFieldDisabled', () => {
  it('disable：条件成立时禁用', () => {
    const field = { key: 'amount', linkage: ruleOf('disable', 'eq', 'A') };
    expect(isFieldDisabled(field, { type: 'A' })).toBe(true);
    expect(isFieldDisabled(field, { type: 'B' })).toBe(false);
  });

  it('无规则或其他 action 不禁用', () => {
    expect(isFieldDisabled({ key: 'amount' }, { type: 'A' })).toBe(false);
    expect(
      isFieldDisabled({ key: 'amount', linkage: ruleOf('show', 'eq', 'A') }, { type: 'A' }),
    ).toBe(false);
  });
});

describe('isFieldRequired', () => {
  it('require：条件成立时必填', () => {
    const field = { key: 'amount', linkage: ruleOf('require', 'eq', 'A') };
    expect(isFieldRequired(field, { type: 'A' })).toBe(true);
    expect(isFieldRequired(field, { type: 'B' })).toBe(false);
  });

  it('原有 required 与联动结果取或', () => {
    expect(isFieldRequired({ key: 'amount', required: true }, {})).toBe(true);
    expect(
      isFieldRequired(
        { key: 'amount', required: true, linkage: ruleOf('require', 'eq', 'A') },
        { type: 'B' },
      ),
    ).toBe(true);
    expect(isFieldRequired({ key: 'amount', required: false }, {})).toBe(false);
  });
});
