import { useEffect } from 'react';

export default function SubAppContainer() {
  useEffect(() => {
    console.log('[whale-platform] SubAppContainer mounted, qiankun should render sub-app here');
  }, []);

  return (
    <div id="subapp-container" style={{ width: '100%', height: '100%', position: 'relative' }}>
      {/* qiankun 子应用会挂载到这个容器 */}
    </div>
  );
}
