import { useEffect, useRef, useState } from 'react';
import type { MicroApp } from 'qiankun';
import { createLogger } from 'common-tools';
import { findMicroApp, mountMicroApp } from '@/micro';
import './MicroAppContainer.less';

const logger = createLogger('micro:container');

type Phase = 'loading' | 'ready' | 'error';

/**
 * 子应用挂载容器。
 *
 * **本组件自己就是 qiankun 的挂载点** —— 路由出口在哪，子应用就挂在哪，
 * 加载与卸载直接对应 React 的挂载与清理，不存在"容器还没渲染出来就去挂载"的竞态。
 *
 * 实例暂存在 ref 里而不是局部变量：清理函数要拿到**最新的**那个实例，
 * 而 promise 回调是异步的，闭包里的局部变量可能已经过期。
 *
 * 关于样式隔离：这里没有开 qiankun 的 `experimentalStyleIsolation`。
 * 那个能力依赖子应用把 `<style>` 写进入口 HTML，而 Vite 开发期是**由 JS 在运行时**
 * 把样式插进 `document.head` 的，且这段代码跑在真实 window 上、不经过沙箱代理，
 * 因此拦截规则根本不会命中。结论是：两个子应用的样式前缀必须自己保证不撞车，
 * 不能指望框架兜底。
 */
export default function MicroAppContainer({ appName }: { appName: string }) {
  const meta = findMicroApp(appName);
  const hostRef = useRef<HTMLDivElement | null>(null);
  const instanceRef = useRef<MicroApp | null>(null);
  const [phase, setPhase] = useState<Phase>('loading');
  const [errorText, setErrorText] = useState('');

  useEffect(() => {
    const host = hostRef.current;
    if (!meta || !host) {
      return;
    }

    // 卸载后所有异步回调一律静默 —— 否则迟到的 catch 会把已经离开的页面重新置为"失败"
    let disposed = false;

    const start = async () => {
      try {
        const instance = mountMicroApp(meta, host);
        instanceRef.current = instance;
        await instance.mountPromise;

        if (disposed) {
          return;
        }
        logger.success(`子应用 ${meta.name} 已挂载`);
        setPhase('ready');
      } catch (error) {
        if (disposed) {
          return;
        }
        logger.error(`子应用 ${meta.name} 加载失败`, error);
        setPhase('error');
        setErrorText(error instanceof Error ? error.message : String(error));
      }
    };

    void start();

    return () => {
      disposed = true;
      const instance = instanceRef.current;
      instanceRef.current = null;

      void Promise.resolve(instance?.unmount()).catch((error: unknown) => {
        logger.warn(`子应用 ${meta.name} 卸载异常`, error);
      });
    };
  }, [meta]);

  if (!meta) {
    return (
      <div className="micro-app micro-app--missing">
        <p className="micro-app__error">未注册的子应用：{appName}</p>
        <p className="micro-app__hint">请在 src/micro/apps.ts 的 MICRO_APPS 中登记。</p>
      </div>
    );
  }

  return (
    <section className="micro-app">
      <header className="micro-app__bar">
        <span className="micro-app__title">{meta.title}</span>
        <code className="micro-app__name">{meta.name}</code>
        <span className={`micro-app__phase micro-app__phase--${phase}`}>
          {phase === 'loading' ? '加载中' : phase === 'ready' ? '已挂载' : '加载失败'}
        </span>
      </header>

      {phase === 'error' && (
        <div className="micro-app__error-panel">
          <p className="micro-app__error">{errorText || '子应用加载失败'}</p>
          <p className="micro-app__hint">
            开发期最常见的原因是子应用 dev server 没起（端口 {meta.devPort}），或者子应用没开 CORS。
          </p>
        </div>
      )}

      <div className="micro-app__host" ref={hostRef} />
    </section>
  );
}
