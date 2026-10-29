package com.bluewhale.doc.application.service;

import com.bluewhale.doc.interfaces.vo.DocPageAttachmentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 页面附件应用服务。
 *
 * @author BlueWhale
 */
public interface DocAttachmentService {

    /**
     * 查询页面附件列表。
     *
     * @param pageId 页面ID
     * @param userId 当前用户ID
     * @return 附件列表
     */
    List<DocPageAttachmentVO> list(Long pageId, Long userId);

    /**
     * 上传附件并登记页面引用。
     *
     * @param pageId    页面ID
     * @param file      文件
     * @param usageType 用途：ATTACHMENT / INLINE
     * @param userId    当前用户ID
     * @return 附件视图
     */
    DocPageAttachmentVO upload(Long pageId, MultipartFile file, String usageType, Long userId);

    /**
     * 解除附件引用（不删除文件实体）。
     *
     * @param pageId       页面ID
     * @param attachmentId 附件引用ID
     * @param userId       当前用户ID
     */
    void delete(Long pageId, Long attachmentId, Long userId);

    /**
     * 读取附件内容（经 whale-file 内网代理）。
     *
     * @param pageId       页面ID
     * @param attachmentId 附件引用ID
     * @param userId       当前用户ID
     * @return 附件内容
     */
    DocAttachmentContent content(Long pageId, Long attachmentId, Long userId);
}
