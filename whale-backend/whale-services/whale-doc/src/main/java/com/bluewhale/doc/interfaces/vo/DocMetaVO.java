package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 文档服务运行元信息。
 *
 * <p>供前端子应用启动时确认服务可用性、当前租户与功能开关，避免前端硬编码能力范围。</p>
 *
 * @author BlueWhale
 */
@Data
public class DocMetaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务名 */
    private String service;

    /** 当前租户ID */
    private Long tenantId;

    /** 当前用户ID文本（网关注入，未登录时为 {@code null}） */
    private String userId;

    /** 是否启用实时协作（M2） */
    private boolean collabEnabled;

    /** 是否启用发布审批（M3） */
    private boolean approvalEnabled;

    /** 单页正文 JSON 最大字节数 */
    private long contentMaxBytes;

    /** 单页纯文本投影最大字节数 */
    private long contentTextMaxBytes;

    /** 单个附件最大字节数 */
    private long attachmentMaxBytes;

    /** 页面树最大层级 */
    private int maxDepth;
}
