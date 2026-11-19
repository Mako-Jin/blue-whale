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
    @NotBlank(message = "{validation.forum.report.target-type-required}")
    @Pattern(regexp = "THREAD|POST|USER", message = "{validation.forum.report.target-type-pattern}")
    private String targetType;

    /** 对象ID */
    private Long targetId;

    /** 举报类型：SPAM / ABUSE / PORN / ILLEGAL / OTHER */
    @NotBlank(message = "{validation.forum.report.type-required}")
    @Pattern(regexp = "SPAM|ABUSE|PORN|ILLEGAL|OTHER", message = "{validation.forum.report.type-pattern}")
    private String reportType;

    /** 举报说明 */
    @Size(max = 1024, message = "{validation.forum.report.description-size}")
    private String description;
}
