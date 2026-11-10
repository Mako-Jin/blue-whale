// IoT 子应用根组件：左侧模块导航 + 右侧页面（无路由库，按内部状态切换）
//
// 基座以 activeRule=/iot 挂载本子应用；页面切换只切内部状态，回写 URL 便于刷新定位。

import { useMemo, useState } from 'react';
import { Menu } from 'antd';
import ProductPage from './pages/ProductPage';
import DevicePage from './pages/DevicePage';
import TelemetryPage from './pages/TelemetryPage';
import RulePage from './pages/RulePage';

type PageKey = 'product' | 'device' | 'telemetry' | 'rule';

const PAGES: Array<{ key: PageKey; label: string }> = [
  { key: 'product', label: '产品与物模型' },
  { key: 'device', label: '设备管理' },
  { key: 'telemetry', label: '遥测与影子' },
  { key: 'rule', label: '规则引擎' },
];

/** 从路径推断初始页（/iot/device → device），未知值回落产品页 */
function initialPage(): PageKey {
  const segment = window.location.pathname.split('/')[2] ?? '';
  const matched = PAGES.find((page) => page.key === segment);
  return matched?.key ?? 'product';
}

export default function App() {
  const [active, setActive] = useState<PageKey>(initialPage);

  const content = useMemo(() => {
    switch (active) {
      case 'device':
        return <DevicePage />;
      case 'telemetry':
        return <TelemetryPage />;
      case 'rule':
        return <RulePage />;
      default:
        return <ProductPage />;
    }
  }, [active]);

  const handleSelect = (key: string) => {
    setActive(key as PageKey);
    // 子应用 base 为 /iot，同步子路径便于刷新/回退定位（不改基座主路由）
    window.history.replaceState(null, '', `/iot/${key}`);
  };

  return (
    <div className="iot-layout">
      <div className="iot-sider">
        <div className="iot-sider-title">设备联网</div>
        <Menu
          mode="inline"
          selectedKeys={[active]}
          onClick={({ key }) => handleSelect(key)}
          items={PAGES.map((page) => ({ key: page.key, label: page.label }))}
          style={{ borderInlineEnd: 'none' }}
        />
      </div>
      <div className="iot-content">{content}</div>
    </div>
  );
}
