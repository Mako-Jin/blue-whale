import './index.less';

export default function Home() {
  return (
    <section className="home">
      <h1 className="home__title">平台门户已就绪</h1>
      <p className="home__desc">
        这是 qiankun 主应用的容器页。子应用就绪后，将挂载在各自的子路由下。
      </p>
    </section>
  );
}
