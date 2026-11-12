package com.bluewhale.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 敏感词保存请求。
 *
 * @author BlueWhale
 */
@Data
public class SensitiveWordSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 敏感词 */
    @NotBlank(message = "敏感词不能为空")
    @Size(max = 128, message = "敏感词不能超过 128 字")
    private String word;

    /** 动作：BLOCK 拦截 / REPLACE 替换 / PENDING 转待审 */
    @NotBlank(message = "动作不能为空")
    @Pattern(regexp = "BLOCK|REPLACE|PENDING", message = "动作只能是 BLOCK/REPLACE/PENDING")
    private String action;

    /** 分类（可选） */
    @Size(max = 32, message = "分类不能超过 32 字")
    private String category;
}
