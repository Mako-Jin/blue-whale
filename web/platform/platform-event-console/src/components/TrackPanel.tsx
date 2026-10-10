import type { EventEnvelope } from 'common-tools/events';
import type { BatchChannelState } from 'common-tools/report';
import { useState } from 'react';

import { EVENT_CATALOG } from '../events';
import type { TrackerError } from '../events';

/** 可以被"屏蔽"的事件，用来观察中间件在通道之前把事件丢掉 */
const BLOCKABLE = ['page.view', 'search.query', 'cart.add'];

export interface TrackPanelProps {
  events: readonly EventEnvelope[];
  channelState: BatchChannelState;
  autoReport: boolean;
  userId: string | undefined;
  route: string;
  blocked: readonly string[];
  trackerErrors: readonly TrackerError[];
  onToggleAutoReport: (next: boolean) => void;
  onClear: () => void;
  onFire: (name: string) => void;
  onLogin: () => void;
  onLogout: () => void;
  onNavigate: (route: string) => void;
  onToggleBlock: (name: string) => void;
  onViewProfile: () => void;
}

function pad(value: number, width = 2): string {
  return String(value).padStart(width, '0');
}

function formatTime(ms: number): string {
  const date = new Date(ms);
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}`;
}

function summarizeProps(event: EventEnvelope): string {
  if (!event.props) return '—';
  const entries = Object.entries(event.props);
  if (entries.length === 0) return '{}';
  return entries.map(([key, value]) => `${key}=${String(value)}`).join(' ');
}

export function TrackPanel(props: TrackPanelProps) {
  const [routeInput, setRouteInput] = useState('/orders');

  const { channelState } = props;

  return (
    <section className="panel panel--client">
      <header className="panel__head">
        <h2 className="panel__title">客户端视角</h2>
        <p className="panel__hint">业务只调 track()，其余全在内核里</p>
      </header>

      {/* ---------------------------------------------------------- 上下文 */}
      <div className="block">
        <h3 className="block__title">身份与会话（模拟）</h3>
        <div className="row">
          <span className={`tag ${props.userId ? 'tag--on' : ''}`}>
            {props.userId ? `已登录 ${props.userId}` : '未登录'}
          </span>
          <button type="button" className="btn btn--sm" onClick={props.onLogin}>
            登录
          </button>
          <button
            type="button"
            className="btn btn--sm"
            onClick={props.onLogout}
            disabled={!props.userId}
          >
            登出
          </button>
        </div>
        <div className="row">
          <input
            className="input mono"
            value={routeInput}
            onChange={(event) => setRouteInput(event.target.value)}
            aria-label="路由"
          />
          <button
            type="button"
            className="btn btn--sm"
            onClick={() => props.onNavigate(routeInput)}
          >
            跳转并记 page.view
          </button>
        </div>
        <p className="block__note">
          登录前后的 <code className="mono">deviceId</code> 不变、
          <code className="mono">userId</code>
          出现 —— 这就是"身份缝合"的锚点：分析侧靠 deviceId 把匿名期与登录期并成同一个人。
        </p>
      </div>

      {/* ---------------------------------------------------------- 触发 */}
      <div className="block">
        <h3 className="block__title">触发语义化事件</h3>
        <div className="row row--wrap">
          {EVENT_CATALOG.map((definition) => (
            <button
              key={definition.name}
              type="button"
              className="btn btn--sm"
              title={definition.trigger}
              onClick={() => props.onFire(definition.name)}
            >
              {definition.label}
            </button>
          ))}
          <button type="button" className="btn btn--sm btn--accent" onClick={props.onViewProfile}>
            查看联系方式（脱敏演示）
          </button>
        </div>
      </div>

      {/* -------------------------------------------------------- 中间件 */}
      <div className="block">
        <h3 className="block__title">中间件：屏蔽</h3>
        <div className="row row--wrap">
          {BLOCKABLE.map((name) => {
            const on = props.blocked.includes(name);
            return (
              <button
                key={name}
                type="button"
                className={`chip mono ${on ? 'chip--on' : ''}`}
                onClick={() => props.onToggleBlock(name)}
                aria-pressed={on}
              >
                {name}
              </button>
            );
          })}
        </div>
        <p className="block__note">
          屏蔽命中即返回 null，事件**在进入缓冲之前**就没了：左栏不会出现、服务端更不会收到。
          与"上报之后再删"的区别是明文从未离开浏览器。
        </p>
      </div>

      {/* ------------------------------------------------------- 上报状态 */}
      <div className="block">
        <h3 className="block__title">
          上报通道
          <label className="switch">
            <input
              type="checkbox"
              checked={props.autoReport}
              onChange={(event) => props.onToggleAutoReport(event.target.checked)}
            />
            <span>{props.autoReport ? '自动上报中' : '已暂停'}</span>
          </label>
        </h3>
        <div className="row row--wrap">
          <span className="pill" title="待上报条数">
            待发 {channelState.pending}
          </span>
          <span className="pill pill--ok" title="后端确认接收">
            已收 {channelState.sent}
          </span>
          <span className="pill pill--err" title="后端确认拒收">
            拒收 {channelState.rejected}
          </span>
          <span className="pill" title="整批失败的次数">
            失败批 {channelState.failedBatches}
          </span>
          <span className="pill" title="因缓冲上限被丢弃">
            丢弃 {channelState.dropped}
          </span>
          {channelState.backoffMs > 0 ? (
            <span className="pill pill--warn" title="当前退避时长">
              退避 {(channelState.backoffMs / 1000).toFixed(1)}s
            </span>
          ) : null}
          <button type="button" className="btn btn--sm" onClick={props.onClear}>
            清空本地
          </button>
        </div>
        {channelState.lastError ? (
          <p className="block__note block__note--warn">最近失败：{channelState.lastError}</p>
        ) : null}
      </div>

      {/* ------------------------------------------------- 埋点自身异常 */}
      {props.trackerErrors.length > 0 ? (
        <div className="block">
          <h3 className="block__title">埋点自身异常（不抛给业务）</h3>
          <ul className="errors">
            {props.trackerErrors.slice(0, 5).map((error) => (
              <li key={`${error.at}-${error.stage}`} className="mono">
                [{error.stage}] {error.message}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {/* ---------------------------------------------------- 客户端事件流 */}
      <div className="block block--grow">
        <h3 className="block__title">
          本地事件流
          <span className="block__count">{props.events.length}</span>
        </h3>
        <ol className="events">
          {props.events.length === 0 ? (
            <li className="events__empty">还没有事件。点上面的按钮试试。</li>
          ) : (
            props.events.map((event) => (
              <li key={event.eventId} className="events__item">
                <div className="events__line">
                  <span className="mono events__time">{formatTime(event.clientTime)}</span>
                  <span className="mono events__name">{event.eventName}</span>
                  <span className="events__meta mono">
                    v{event.propsVersion ?? 1}
                    {event.userId ? ` · ${event.userId}` : ' · 匿名'}
                  </span>
                </div>
                <div className="mono events__props">{summarizeProps(event)}</div>
              </li>
            ))
          )}
        </ol>
      </div>
    </section>
  );
}
