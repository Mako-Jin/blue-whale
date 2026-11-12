// 蓝鲸城 3D 子应用根组件
//
// P0 边界（对 2D 零侵入）：本应用是独立子应用（/world，端口 8124），不调用任何后端
// 接口、不改基座既有路由；传送门跳转 2D 子应用通过 window.history.pushState 走
// qiankun 的 activeRule 切换（qiankun 2.x 劫持 pushState 会触发 reroute）。
//
// HUD 覆盖层的 DOM 与事件由 world/city.ts 托管；本组件只负责结构与生命周期：
// 挂载时 createWorld(host)，卸载时 dispose（three 场景 / 事件 / raf 全量回收）。

import { useEffect, useRef } from 'react';
import { createWorld } from './world/city';

export default function App() {
  const hostRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) {
      return undefined;
    }
    const inQiankun = Boolean(
      (window as unknown as { __POWERED_BY_QIANKUN__?: boolean }).__POWERED_BY_QIANKUN__,
    );
    const world = createWorld(host, {
      poweredByQiankun: inQiankun,
      navigate: (route: string) => {
        window.history.pushState(null, '', route);
      },
    });
    return () => world.dispose();
  }, []);

  return (
    <div className="bww-page">
      <div ref={hostRef} className="bww-scene" id="bww-scene" />
      <div className="bww-vignette" />

      <div className="bww-labels" id="bww-labels" />

      <header className="bww-hud">
        <div className="bww-brand">
          <div className="bww-seal">鲸</div>
          <div>
            <h1>蓝鲸城</h1>
            <p>BlueWhale City · Digital Realm</p>
          </div>
        </div>
        <div className="bww-meta">
          <div id="bww-hud-time">—</div>
          <div id="bww-hud-date">—</div>
          <div className="bww-hint">皇城 · 内城 · 外城 · 八方宫署</div>
        </div>
      </header>

      <div className="bww-pet-bubble" id="bww-pet-bubble">
        <div className="bww-who">小鲸宝</div>
        <div className="bww-bubble-body" id="bww-pet-text">……</div>
      </div>

      <div className="bww-portal-hint" id="bww-portal-hint">
        按 <b>E</b> 打开传送门
      </div>

      <div className="bww-help">
        <div>
          <kbd>W</kbd><kbd>A</kbd><kbd>S</kbd><kbd>D</kbd> 行走&ensp;<kbd>Shift</kbd> 疾行
        </div>
        <div>拖拽 转视角&ensp;滚轮 远近&ensp;<kbd>E</kbd> 传送&ensp;<kbd>R</kbd> 复位</div>
      </div>

      <div className="bww-toolbar">
        <button data-action="weather" type="button">天象：晴</button>
        <button data-action="season" type="button">时节：秋</button>
        <button data-action="speed" type="button">时辰：常速</button>
        <button data-action="portal" type="button">传送门 · E</button>
        <button data-action="reset" type="button">视角复位 · R</button>
      </div>

      <div className="bww-portal-panel" id="bww-portal-panel">
        <div className="bww-panel-inner">
          <div className="bww-panel-head">
            <h2>传 送 门</h2>
            <span>PORTAL NETWORK</span>
          </div>
          <div className="bww-panel-sub">
            点卡片城内瞬移 · 点「进入」前往对应 2D 宫殿 · 也可按数字键 1–9
          </div>
          <div className="bww-portal-list" id="bww-portal-list" />
        </div>
      </div>

      <div className="bww-toast" id="bww-toast">
        <div id="bww-toast-title" />
        <div id="bww-toast-sub" />
      </div>

      <div className="bww-fade" id="bww-fade" />

      <div className="bww-loading" id="bww-loading">
        <div className="bww-load-inner">
          <div className="bww-load-title">蓝鲸城</div>
          <div className="bww-load-sub">BlueWhale City</div>
          <div className="bww-load-line" />
        </div>
      </div>
    </div>
  );
}
