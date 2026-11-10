// 消息输入区：Enter 发送 / Shift+Enter 换行，支持图片与文件上传

import { useState } from 'react';
import { App, Button, Input, Space, Upload } from 'antd';
import { PaperClipOutlined, PictureOutlined, SendOutlined } from '@ant-design/icons';
import { createLogger } from '@bluewhale/whale-common-tools';
import type { MessageContentType } from '../api/im';
import { uploadFile, type UploadedFile } from '../api/file';
import { formatSize } from '../utils/format';

const logger = createLogger('whale-im › composer');

const { TextArea } = Input;

interface ComposerProps {
  disabled: boolean;
  sending: boolean;
  onSend: (contentType: MessageContentType, content: string) => Promise<boolean>;
}

/** 附件消息 content JSON */
function toAttachmentContent(file: UploadedFile): string {
  return JSON.stringify({
    fileId: file.id,
    name: file.originalName,
    size: file.fileSize,
    contentType: file.contentType,
  });
}

export default function Composer({ disabled, sending, onSend }: ComposerProps) {
  const { message } = App.useApp();
  const [text, setText] = useState('');
  const [uploading, setUploading] = useState(false);

  const submitText = async () => {
    const content = text.trim();
    if (!content || disabled || sending) {
      return;
    }
    const ok = await onSend('TEXT', content);
    if (ok) {
      setText('');
    }
  };

  const handleUpload = async (file: File) => {
    setUploading(true);
    try {
      const uploaded = await uploadFile(file);
      const contentType: MessageContentType = file.type.startsWith('image/') ? 'IMAGE' : 'FILE';
      const ok = await onSend(contentType, toAttachmentContent(uploaded));
      if (!ok) {
        message.warning('附件已上传，但消息发送失败，请重试');
      } else {
        message.success(
          `已发送${contentType === 'IMAGE' ? '图片' : '文件'}：${uploaded.originalName}（${formatSize(uploaded.fileSize)}）`,
        );
      }
    } catch (error) {
      logger.warn('附件发送失败', error);
      message.error('附件上传失败，请重试');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="im-composer">
      <TextArea
        value={text}
        autoSize={{ minRows: 2, maxRows: 6 }}
        maxLength={16000}
        disabled={disabled}
        placeholder={disabled ? '请选择会话后输入消息' : '输入消息，Enter 发送，Shift+Enter 换行'}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
            event.preventDefault();
            void submitText();
          }
        }}
      />
      <div className="im-composer-toolbar">
        <Space size={8}>
          <Upload
            accept="image/*"
            showUploadList={false}
            disabled={disabled || uploading}
            beforeUpload={(file) => {
              void handleUpload(file);
              return Upload.LIST_IGNORE;
            }}
          >
            <Button size="small" type="text" icon={<PictureOutlined />} aria-label="发送图片" />
          </Upload>
          <Upload
            showUploadList={false}
            disabled={disabled || uploading}
            beforeUpload={(file) => {
              void handleUpload(file);
              return Upload.LIST_IGNORE;
            }}
          >
            <Button size="small" type="text" icon={<PaperClipOutlined />} aria-label="发送文件" />
          </Upload>
          {uploading && <span style={{ fontSize: 12 }}>上传中…</span>}
        </Space>
        <Button
          type="primary"
          size="small"
          icon={<SendOutlined />}
          loading={sending}
          disabled={disabled || !text.trim()}
          onClick={() => {
            void submitText();
          }}
        >
          发送
        </Button>
      </div>
    </div>
  );
}
