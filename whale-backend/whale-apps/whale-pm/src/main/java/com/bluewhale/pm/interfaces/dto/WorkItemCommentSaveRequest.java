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
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容长度不能超过 2000")
    private String content;

    /** 父评论ID（回复时填写） */
    private Long parentId;
}
