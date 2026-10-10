import { Link } from 'react-router-dom';
import { MICRO_APPS } from '@/micro';
import './index.less';

export default function Home() {
  return (
    <section className="home">
      <h1 className="home__title">平台门户已就绪</h1>
      <p className="home__desc">
        主应用是 qiankun 容器，只负责导航骨架与全局状态下发；具体功能由子应用提供。
        下面两个入口既是端到端演示页，也都是**能独立启动**的应用 ——
        单独跑（各自端口）与被主应用加载（本页）走的是同一份代码，这正是要验证的点。
      </p>

      <ul className="home__list">
        {MICRO_APPS.map((app) => (
          <li key={app.name} className="home__item">
            <div className="home__item-head">
              <Link className="home__link" to={app.routePath}>
                {app.title}
              </Link>
              <code className="home__name">{app.name}</code>
            </div>
            <p className="home__summary">{app.summary}</p>
          </li>
        ))}
      </ul>

      <div className="home__note">
        <p className="home__note-title">首次调试请先起子应用的 dev server</p>
        <pre className="home__code">
          {`pnpm --filter platform-log-console dev     # :3200
pnpm --filter platform-event-console dev   # :3300`}
        </pre>
        <p className="home__note-desc">
          子应用没起时不会白屏：入口会显示"加载失败"并给出端口提示。
        </p>
      </div>
    </section>
  );
}
