package com.bluewhale.oa.hr.application.view;

import lombok.Data;

import java.io.Serializable;

/**
 * 请假附件视图。
 *
 * <p>{@code id} 为 OA 文件引用ID，前端编辑时用于保留既有附件；
 * 下载仍走 OA 文件代理，不暴露裸 {@code fileId}。</p>
 *
 * @author BlueWhale
 */
@Data
public class LeaveAttachmentView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文件引用ID */
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;
}
