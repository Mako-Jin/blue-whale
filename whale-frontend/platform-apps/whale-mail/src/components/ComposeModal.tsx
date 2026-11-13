// 写信 / 回复 / 编辑草稿 弹窗。
//
// M1 起支持附件：文件先直传 `whale-file` 换 fileId，发信只传引用。
// 草稿仍**不保存附件**（`mail_draft` 不落附件引用），界面上明确提示，
// 避免用户存了草稿以为附件还在。

import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Tag,
  Typography,
  Upload,
  message as toast,
} from 'antd';
import { useEffect, useState } from 'react';
import {
  createDraft,
  removeDraft,
  sendMail,
  updateDraft,
  uploadAttachment,
  type MailAccount,
  type MailDraft,
  type MailMessage,
  type UploadedFile,
} from '../api/mail';

/** 邮箱格式（与后端 @Email 校验保持一致的宽松口径） */
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** 写信表单值 */
interface ComposeFormValues {
  accountId: number;
  to: string[];
  cc?: string[];
  bcc?: string[];
  subject?: string;
  body?: string;
}

/** 写信弹窗属性 */
interface ComposeModalProps {
  open: boolean;
  accounts: MailAccount[];
  defaultAccountId?: number;
  /** 编辑已有草稿 */
  draft?: MailDraft | null;
  /** 回复某封邮件（与 draft 互斥，draft 优先） */
  replyTo?: MailMessage | null;
  onClose: () => void;
  /** 发送或保存草稿成功后回调（用于刷新列表） */
  onChanged: () => void;
}

/**
 * 逗号分隔地址串转数组（草稿与回复场景用）。
 *
 * @param raw 地址串
 * @returns 地址数组
 */
function splitAddresses(raw?: string | null): string[] {
  if (!raw) {
    return [];
  }
  return raw
    .split(/[,;，；\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

/** 写信弹窗 */
export default function ComposeModal({
  open,
  accounts,
  defaultAccountId,
  draft,
  replyTo,
  onClose,
  onChanged,
}: ComposeModalProps) {
  const [form] = Form.useForm<ComposeFormValues>();
  const [sending, setSending] = useState(false);
  const [saving, setSaving] = useState(false);
  const [attachmentFiles, setAttachmentFiles] = useState<UploadedFile[]>([]);
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    setAttachmentFiles([]);
    if (draft) {
      form.setFieldsValue({
        accountId: draft.accountId,
        to: splitAddresses(draft.toAddrs),
        cc: splitAddresses(draft.ccAddrs),
        bcc: splitAddresses(draft.bccAddrs),
        subject: draft.subject ?? '',
        body: draft.bodyText ?? '',
      });
      return;
    }
    if (replyTo) {
      const quoted = replyTo.bodyText ? `\n\n----- 原始邮件 -----\n${replyTo.bodyText}` : '';
      form.setFieldsValue({
        accountId: replyTo ? (defaultAccountId ?? accounts[0]?.id) : undefined,
        to: splitAddresses(replyTo.fromAddr),
        cc: [],
        bcc: [],
        subject: replyTo.subject ? `Re: ${replyTo.subject}` : 'Re:',
        body: quoted,
      });
      return;
    }
    form.setFieldsValue({
      accountId: defaultAccountId ?? accounts[0]?.id,
      to: [],
      cc: [],
      bcc: [],
      subject: '',
      body: '',
    });
  }, [open, draft, replyTo, defaultAccountId, accounts, form]);

  /**
   * 读取当前表单值并裁剪空白。
   *
   * <p>校验失败返回 null：antd 已在字段下方给出提示，调用方不应再弹「发送失败」。</p>
   *
   * @returns 规整后的表单值；校验不通过时为 null
   */
  const readValues = async (): Promise<ComposeFormValues | null> => {
    let values: ComposeFormValues;
    try {
      values = await form.validateFields();
    } catch {
      return null;
    }
    return {
      ...values,
      to: (values.to ?? []).map((item) => item.trim()).filter(Boolean),
      cc: (values.cc ?? []).map((item) => item.trim()).filter(Boolean),
      bcc: (values.bcc ?? []).map((item) => item.trim()).filter(Boolean),
      subject: values.subject?.trim(),
      body: values.body ?? '',
    };
  };

  /** 选择文件后立即上传换取 fileId；返回 false 阻止 antd 自行发请求 */
  const handleBeforeUpload = (file: File): boolean => {
    void (async () => {
      setUploading(true);
      try {
        const uploaded = await uploadAttachment(file);
        setAttachmentFiles((prev) => [...prev, uploaded]);
      } catch (err) {
        toast.error(err instanceof Error ? err.message : `附件 ${file.name} 上传失败`);
      } finally {
        setUploading(false);
      }
    })();
    return false;
  };

  /** 保存草稿 */
  const handleSaveDraft = async () => {
    const values = await readValues();
    if (!values) {
      return;
    }
    setSaving(true);
    try {
      const payload = {
        accountId: values.accountId,
        subject: values.subject,
        to: values.to,
        cc: values.cc,
        bcc: values.bcc,
        bodyText: values.body,
      };
      if (draft) {
        await updateDraft(draft.id, payload);
      } else {
        await createDraft(payload);
      }
      toast.success('草稿已保存');
      onChanged();
      onClose();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '草稿保存失败');
    } finally {
      setSaving(false);
    }
  };

  /** 发送 */
  const handleSend = async () => {
    const values = await readValues();
    if (!values) {
      return;
    }
    setSending(true);
    try {
      await sendMail({
        accountId: values.accountId,
        to: values.to,
        cc: values.cc?.length ? values.cc : undefined,
        bcc: values.bcc?.length ? values.bcc : undefined,
        subject: values.subject,
        bodyText: values.body,
        attachments: attachmentFiles.length
          ? attachmentFiles.map((file) => ({
              fileId: file.id,
              fileName: file.originalName ?? file.fileName ?? 'attachment',
              contentType: file.contentType ?? undefined,
              sizeBytes: file.fileSize ?? undefined,
            }))
          : undefined,
      });
      if (draft) {
        // 草稿已发出，清掉本地副本；失败不影响发送结果，仅提示
        try {
          await removeDraft(draft.id);
        } catch {
          toast.warning('邮件已发出，但本地草稿清理失败，可在草稿箱手动删除');
        }
      }
      toast.success('邮件已发送');
      onChanged();
      onClose();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '发送失败');
    } finally {
      setSending(false);
    }
  };

  return (
    <Modal
      open={open}
      title={draft ? '编辑草稿' : replyTo ? '回复' : '写邮件'}
      width={760}
      onCancel={onClose}
      maskClosable={false}
      footer={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button loading={saving} onClick={handleSaveDraft}>
            存草稿
          </Button>
          <Button type="primary" loading={sending} onClick={handleSend}>
            发送
          </Button>
        </Space>
      }
    >
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message="正文为纯文本发送；草稿不保存附件"
        description="附件在发送时随邮件发出，但「存草稿」不会保留附件引用——需要保留请直接发送。"
      />
      <Form form={form} layout="vertical" requiredMark={false}>
        <Form.Item
          name="accountId"
          label="发件账号"
          rules={[{ required: true, message: '请选择发件账号' }]}
        >
          <Select
            placeholder="选择发件邮箱"
            options={accounts.map((item) => ({
              value: item.id,
              label: item.displayName ? `${item.displayName} <${item.email}>` : item.email,
            }))}
          />
        </Form.Item>
        <Form.Item
          name="to"
          label="收件人"
          rules={[
            { required: true, message: '至少填写一个收件人' },
            {
              validator: (_, value: string[] | undefined) => {
                const list = value ?? [];
                if (list.length === 0) {
                  return Promise.reject(new Error('至少填写一个收件人'));
                }
                const bad = list.filter((item) => !EMAIL_PATTERN.test(item.trim()));
                return bad.length > 0
                  ? Promise.reject(new Error(`地址格式不正确：${bad.join('、')}`))
                  : Promise.resolve();
              },
            },
          ]}
          tooltip="输入后按回车添加，可粘贴多个地址（逗号或分号分隔）"
        >
          <Select
            mode="tags"
            placeholder="name@example.com"
            tokenSeparators={[',', ';', '，', '；']}
          />
        </Form.Item>
        <Space style={{ width: '100%' }} size={16}>
          <Form.Item name="cc" label="抄送" style={{ flex: 1 }}>
            <Select mode="tags" placeholder="可选" tokenSeparators={[',', ';', '，', '；']} />
          </Form.Item>
          <Form.Item name="bcc" label="密送" style={{ flex: 1 }}>
            <Select mode="tags" placeholder="可选" tokenSeparators={[',', ';', '，', '；']} />
          </Form.Item>
        </Space>
        <Form.Item name="subject" label="主题">
          <Input placeholder="邮件主题" maxLength={512} />
        </Form.Item>
        <Form.Item name="body" label="正文">
          <Input.TextArea placeholder="邮件正文" autoSize={{ minRows: 10, maxRows: 18 }} />
        </Form.Item>
        <Form.Item label="附件" tooltip="单个 25MB、单封合计 50MB">
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            <Space wrap>
              <Upload multiple showUploadList={false} beforeUpload={handleBeforeUpload}>
                <Button loading={uploading}>添加附件</Button>
              </Upload>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                选择后立即上传；发送时才随邮件投递
              </Typography.Text>
            </Space>
            {attachmentFiles.length > 0 ? (
              <Space wrap size={4}>
                {attachmentFiles.map((file) => (
                  <Tag
                    key={file.id}
                    closable
                    onClose={() =>
                      setAttachmentFiles((prev) => prev.filter((item) => item.id !== file.id))
                    }
                  >
                    {file.originalName ?? file.fileName ?? '附件'}
                  </Tag>
                ))}
              </Space>
            ) : null}
          </Space>
        </Form.Item>
      </Form>
    </Modal>
  );
}
