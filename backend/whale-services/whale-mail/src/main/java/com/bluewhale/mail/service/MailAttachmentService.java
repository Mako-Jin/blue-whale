package com.bluewhale.mail.service;

import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.entity.MailAttachment;
import com.bluewhale.mail.entity.MailFolder;
import com.bluewhale.mail.entity.MailMessage;

import java.util.List;

/**
 * 邮件附件服务。
 *
 * <p>附件字节不落本库：入库时上传 {@code whale-file} 换 {@code fileId}，
 * 下载时再按 {@code fileId} 取流。本服务只管元信息与归属校验。</p>
 *
 * @author BlueWhale
 */
public interface MailAttachmentService {

    /**
     * 确保某封邮件的附件已登记入库（幂等）。
     *
     * <p>采用「首次打开详情时登记」而非同步时登记：附件是低频大对象，
     * 同步阶段下载会让首同步流量与存储成本随邮件量线性上涨。</p>
     *
     * @param message 邮件
     * @param account 所属账号
     * @param folder 所属文件夹
     * @return 附件实体列表（已入库）
     */
    List<MailAttachment> ensureIngested(MailMessage message, MailAccount account, MailFolder folder);

    /**
     * 下载附件内容。
     *
     * @param attachmentId 附件ID
     * @param userId 当前用户ID（用于归属校验）
     * @return 附件内容
     */
    AttachmentContent download(Long attachmentId, Long userId);

    /**
     * 附件内容快照。
     *
     * @param fileName 文件名
     * @param contentType MIME 类型
     * @param content 字节内容
     */
    record AttachmentContent(String fileName, String contentType, byte[] content) {
    }
}
