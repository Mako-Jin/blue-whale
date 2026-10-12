import { describe, expect, it } from 'vitest';

import type { HttpClient } from 'common-tools/http';

import { AuditActions, AuditPermissions, AuditResults, createAuditApi } from '../src/audit';
import type { AuditApi, AuditLog, AuditLogPage, AuditLogQuery, AuditStats } from '../src/audit';
import type { paths } from '../src/generated/schema';

/* ------------------------------------------------------------------ 桩客户端 */

interface Call {
  method: string;
  path: string;
  options: unknown;
}

/**
 * 一个只记录"打了哪个方法、哪条路径"的 HttpClient 桩。
 *
 * <p>本文件测的是**接线**，因此关心的是路径拼得对不对、参数有没有原样透传 ——
 * 而不是请求真的发出去之后怎么处理响应（那是 `common-tools/http` 的测试范围）。
 */
function createStub(): { http: HttpClient; calls: Call[] } {
  const calls: Call[] = [];
  const record = <T>(method: string, path: string, options: unknown): Promise<T> => {
    calls.push({ method, path, options });
    return Promise.resolve({} as T);
  };
  const http = {
    baseUrl: '',
    request: <T>(path: string, options?: unknown) => record<T>('REQUEST', path, options),
    get: <T>(path: string, options?: unknown) => record<T>('GET', path, options),
    post: <T>(path: string, body?: unknown, options?: unknown) =>
      record<T>('POST', path, { body, options }),
    put: <T>(path: string, body?: unknown, options?: unknown) =>
      record<T>('PUT', path, { body, options }),
    patch: <T>(path: string, body?: unknown, options?: unknown) =>
      record<T>('PATCH', path, { body, options }),
    delete: <T>(path: string, options?: unknown) => record<T>('DELETE', path, options),
  } as unknown as HttpClient;
  return { http, calls };
}

/* --------------------------------------------------------------- 编译期闸门 */

/**
 * 这三条路径必须真的存在于契约里。
 *
 * <p>写成"赋给 `Array<keyof paths>`"而不是 `satisfies`，是因为这里要的不是
 * "这个字面量是合法路径"，而是"这几条路径**都**是合法路径"——
 * 少一条、拼错一条都会在这里编译不过。
 */
const AUDIT_PATHS: Array<keyof paths> = [
  '/api/audit/logs',
  '/api/audit/logs/{id}',
  '/api/audit/logs/stats',
];

/** `[T] extends [never]` 的包裹写法是必需的：裸的 `never` 在条件类型里会分发成 `never` */
type IsNever<T> = [T] extends [never] ? true : false;

/** 审计记录主键在契约里必须是数字（后端是 `Long`），否则 `detail(id)` 的参数类型会静默放宽 */
type AuditIdType = paths['/api/audit/logs/{id}']['get']['parameters']['path']['id'];

const ID_IS_NUMBER: AuditIdType extends number ? true : false = true;

describe('审计域的契约接线', () => {
  it('三条路径都在契约里，且 id 参数是数字', () => {
    expect(AUDIT_PATHS).toHaveLength(3);
    expect(ID_IS_NUMBER).toBe(true);
  });

  it('分页把路径与查询条件原样交给传输层', async () => {
    const { http, calls } = createStub();
    const query: AuditLogQuery = {
      action: AuditActions.TENANT_CREATE,
      resource: 'auth:tenant',
      operatorName: 'admin',
      result: AuditResults.FAILURE,
      startTime: 1_700_000_000_000,
      endTime: 1_700_086_400_000,
      page: 2,
      size: 20,
    };

    await createAuditApi(http).page(query);

    expect(calls).toHaveLength(1);
    expect(calls[0]?.method).toBe('GET');
    expect(calls[0]?.path).toBe('/api/audit/logs');
    // 查询条件不在这里做任何加工：时间窗、结果值的解释权都在服务端
    expect(calls[0]?.options).toEqual({ query });
  });

  it('详情把主键拼进路径', async () => {
    const { http, calls } = createStub();

    await createAuditApi(http).detail(1024);

    expect(calls[0]?.path).toBe('/api/audit/logs/1024');
  });

  it('聚合走独立路径，且时间窗缺省时不下发任何条件', async () => {
    const { http, calls } = createStub();
    const api = createAuditApi(http);

    await api.stats({ resource: 'auth:user' });
    await api.stats();

    expect(calls[0]?.path).toBe('/api/audit/logs/stats');
    expect(calls[0]?.options).toEqual({ query: { resource: 'auth:user' } });
    expect(calls[1]?.options).toEqual({ query: undefined });
  });
});

describe('审计域的写入面', () => {
  it('接口上只有三个读方法 —— 一个写方法都没有', () => {
    const { http } = createStub();
    // 快照式断言：将来有人往 AuditApi 上加方法，这里会失败，
    // 逼他回答"审计记录该不该由前端补写"（答案是不该，见文件头）
    expect(Object.keys(createAuditApi(http)).sort()).toEqual(['detail', 'page', 'stats']);
  });

  it('契约里三条审计路径都没有写操作', () => {
    // 与上面那条互为表里：一个是前端没写方法，一个是后端没写接口。
    // 前者可以被人为绕开（直接拿 http.post），后者绕不开 —— 后端会回 40500。
    const logsPost: IsNever<
      NonNullable<paths['/api/audit/logs']['post' | 'put' | 'delete' | 'patch']>
    > = true;
    const detailPost: IsNever<
      NonNullable<paths['/api/audit/logs/{id}']['post' | 'put' | 'delete' | 'patch']>
    > = true;
    const statsPost: IsNever<
      NonNullable<paths['/api/audit/logs/stats']['post' | 'put' | 'delete' | 'patch']>
    > = true;

    expect([logsPost, detailPost, statsPost]).toEqual([true, true, true]);
  });

  it('AuditApi 不存在任何以写动作命名的方法', () => {
    const api = createAuditApi(createStub().http) as unknown as Record<string, unknown>;
    for (const name of ['create', 'report', 'update', 'remove', 'delete', 'save']) {
      expect(api[name]).toBeUndefined();
    }
  });
});

describe('审计的标识常量', () => {
  it('查询权限与后端逐字一致', () => {
    // 拼错不会报错，只会让控制台永远 40301，而排查方向会指向"权限没配"
    expect(AuditPermissions.READ).toBe('auth:audit:read');
  });

  it('结果取值为大写两态', () => {
    expect(AuditResults.SUCCESS).toBe('SUCCESS');
    expect(AuditResults.FAILURE).toBe('FAILURE');
  });

  it('动作标识是"域.资源.动作"三段点分，与资源标识（冒号）不同形', () => {
    expect(AuditActions.TENANT_CREATE).toBe('auth.tenant.create');
    expect(AuditActions.TENANT_DELETE).toBe('auth.tenant.delete');
    for (const action of Object.values(AuditActions)) {
      expect(action).toMatch(/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/);
      expect(action).not.toContain(':');
    }
  });
});

describe('审计的类型收紧', () => {
  it('result 被收紧为字面量联合，而不是任意字符串', () => {
    const log: AuditLog = { action: 'auth.user.create', result: 'SUCCESS' };
    const query: AuditLogQuery = { result: 'FAILURE' };
    expect(log.result).toBe('SUCCESS');
    expect(query.result).toBe('FAILURE');

    // @ts-expect-error 契约里是 string，收紧之后拼错的结果值必须在编译期被拒
    const wrong: AuditLog = { result: 'SUCCEED' };
    expect(wrong).toBeDefined();
  });

  it('分页里的记录与对外导出的 AuditLog 是同一个类型', () => {
    // 这一条挡的是"元素类型裂缝"：契约生成的 records 元素是 AuditLogVO，
    // 若不做替换，下面这个赋值会在编译期失败（而运行期其实一直是好的）
    const page: AuditLogPage = { records: [{ id: 1, action: 'auth.tenant.create' }], total: 1 };
    const first: AuditLog | undefined = page.records[0];
    expect(first?.id).toBe(1);
  });

  it('AuditApi 的入参与出参都出自契约（不在运行期触碰对象）', () => {
    const params: Parameters<AuditApi['page']>[0] = {
      result: 'SUCCESS',
      action: 'auth.tenant.create',
    };
    const pageShape: Awaited<ReturnType<AuditApi['page']>> extends { records: AuditLog[] }
      ? true
      : false = true;
    const statsShape: AuditStats extends Awaited<ReturnType<AuditApi['stats']>> ? true : false =
      true;

    expect(params).toEqual({ result: 'SUCCESS', action: 'auth.tenant.create' });
    expect([pageShape, statsShape]).toEqual([true, true]);
  });
});
