// 子应用挂载容器 - 监听 qiankun 加载失败事件，渲染 antd Result 兜底 UI

import { useEffect, useState } from 'react';
import { Button, Result } from 'antd';
import { useLocation } from 'react-router-dom';
import { apps } from '../qiankun/apps';
import { onSubAppError, startQiankun } from '../qiankun';

export default function SubAppContainer() {
  const { pathname } = useLocation();
  const segment = pathname.split('/')[1];
  const app = apps.find((item) => item.activeRule === `/${segment}`);
  const [failedApp, setFailedApp] = useState<string | null>(null);

  useEffect(() => {
    // 容器已在 DOM 中（useEffect 在提交后执行），此时启动 qiankun 才能正确激活；
    // 幂等，重复调用无副作用（深链接刷新时由这里补上 start）
    startQiankun();
    setFailedApp(null);
    if (!app) return undefined;
    return onSubAppError((name) => {
      if (name === app.name) setFailedApp(name);
    });
  }, [app]);

  if (app && failedApp === app.name) {
    return (
      <Result
        status="error"
        title="子应用加载失败"
        subTitle={`${app.name} 加载失败（entry: ${app.entry}），请确认子应用开发服务器已启动`}
        extra={
          <Button type="primary" onClick={() => window.location.reload()}>
            重新加载
          </Button>
        }
      />
    );
  }

  return (
    <div id="subapp-container" style={{ width: '100%', height: '100%', position: 'relative' }}>
      {/* qiankun 子应用会挂载到这个容器 */}
    </div>
  );
}
