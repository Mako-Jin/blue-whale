import { useCallback, useEffect, useState } from 'react';
import { getHostGlobalState, isMicroApp, onHostGlobalStateChange } from 'common-tools/micro';
import type { HostGlobalState } from 'common-tools/micro';
import ControlPanel from '@/components/ControlPanel';
import SceneSwitcher from '@/components/SceneSwitcher';
import SceneView from '@/components/SceneView';
import {
  defaultScene,
  findScene,
  loadSceneCatalog,
  localSceneCatalog,
  sceneOptions,
  type SceneCatalog,
  type SceneDefinition,
  type SceneId,
  type SceneOptions,
  type StageStats,
} from '@/scene';
import type { MountMode } from '@/mount';
import './App.less';

type AppProps = {
  /** 挂载点来源，由 `main.tsx` 解析后传下来（基座容器 / 自建容器） */
  mode: MountMode;
};

/**
 * WebGL 不可用时的降级面板。
 *
 * 三维应用最容易犯的错是"渲染不出来就白屏"—— 用户不知道是坏了还是在加载。
 * 这里把原因摆出来，并且明确**其余功能不受影响**。
 */
function WebGLFallback({ message }: { message: string }) {
  return (
    <div className="env-app__fallback" role="alert">
      <h2 className="env-app__fallback-title">无法创建 WebGL 上下文</h2>
      <p className="env-app__fallback-text">
        当前浏览器或运行环境不提供可用的
        WebGL，三维场景无法渲染。应用本身运行正常，其余功能不受影响。
      </p>
      <ul className="env-app__fallback-list">
        <li>确认浏览器未关闭「使用硬件加速」</li>
        <li>无头浏览器需要开启软件渲染（如 Chrome 的 --use-gl=swiftshader）</li>
      </ul>
      <p className="env-app__fallback-detail mono">{message}</p>
    </div>
  );
}

export default function App({ mode }: AppProps) {
  /**
   * 首屏用**同步**的内置清单起步，异步加载的结果到了再替换。
   *
   * 这是 ADR-0003 客户端底线第 1 条的落地：不允许首屏等配置接口。
   * 今天 `loadSceneCatalog()` 返回的就是这份内置清单，看着像多此一举 ——
   * 但等它真接了后端，这段代码一个字都不用改，也不会出现"白屏等接口"。
   */
  const [catalog, setCatalog] = useState<SceneCatalog>(() => localSceneCatalog());
  const [activeScene, setActiveScene] = useState<SceneDefinition>(() => defaultScene());
  const [options, setOptions] = useState<SceneOptions>(() => sceneOptions(defaultScene().meta));
  const [stats, setStats] = useState<StageStats | null>(null);
  const [error, setError] = useState<string | null>(null);
  // 初值直接读一次，避免为了"首帧就有值"而在 effect 里同步 setState
  const [globalState, setGlobalState] = useState<HostGlobalState>(() => getHostGlobalState());

  useEffect(() => {
    let cancelled = false;
    void loadSceneCatalog().then((next) => {
      if (!cancelled) {
        setCatalog(next);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  /**
   * 切换场景。
   *
   * 换场景**同时换选项**：网格辅助线对广场铺装是有意义的参照，
   * 对丛林与太空则纯属干扰。沿用上一个场景的开关会让人以为
   * "这个场景的开关坏了"—— 每个场景在 `SceneMeta.options` 里声明自己的默认值。
   */
  const handleSelect = useCallback((id: SceneId) => {
    const definition = findScene(id);
    if (!definition) {
      return;
    }
    setActiveScene(definition);
    setOptions(sceneOptions(definition.meta));
  }, []);

  const handleOptionsChange = useCallback((patch: Partial<SceneOptions>) => {
    setOptions((previous) => ({ ...previous, ...patch }));
  }, []);

  const handleStats = useCallback((next: StageStats) => {
    setStats(next);
  }, []);

  const handleError = useCallback((message: string) => {
    setError(message);
  }, []);

  /**
   * 订阅基座下发的全局状态。
   *
   * 走桥的 `onHostGlobalStateChange`，**不直接调 `props.onGlobalStateChange`**：
   * qiankun 每个子应用只有一个回调槽位，直接注册会把桥用来刷新右下角调试标记的
   * 订阅顶掉（症状：这里的徽标变了，右下角的标记不变）。
   *
   * 这里**不带 `fireImmediately`**：立即回调会在 effect 同步执行期间触发 setState，
   * 而当前值已经由上面的初值读到了，不需要第二次。
   */
  useEffect(
    () =>
      onHostGlobalStateChange((state) => {
        setGlobalState(state);
      }),
    [],
  );

  const bridgeConnected = isMicroApp();

  return (
    <div className={`env-app env-app--${mode}`}>
      <header className="env-app__bar">
        <div className="env-app__heading">
          <h1 className="env-app__title">三维环境</h1>
          <span className="env-app__subtitle">three.js · 多场景渲染与生命周期</span>
        </div>

        <div className="env-app__badges">
          <span
            className={`env-app__badge env-app__badge--${mode}`}
            title="挂载点来源：被基座加载时用基座容器，独立启动时用自建容器"
          >
            {mode === 'host' ? '被基座加载 · 基座容器' : '独立运行 · 自建容器'}
          </span>
          <span
            className={`env-app__badge env-app__badge--${bridgeConnected ? 'linked' : 'plain'}`}
            title="与基座的通信桥接状态（common-tools/micro）"
          >
            {bridgeConnected ? '桥接已连接' : '桥接未连接'}
          </span>
          <span className="env-app__badge" title="基座下发的全局状态">
            {`theme=${globalState.theme ?? '-'} · userId=${globalState.userId ?? '未登录'}`}
          </span>
        </div>
      </header>

      <SceneSwitcher
        scenes={catalog.scenes}
        activeId={activeScene.meta.id}
        onSelect={handleSelect}
        source={catalog.source}
      />

      <div className="env-app__stage">
        {error ? (
          <WebGLFallback message={error} />
        ) : (
          <SceneView
            scene={activeScene}
            options={options}
            onStats={handleStats}
            onError={handleError}
          />
        )}
        <ControlPanel options={options} onChange={handleOptionsChange} stats={stats} />
      </div>
    </div>
  );
}
