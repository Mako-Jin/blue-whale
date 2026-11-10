// 内容 / 系列封面：有代理封面时加载图片，加载失败或无封面时展示占位渐变
//
// 封面地址由后端返回（/video/content/{id}/cover 或 /video/series/{id}/cover），
// 前端不接触 fileId；占位图避免无封面内容出现空白卡片。

import { useState } from 'react';
import type { CSSProperties } from 'react';

export interface ContentCoverProps {
  /** 封面代理地址（后端返回，可为空） */
  url?: string;
  /** 无封面时的占位文案（通常为标题） */
  title: string;
  /** 封面高度（像素） */
  height?: number;
  /** 额外样式 */
  style?: CSSProperties;
}

export default function ContentCover({ url, title, height = 160, style }: ContentCoverProps) {
  const [failed, setFailed] = useState(false);
  const showImage = Boolean(url) && !failed;

  return (
    <div
      style={{
        height,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
        background: 'linear-gradient(135deg, #1f2a44 0%, #3a4a6b 100%)',
        ...style,
      }}
    >
      {showImage ? (
        <img
          src={url}
          alt={title}
          onError={() => setFailed(true)}
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
      ) : (
        <span style={{ color: 'rgba(255,255,255,0.85)', fontSize: 13, padding: '0 12px', textAlign: 'center' }}>
          {title}
        </span>
      )}
    </div>
  );
}
