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
    @NotBlank(message = "{validation.forum.sensitive-word.word-required}")
    @Size(max = 128, message = "{validation.forum.sensitive-word.word-size}")
    private String word;

    /** 动作：BLOCK 拦截 / REPLACE 替换 / PENDING 转待审 */
    @NotBlank(message = "{validation.forum.sensitive-word.action-required}")
    @Pattern(regexp = "BLOCK|REPLACE|PENDING", message = "{validation.forum.sensitive-word.action-pattern}")
    private String action;

    /** 分类（可选） */
    @Size(max = 32, message = "{validation.forum.sensitive-word.category-size}")
    private String category;
}
