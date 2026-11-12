// 申诉材料预览：图片拉缩略图，其它类型保持原下载链接
//
// 预览接口要鉴权，`<img src>` 带不上 Authorization 头，只能先 fetch 成 blob
// 再用对象 URL 渲染；对象 URL 随组件卸载释放，避免多份材料时内存泄漏。
// 非图片材料后端直接拒（PARAM_ERROR），前端不请求，省一次必然失败的往返。
// 缩略图按需加载：滚到视口（提前 64px）才发请求，复核台一次展开多张工单时不会一次打满。

import { useEffect, useRef, useState } from 'react';
import { Image, Space, Typography } from 'antd';
import { fetchAppealMaterialPreview, type AppealMaterial } from '../api/forum';

const { Text } = Typography;

/** 与后端 `previewMaterial` 的放行口径一致 */
function isImage(material: AppealMaterial): boolean {
  return (material.contentType ?? '').startsWith('image/');
}

function downloadHref(material: AppealMaterial): string {
  return material.downloadUrl ?? `/file/${material.fileId}/download`;
}

/** 单份材料：图片显示缩略图，其余或加载失败降级为文件名链接 */
function MaterialThumb({ material }: { material: AppealMaterial }) {
  const [src, setSrc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const [visible, setVisible] = useState(false);
  const holderRef = useRef<HTMLDivElement | null>(null);
  const label = material.fileName ?? `文件 #${material.fileId}`;
  const image = isImage(material);

  // 进入视口才拉取；不做懒加载时，复核台一次展开多条工单会并发打满预览接口
  useEffect(() => {
    if (!image || visible) {
      return;
    }
    const node = holderRef.current;
    if (!node || typeof IntersectionObserver === 'undefined') {
      // 老浏览器没有 IntersectionObserver：直接加载，别让缩略图永远停在占位态
      setVisible(true);
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setVisible(true);
          observer.disconnect();
        }
      },
      { rootMargin: '64px' },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [image, visible]);

  useEffect(() => {
    if (!image || !visible) {
      return;
    }
    let disposed = false;
    let objectUrl: string | null = null;
    setSrc(null);
    setFailed(false);
    fetchAppealMaterialPreview(material.fileId)
      .then((url) => {
        objectUrl = url;
        if (disposed) {
          URL.revokeObjectURL(url);
        } else {
          setSrc(url);
        }
      })
      .catch(() => {
        if (!disposed) {
          setFailed(true);
        }
      });
    return () => {
      disposed = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [image, visible, material.fileId]);

  if (!image || failed) {
    return (
      <a href={downloadHref(material)} target="_blank" rel="noreferrer">
        {label}
      </a>
    );
  }

  if (!src) {
    return (
      <div
        ref={holderRef}
        style={{
          width: 88,
          height: 64,
          borderRadius: 4,
          background: 'rgba(0, 0, 0, 0.04)',
        }}
        title={label}
      />
    );
  }

  return (
    <Space direction="vertical" size={2}>
      <Image
        src={src}
        width={88}
        height={64}
        alt={label}
        style={{ objectFit: 'cover', borderRadius: 4 }}
      />
      <Text type="secondary" ellipsis style={{ maxWidth: 88, fontSize: 12 }}>
        {label}
      </Text>
    </Space>
  );
}

/**
 * 申诉材料列表。
 *
 * @param materials 服务端返回的材料快照（无材料时返回 null）
 */
export default function AppealMaterials({
  materials,
}: {
  materials?: AppealMaterial[] | null;
}) {
  if (!materials || materials.length === 0) {
    return null;
  }
  return (
    <Space size={12} wrap align="start">
      {materials.map((material) => (
        <MaterialThumb key={material.fileId} material={material} />
      ))}
    </Space>
  );
}
