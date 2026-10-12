import { useState } from 'react';
import type { ReactNode } from 'react';
import type { AuditLog } from 'common-api';

import './App.less';
import { usePermission, usePermissionState } from './auth/hooks';
import { DrillPanel } from './audit/DrillPanel';
import { RESULT_OPTIONS, TIME_WINDOWS } from './audit/constants';
import { useAuditQueries } from './audit/use-audit-queries';
import { Dialog } from './components/Dialog';
import { formatDuration, formatResult, formatTime, orDash, resultModifier } from './format';
import { PERM } from './permission-codes';

/**
 * 审计日志台。
 *
 * <h3>它和另外两个控制台（日志台 / 事件台）刻意长得不一样</h3>
 *
 * 那两个是"前端产生 → 上报 → 服务端落库"，因此都做成了左右分栏，
 * 让"客户端视角"与"服务端视角"并排对照 —— 只看一边无法判断上报成没成。
 * 审计**不由前端产生**：记录是后端拦截器在别人的请求上写的，
 * 本页对审计表**只有读**。所以这里没有左右分栏，只有三块自上而下的东西：
 *
 * 1. 流水（谁在何时对什么做了什么、成没成）；
 * 2. 按动作聚合（这段时间里哪类操作最多、失败集中在哪）；
 * 3. 演练区（真实写一次，再看它有没有被记下来）。
 *
 * 分栏要对照的是"两边是否一致"，而审计没有"两边" —— 硬套过来只会让人以为
 * 这里也能上报。**形态跟着管道产生方式走**，这才是"三条并列管道"该有的样子。
 */
export default function App() {
  const permission = usePermissionState();
  const canRead = usePermission(PERM.READ);

  // 没有审计读权限就不发请求：这时候必然失败，只会白留一屏错误
  const queries = useAuditQueries(permission.ready && canRead);

  const [detail, setDetail] = useState<AuditLog | null>(null);

  const records = queries.page?.records ?? [];
  const actions = queries.stats?.actions ?? [];
  const maxCount = actions.reduce((max, item) => Math.max(max, item.actionCount ?? 0), 0);

  return (
    <div className="app" data-testid="audit-app">
      <header className="topbar">
        <div className="topbar__brand">
          <span className="topbar__logo" aria-hidden="true" />
          <div>
            <h1 className="topbar__title">审计日志</h1>
            <p className="topbar__subtitle">
              platform-audit-console · 后端拦截器自动产生，前端只读
            </p>
          </div>
        </div>

        <div className="topbar__side">
          <p className="topbar__identity" data-testid="audit-identity">
            {permission.ready
              ? `userId=${permission.userId ?? '-'} · 角色 ${permission.roles.length} 个 · 权限 ${permission.permissions.length} 项`
              : '正在获取权限…'}
          </p>
          {permission.permissions.length > 0 ? (
            // 把权限标识原样列出来：截图与排查都要看"到底下发了哪些"
            <details className="topbar__perms">
              <summary>权限集合</summary>
              <ul className="topbar__perms-list" data-testid="audit-permissions">
                {permission.permissions.map((code) => (
                  <li key={code} className="mono">
                    {code}
                  </li>
                ))}
              </ul>
            </details>
          ) : null}
        </div>
      </header>

      <p className="callout" data-testid="audit-callout">
        <strong>本页没有「新增一条审计」的按钮，因为那个接口不存在。</strong>
        审计不可篡改不靠权限保护，而是靠后端不提供任何写入口（往 <code>/api/audit/logs</code> 发
        POST 会得到 40500）。本页只发三个 <code>GET</code>：流水、详情、按动作聚合。
      </p>

      {!permission.ready ? (
        <p className="banner" data-testid="audit-pending">
          正在获取权限…
        </p>
      ) : !canRead ? (
        <p className="banner banner--error" data-testid="audit-no-permission">
          当前账号没有 <code>{PERM.READ}</code> 权限，看不到审计流水。请换用管理员或审计员账号。
        </p>
      ) : (
        <>
          <section className="panel" data-testid="audit-list-panel">
            <header className="panel__head">
              <div>
                <h2 className="panel__title">审计流水</h2>
                <p className="panel__hint">
                  按服务端记录时间倒序。窗口{' '}
                  <span className="mono" data-testid="audit-window">
                    {queries.window
                      ? `${formatTime(queries.window.startTime)} ~ ${formatTime(queries.window.endTime)}`
                      : '正在计算…'}
                  </span>
                  —— 时间窗是这里的上下文，没有它"只有 3 条"分不清是没数据还是窗口太小。
                </p>
              </div>
              <div className="panel__actions">
                <button
                  type="button"
                  className="btn audit__refresh"
                  disabled={queries.loading}
                  onClick={queries.reload}
                >
                  {queries.loading ? '加载中…' : '刷新'}
                </button>
              </div>
            </header>

            <form className="toolbar" onSubmit={(event) => event.preventDefault()}>
              <input
                className="input"
                name="action"
                placeholder="动作，如 auth.tenant.create"
                value={queries.filters.action}
                onChange={(event) => queries.patchFilters({ action: event.target.value })}
              />
              <input
                className="input"
                name="resource"
                placeholder="资源，如 auth:tenant"
                value={queries.filters.resource}
                onChange={(event) => queries.patchFilters({ resource: event.target.value })}
              />
              <input
                className="input"
                name="operatorName"
                placeholder="操作人登录名"
                value={queries.filters.operatorName}
                onChange={(event) => queries.patchFilters({ operatorName: event.target.value })}
              />
              <input
                className="input"
                name="targetId"
                placeholder="操作对象主键"
                value={queries.filters.targetId}
                onChange={(event) => queries.patchFilters({ targetId: event.target.value })}
              />
              <select
                className="input"
                name="result"
                value={queries.filters.result}
                onChange={(event) =>
                  queries.patchFilters({
                    result: event.target.value as '' | 'SUCCESS' | 'FAILURE',
                  })
                }
              >
                {RESULT_OPTIONS.map((option) => (
                  <option key={option.label} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              <select
                className="input"
                name="window"
                value={queries.filters.window}
                onChange={(event) =>
                  queries.patchFilters({
                    // 取值来自 TIME_WINDOWS 常量，因此这里用 `as` 而不是再校验一遍
                    window: event.target.value as (typeof TIME_WINDOWS)[number]['key'],
                  })
                }
              >
                {TIME_WINDOWS.map((option) => (
                  <option key={option.key} value={option.key}>
                    {option.label}
                  </option>
                ))}
              </select>
              <button type="button" className="btn" onClick={queries.resetFilters}>
                重置
              </button>
              <span className="toolbar__spacer" />
              <span className="toolbar__meta" data-testid="audit-total">
                共 {queries.page?.total ?? 0} 条 · 第 {queries.pageNo} / {queries.page?.pages ?? 0}{' '}
                页
              </span>
            </form>

            {queries.error ? (
              <p className="banner banner--error" data-testid="audit-error">
                {queries.error}
              </p>
            ) : null}

            <div className="table-wrap">
              <table className="table" data-testid="audit-table">
                <thead>
                  <tr>
                    <th>记录时间</th>
                    <th>动作</th>
                    <th>资源</th>
                    <th>对象</th>
                    <th>操作人</th>
                    <th>结果</th>
                    <th>耗时</th>
                    <th>来源 IP</th>
                    <th className="table__ops">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {records.length === 0 ? (
                    <tr>
                      <td className="table__empty" colSpan={9}>
                        {queries.loading ? '加载中…' : '这段时间内没有审计记录'}
                      </td>
                    </tr>
                  ) : (
                    records.map((record) => (
                      <tr
                        key={String(record.id)}
                        className={record.result === 'FAILURE' ? 'table__row--fail' : undefined}
                        data-testid={`audit-row-${String(record.id)}`}
                      >
                        <td className="mono">{formatTime(record.serverTime)}</td>
                        <td className="mono">{orDash(record.action)}</td>
                        <td className="mono">{orDash(record.resource)}</td>
                        <td className="mono">{orDash(record.targetId)}</td>
                        <td>{orDash(record.operatorName)}</td>
                        <td>
                          <span
                            className={`tag tag--${resultModifier(record.result)}`}
                            data-testid={`audit-result-${String(record.id)}`}
                          >
                            {formatResult(record.result)}
                            {record.errorCode ? ` (${record.errorCode})` : ''}
                          </span>
                        </td>
                        <td className="mono">{formatDuration(record.durationMs)}</td>
                        <td className="mono">{orDash(record.ip)}</td>
                        <td className="table__ops">
                          <button
                            type="button"
                            className="btn audit__detail"
                            onClick={() => setDetail(record)}
                          >
                            详情
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            <div className="pager">
              <button
                type="button"
                className="btn"
                disabled={queries.pageNo <= 1}
                onClick={() => queries.setPageNo(Math.max(1, queries.pageNo - 1))}
              >
                上一页
              </button>
              <button
                type="button"
                className="btn"
                // 总页数由服务端算好（取整方式属业务约定），契约里它是可选字段，因此兜 0
                disabled={queries.pageNo >= (queries.page?.pages ?? 0)}
                onClick={() => queries.setPageNo(queries.pageNo + 1)}
              >
                下一页
              </button>
            </div>
          </section>

          <section className="panel" data-testid="audit-stats-panel">
            <header className="panel__head">
              <div>
                <h2 className="panel__title">按动作聚合</h2>
                <p className="panel__hint">
                  总 {queries.stats?.total ?? 0} 条，其中失败{' '}
                  <strong>{queries.stats?.failureTotal ?? 0}</strong> 条。这个接口刻意
                  <strong>不接受按动作筛选</strong> —— 它本身就是按动作分组的，
                  带了那个条件只会永远返回一条。
                </p>
              </div>
            </header>

            {actions.length === 0 ? (
              <p className="banner">这段时间内没有产生任何动作。</p>
            ) : (
              <ul className="stats" data-testid="audit-stats">
                {actions.map((item) => {
                  const count = item.actionCount ?? 0;
                  const failures = item.failureCount ?? 0;
                  const width = maxCount > 0 ? (count / maxCount) * 100 : 0;
                  const failWidth = count > 0 ? (failures / count) * 100 : 0;
                  return (
                    <li
                      key={String(item.action)}
                      className="stats__item"
                      data-testid={`audit-stat-${String(item.action)}`}
                    >
                      <button
                        type="button"
                        className="stats__action mono"
                        // 点动作即筛选：聚合视图给的是"哪类操作最多"，
                        // 接下来必然是"那些操作长什么样"，跳列表比让人手抄动作名好
                        onClick={() => queries.patchFilters({ action: String(item.action) })}
                      >
                        {item.action}
                      </button>
                      <span className="stats__bar" aria-hidden="true">
                        <span className="stats__bar-fill" style={{ width: `${String(width)}%` }}>
                          <span
                            className="stats__bar-fail"
                            style={{ width: `${String(failWidth)}%` }}
                          />
                        </span>
                      </span>
                      <span className="stats__count mono">
                        {count} 条{failures > 0 ? ` · 失败 ${failures}` : ''}
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>

          <DrillPanel onRecorded={queries.reload} />
        </>
      )}

      {detail ? (
        <Dialog title={`审计记录 #${String(detail.id)}`} onClose={() => setDetail(null)}>
          <dl className="facts" data-testid="audit-detail">
            <Fact label="动作">
              <span className="mono">{orDash(detail.action)}</span>
            </Fact>
            <Fact label="资源">
              <span className="mono">{orDash(detail.resource)}</span>
            </Fact>
            <Fact label="操作对象">{orDash(detail.targetId)}</Fact>
            <Fact label="操作人">
              {orDash(detail.operatorName)}
              {detail.operatorId ? ` (id=${detail.operatorId})` : ''}
            </Fact>
            <Fact label="租户">{orDash(detail.tenantId)}</Fact>
            <Fact label="结果">
              <span className={`tag tag--${resultModifier(detail.result)}`}>
                {formatResult(detail.result)}
              </span>
              {detail.errorCode ? ` 业务码 ${detail.errorCode}` : ''}
            </Fact>
            <Fact label="失败原因">{orDash(detail.errorMessage)}</Fact>
            <Fact label="请求">
              <span className="mono">
                {orDash(detail.httpMethod)} {orDash(detail.requestUri)}
              </span>
            </Fact>
            <Fact label="来源 IP">{orDash(detail.ip)}</Fact>
            <Fact label="User-Agent">{orDash(detail.userAgent)}</Fact>
            <Fact label="traceId">
              <span className="mono">{orDash(detail.traceId)}</span>
            </Fact>
            <Fact label="耗时">{formatDuration(detail.durationMs)}</Fact>
            <Fact label="记录时间">
              {/* 审计只有服务端时间：它不由客户端产生，没有"两个时间信哪个"的问题 */}
              {formatTime(detail.serverTime)}
            </Fact>
          </dl>
        </Dialog>
      ) : null}
    </div>
  );
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="facts__row">
      <dt className="facts__label">{label}</dt>
      <dd className="facts__value">{children}</dd>
    </div>
  );
}
