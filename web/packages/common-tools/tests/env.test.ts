import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  DEBUG_HOSTNAMES,
  configureProductionHostnames,
  getCurrentHostname,
  getEnvMode,
  getProductionHostnames,
  getRuntimeEnv,
  isBrowser,
  isDebugMode,
  isDev,
  isNode,
  isProd,
  isProductionHost,
  resolveDefaultLevel,
} from '../src/env';

const ORIGINAL_URL = 'http://localhost:3000/';

function goto(search: string): void {
  window.history.replaceState({}, '', `${ORIGINAL_URL.replace(/\/$/, '')}/${search}`);
}

beforeEach(() => {
  configureProductionHostnames([]);
  goto('');
});

afterEach(() => {
  configureProductionHostnames([]);
  goto('');
});

describe('运行环境探测', () => {
  it('jsdom 下识别为浏览器', () => {
    expect(getRuntimeEnv()).toBe('browser');
    expect(isBrowser()).toBe(true);
    expect(isNode()).toBe(false);
  });

  it('hostname 可读取', () => {
    expect(getCurrentHostname()).toBe('localhost');
  });

  it('DEBUG_HOSTNAMES 含本地回环地址', () => {
    expect(DEBUG_HOSTNAMES).toContain('localhost');
    expect(DEBUG_HOSTNAMES).toContain('127.0.0.1');
  });
});

describe('构建模式', () => {
  it('读取 vitest 注入的 MODE', () => {
    expect(getEnvMode()).toBe('test');
    expect(isDev()).toBe(false);
    expect(isProd()).toBe(false);
  });
});

describe('生产域名规则', () => {
  it('默认不预设任何业务域名（旧实现声明的默认值实际从未生效）', () => {
    expect(getProductionHostnames()).toEqual([]);
    expect(isProductionHost()).toBe(false);
  });

  it('精确匹配', () => {
    configureProductionHostnames(['localhost']);
    expect(isProductionHost()).toBe(true);
  });

  it('通配符匹配子域名', () => {
    configureProductionHostnames(['*.example.com']);
    expect(isProductionHost('a.example.com')).toBe(true);
    expect(isProductionHost('example.com')).toBe(false);
  });

  it('正则匹配（注意：\\.example\\.com$ 本身不匹配裸域名）', () => {
    // 参考项目注释里写的是"匹配 example.com 及其子域名"，但正则实际要求前面有个点，
    // 所以裸域名不命中。这里按真实语义断言，避免把错误注释固化下来。
    configureProductionHostnames([/\.example\.com$/]);
    expect(isProductionHost('example.com')).toBe(false);
    expect(isProductionHost('a.example.com')).toBe(true);
    expect(isProductionHost('example.org')).toBe(false);
  });

  it('要同时匹配裸域名与子域名时，写成 ^(.*\\.)?example\\.com$', () => {
    configureProductionHostnames([/^(.*\.)?example\.com$/]);
    expect(isProductionHost('example.com')).toBe(true);
    expect(isProductionHost('a.example.com')).toBe(true);
    expect(isProductionHost('notexample.com')).toBe(false);
  });
});

describe('调试模式', () => {
  it('test 构建下默认关闭', () => {
    expect(isDebugMode()).toBe(false);
    expect(resolveDefaultLevel()).toBe('info');
  });

  it('?mode=debug 可开启', () => {
    goto('?mode=debug');
    expect(isDebugMode()).toBe(true);
    expect(resolveDefaultLevel()).toBe('debug');
  });

  it('命中生产域名规则时 ?mode=debug 失效', () => {
    configureProductionHostnames(['localhost']);
    goto('?mode=debug');
    expect(isDebugMode()).toBe(false);
  });

  it('未配置域名规则时 ?mode=debug 正常生效', () => {
    goto('?mode=debug');
    expect(isDebugMode()).toBe(true);
  });
});
