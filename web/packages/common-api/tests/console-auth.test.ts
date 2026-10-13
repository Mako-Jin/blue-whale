import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { HostProps } from 'common-tools/micro';

/*
 * 本文件只测 `createConsoleAuth` 的**分支**，不测登录本身（那是 `auth.test.ts` 的射程）。
 * 因此把两处外部世界换掉：
 *   - `./channel`：真实实现会发 HTTP，换成一个能记录的替身；
 *   - `common-tools/micro` 的 `getHostProps`：用它来分别扮演"被基座加载"与"独立运行"。
 * 其余（会话存储、会话订阅）都用真货 —— 它们不需要浏览器。
 */
const hostProps = vi.hoisted(() => ({ value: undefined as HostProps | undefined }));

const channelSpies = vi.hoisted(() => ({
  login: vi.fn(async () => ({ username: 'admin' })),
  restore: vi.fn(async () => null),
}));

vi.mock('common-tools/micro', async (importOriginal) => {
  const actual = await importOriginal<typeof import('common-tools/micro')>();
  return { ...actual, getHostProps: () => hostProps.value };
});

vi.mock('../src/channel', async () => {
  const { createMemoryStorage, createSessionStore } = await import('common-tools/session');
  return {
    createAuthChannel: () => ({
      session: createSessionStore({ logger: null, storage: createMemoryStorage() }),
      login: channelSpies.login,
      restore: channelSpies.restore,
    }),
  };
});

const { createConsoleAuth } = await import('../src/console-auth');

const DEV_ACCOUNT = { tenantCode: 'default', username: 'admin', password: 'dev-only' };

beforeEach(() => {
  hostProps.value = undefined;
  channelSpies.login.mockClear();
  channelSpies.restore.mockClear();
});

describe('createConsoleAuth · 开发账号（ADR-0033）', () => {
  it('不传 devAccount 就是不自动登录：既不登录、也不抛（生产构建被独立部署的情形）', async () => {
    const auth = createConsoleAuth({ name: 'test' });

    await expect(auth.ensureSession()).resolves.toBeUndefined();

    expect(channelSpies.login).not.toHaveBeenCalled();
    // 自建会话仍然建出来了（"没有会话"只影响需要鉴权的面板，不该连带炸掉整应用）
    expect(auth.getStandaloneSession()).not.toBeNull();
  });

  it('传了 devAccount 才登录，且原样把账号交给通道', async () => {
    const auth = createConsoleAuth({ name: 'test', devAccount: DEV_ACCOUNT });

    await auth.ensureSession();

    expect(channelSpies.login).toHaveBeenCalledTimes(1);
    expect(channelSpies.login).toHaveBeenCalledWith(DEV_ACCOUNT);
  });

  it('被基座加载时不碰自建会话：独立运行身份为 null，也就没有第二套会话', async () => {
    hostProps.value = {
      auth: { getAccessToken: () => 'host-token', reauthenticate: async () => true },
    };
    const auth = createConsoleAuth({ name: 'test', devAccount: DEV_ACCOUNT });

    await auth.ensureSession();

    expect(channelSpies.login).not.toHaveBeenCalled();
    expect(auth.getStandaloneSession()).toBeNull();
    expect(auth.readStandaloneIdentity()).toBeNull();
    expect(auth.port.getAccessToken()).toBe('host-token');
  });
});
