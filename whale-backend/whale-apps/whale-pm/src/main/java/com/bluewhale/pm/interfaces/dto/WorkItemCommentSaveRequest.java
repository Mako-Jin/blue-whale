package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 工作项评论新增请求
 *
 * @author BlueWhale
 */
@Data
public class WorkItemCommentSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 评论内容 */
    @NotBlank(message = "{validation.pm.work-item-comment.content-required}")
    @Size(max = 2000, message = "{validation.pm.work-item-comment.content-size}")
    private String content;

    /** 父评论ID（回复时填写） */
    private Long parentId;
}
