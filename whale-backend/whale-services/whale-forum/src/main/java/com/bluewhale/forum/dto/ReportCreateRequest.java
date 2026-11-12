package com.bluewhale.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 举报提交请求。
 *
 * @author BlueWhale
 */
@Data
public class ReportCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 对象类型：THREAD / POST / USER */
    @NotBlank(message = "举报对象类型不能为空")
    @Pattern(regexp = "THREAD|POST|USER", message = "举报对象类型只能是 THREAD/POST/USER")
    private String targetType;

    /** 对象ID */
    private Long targetId;

    /** 举报类型：SPAM / ABUSE / PORN / ILLEGAL / OTHER */
    @NotBlank(message = "举报类型不能为空")
    @Pattern(regexp = "SPAM|ABUSE|PORN|ILLEGAL|OTHER", message = "举报类型不合法")
    private String reportType;

    /** 举报说明 */
    @Size(max = 1024, message = "举报说明不能超过 1024 字")
    private String description;
}
