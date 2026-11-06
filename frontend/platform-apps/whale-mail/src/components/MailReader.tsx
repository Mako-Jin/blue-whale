// 邮件阅读区。
//
// 安全要点（不可省略）：
// 1. 收到邮件的 HTML 属于**不可信输入**，绝不能用 dangerouslySetInnerHTML 注入到主文档，
//    否则一封带 <img onerror> 或 <script> 的邮件就能拿到平台 token（基座为同源）。
//    这里改用 <iframe sandbox=""> 承载：不授 allow-scripts / allow-same-origin，
//    脚本无法执行，也读不到父页面 DOM 与 localStorage。
// 2. iframe 内再叠一层 CSP（default-src 'none'），即使未来放开脚本也不会外连。
// 3. 远程图片默认**不加载**，否则发件人可用带唯一参数的图片 URL 做「已读回执」追踪。
//    用户点「显示图片」后才放开 img-src。
// 4. 链接通过 <base target="_blank"> + allow-popups 在新标签打开，不占父页面导航。

import { Alert, Button, Empty, Space, Spin, Tag, Typography, message as toast } from 'antd';
import dayjs from 'dayjs';
import { useMemo, useState } from 'react';
import { downloadAttachment, type MailAttachment, type MailMessage } from '../api/mail';

/** 附件大小格式化 */
function formatSize(bytes?: number | null): string {
  if (!bytes || bytes <= 0) {
    return '-';
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

/**
 * 构造 iframe 的 srcdoc：注入 CSP、基础排版与 base target。
 *
 * @param html        邮件原始 HTML
 * @param allowImages 是否允许加载远程图片
 * @returns 可直接赋给 srcDoc 的完整文档
 */
function buildSrcDoc(html: string, allowImages: boolean): string {
  const imgSrc = allowImages ? "data: cid: http: https:" : 'data: cid:';
  const csp = [
    "default-src 'none'",
    `img-src ${imgSrc}`,
    "style-src 'unsafe-inline'",
    'font-src data:',
  ].join('; ');
  return [
    '<!doctype html><html><head><meta charset="utf-8">',
    `<meta http-equiv="Content-Security-Policy" content="${csp}">`,
    // 链接一律新标签打开，避免在 iframe 内导航导致阅读区被替换
    '<base target="_blank" rel="noopener noreferrer">',
    '<style>',
    'html,body{margin:0;padding:16px;font:14px/1.7 -apple-system,"Segoe UI","Microsoft YaHei",sans-serif;',
    'color:#1f2328;word-break:break-word;}',
    'img{max-width:100%;height:auto;}',
    'table{max-width:100%;border-collapse:collapse;}',
    'a{color:#1677ff;}',
    'blockquote{margin:8px 0;padding-left:12px;border-left:3px solid #e5e7eb;color:#57606a;}',
    '</style></head><body>',
    html,
    '</body></html>',
  ].join('');
}

/** 阅读区属性 */
interface MailReaderProps {
  /** 邮件详情（未选中时为 null） */
  message: MailMessage | null;
  /** 详情加载中 */
  loading: boolean;
  /** 切换星标 */
  onToggleFlag: (message: MailMessage) => void;
  /** 切换已读状态 */
  onToggleSeen: (message: MailMessage) => void;
  /** 移入回收站 */
  onDelete: (message: MailMessage) => void;
  /** 回复：带原主题与新正文模板打开写信框 */
  onReply: (message: MailMessage) => void;
}

/** 邮件阅读区 */
export default function MailReader({
  message,
  loading,
  onToggleFlag,
  onToggleSeen,
  onDelete,
  onReply,
}: MailReaderProps) {
  const [allowImages, setAllowImages] = useState(false);
  const [downloadingId, setDownloadingId] = useState<number | null>(null);

  /**
   * 下载附件。
   *
   * <p>不能用 <code>&lt;a href&gt;</code> 直连：下载接口要校验归属，必须带 Bearer token，
   * 而新窗口导航不会携带请求头，只能先取 blob 再触发本地保存。</p>
   *
   * @param item 附件
   */
  const handleDownload = async (item: MailAttachment) => {
    if (!item.fileId) {
      toast.warning('该附件超出单封容量上限，未缓存内容，无法下载');
      return;
    }
    setDownloadingId(item.id);
    try {
      const { blob, filename } = await downloadAttachment(item.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename || item.fileName || 'attachment';
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '附件下载失败');
    } finally {
      setDownloadingId(null);
    }
  };

  const srcDoc = useMemo(
    () => (message?.bodyHtml ? buildSrcDoc(message.bodyHtml, allowImages) : ''),
    [message?.bodyHtml, allowImages],
  );

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
        <Spin tip="正在读取邮件正文…">
          <div style={{ width: 200, height: 60 }} />
        </Spin>
      </div>
    );
  }

  if (!message) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
        <Empty description="从左侧选择一封邮件查看" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      </div>
    );
  }

  const attachments: MailAttachment[] = message.attachments ?? [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
      <div style={{ padding: '16px 20px 12px', borderBottom: '1px solid #f0f0f0', flex: '0 0 auto' }}>
        <Space style={{ width: '100%', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <Typography.Title level={5} style={{ margin: 0, flex: 1 }}>
            {message.subject || '(无主题)'}
          </Typography.Title>
          <Space size={4}>
            <Button size="small" type="text" onClick={() => onToggleFlag(message)}>
              {message.flagged === 1 ? '★ 取消星标' : '☆ 加星标'}
            </Button>
            <Button
              size="small"
              type="text"
              onClick={() => onToggleSeen(message)}
            >
              {message.seen === 1 ? '标为未读' : '标为已读'}
            </Button>
            <Button size="small" type="text" onClick={() => onReply(message)}>
              回复
            </Button>
            <Button size="small" type="text" danger onClick={() => onDelete(message)}>
              删除
            </Button>
          </Space>
        </Space>
        <div style={{ marginTop: 8, color: '#57606a', fontSize: 13, lineHeight: 1.8 }}>
          <div>
            发件人：{message.fromName ? `${message.fromName} <${message.fromAddr ?? ''}>` : message.fromAddr || '-'}
            <span style={{ marginLeft: 16 }}>
              {message.sentTime ? dayjs(message.sentTime).format('YYYY-MM-DD HH:mm') : '-'}
            </span>
            <span style={{ marginLeft: 16 }}>{formatSize(message.sizeBytes)}</span>
          </div>
          <div>收件人：{message.toAddrs || '-'}</div>
          {message.ccAddrs ? <div>抄送：{message.ccAddrs}</div> : null}
        </div>
      </div>

      {message.bodyHtml && !allowImages ? (
        <Alert
          type="info"
          showIcon
          banner
          message={
            <span>
              为保护隐私，远程图片已默认屏蔽（图片可用于追踪已读）。
              <Button type="link" size="small" onClick={() => setAllowImages(true)}>
                显示图片
              </Button>
            </span>
          }
        />
      ) : null}

      <div style={{ flex: 1, minHeight: 0, background: '#fff' }}>
        {message.bodyHtml ? (
          // sandbox 留空即「全部权限关闭」：不执行脚本、不同源、不弹窗（popup 由 base target 触发）
          <iframe
            title="邮件正文"
            sandbox="allow-popups allow-popups-to-escape-sandbox"
            srcDoc={srcDoc}
            style={{ width: '100%', height: '100%', border: 'none', display: 'block' }}
          />
        ) : (
          <div style={{ padding: 20, height: '100%', overflow: 'auto' }}>
            {message.bodyText ? (
              <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', fontFamily: 'inherit', margin: 0 }}>
                {message.bodyText}
              </pre>
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="这封邮件没有可显示的正文（可能是纯附件邮件或正文回源失败）"
              />
            )}
          </div>
        )}
      </div>

      {attachments.length > 0 ? (
        <div style={{ padding: '10px 20px', borderTop: '1px solid #f0f0f0', flex: '0 0 auto' }}>
          <Space wrap size={8}>
            <Typography.Text type="secondary">附件 {attachments.length} 个：</Typography.Text>
            {attachments.map((item) => (
              <Tag
                key={item.id}
                style={{ cursor: 'pointer' }}
                onClick={() => {
                  if (downloadingId === null) {
                    void handleDownload(item);
                  }
                }}
              >
                {item.fileName || '未命名'}（{formatSize(item.sizeBytes)}）
                {downloadingId === item.id ? ' 下载中…' : ''}
              </Tag>
            ))}
          </Space>
        </div>
      ) : null}
    </div>
  );
}
