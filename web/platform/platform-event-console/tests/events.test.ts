import { beforeEach, describe, expect, it } from 'vitest';

import {
  blockEvent,
  clearLocalEvents,
  fireEvent,
  getLocalEvents,
  login,
  logout,
  tracker,
  unblockEvent,
  viewProfile,
} from '../src/events';

/** 模块级状态会跨用例残留，每个用例前把身份与屏蔽名单复位 */
beforeEach(() => {
  clearLocalEvents();
  tracker.clearUser();
  for (const name of ['page.view', 'search.query', 'cart.add']) unblockEvent(name);
});

function latest() {
  return getLocalEvents()[0];
}

describe('埋点接线 · 事件从 track 到本地缓冲', () => {
  it('fireEvent 产生事件并进入本地缓冲', () => {
    const event = fireEvent('order.submit');

    expect(event?.eventName).toBe('order.submit');
    expect(latest()?.eventId).toBe(event?.eventId);
  });

  it('未登录时事件是匿名的，但仍带 deviceId（身份缝合的前提）', () => {
    const event = fireEvent('page.view');

    expect(event?.userId).toBeUndefined();
    expect(event?.deviceId).toBeTruthy();
    expect(event?.sessionId).toBeTruthy();
  });

  it('未登记在目录里的事件名也能发（track 不依赖目录）', () => {
    const event = tracker.track('custom.happened', { a: 1 });

    expect(event?.eventName).toBe('custom.happened');
    expect(event?.props).toEqual({ a: 1 });
  });

  it('事件名非法时返回 null 且不抛异常', () => {
    expect(() => tracker.track('Order.Submit')).not.toThrow();
    expect(tracker.track('Order.Submit')).toBeNull();
  });
});

describe('埋点接线 · 屏蔽中间件', () => {
  it('命中屏蔽名单的事件在进入缓冲之前就被丢掉', () => {
    blockEvent('page.view');

    const event = fireEvent('page.view');

    expect(event).toBeNull();
    expect(getLocalEvents()).toHaveLength(0);
  });

  it('只屏蔽名单里的事件，其余照常通过', () => {
    blockEvent('page.view');

    fireEvent('cart.add');

    expect(getLocalEvents()).toHaveLength(1);
    expect(latest()?.eventName).toBe('cart.add');
  });

  it('取消屏蔽后恢复', () => {
    blockEvent('cart.add');
    expect(fireEvent('cart.add')).toBeNull();

    unblockEvent('cart.add');
    expect(fireEvent('cart.add')).not.toBeNull();
  });
});

describe('埋点接线 · 脱敏中间件', () => {
  it('phone / email 在离开浏览器之前就被打码', () => {
    viewProfile();

    const props = latest()?.props ?? {};

    expect(String(props.phone)).toBe('138******00');
    expect(String(props.email)).not.toBe('captain@bluewhale.dev');
    // 未在脱敏名单里的字段原样保留 —— 脱敏不该连带毁掉分析用的属性
    expect(props.plan).toBe('pro');
  });

  it('打码后的值仍然可读，不是简单抹成星号', () => {
    viewProfile();

    const masked = String(latest()?.props?.phone);
    expect(masked.startsWith('138')).toBe(true);
    expect(masked.endsWith('00')).toBe(true);
    expect(masked).toContain('*');
  });
});

describe('埋点接线 · 登录与身份缝合', () => {
  it('login 先写上下文再发事件，因此登录事件自带 userId', () => {
    login('u-1');

    const event = latest();
    expect(event?.eventName).toBe('user.login');
    expect(event?.userId).toBe('u-1');
  });

  it('登录后新产生的事件带上 userId，deviceId 不变', () => {
    const before = fireEvent('page.view');
    login('u-1');
    const after = fireEvent('page.view');

    expect(after?.userId).toBe('u-1');
    // 同一个 deviceId 是"把匿名期与登录期并成同一个人"的唯一线索
    expect(after?.deviceId).toBe(before?.deviceId);
  });

  it('logout 先发事件再清身份，登出事件仍记在原用户名下', () => {
    login('u-1');
    logout();

    const logoutEvent = latest();
    expect(logoutEvent?.eventName).toBe('user.logout');
    expect(logoutEvent?.userId).toBe('u-1');

    // 清身份之后产生的事件回到匿名
    expect(fireEvent('page.view')?.userId).toBeUndefined();
  });
});
