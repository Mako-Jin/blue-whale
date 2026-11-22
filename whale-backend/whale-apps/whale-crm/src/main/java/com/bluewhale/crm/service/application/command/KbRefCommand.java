package com.bluewhale.crm.service.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识引用维护命令。
 *
 * @author BlueWhale
 */
@Data
public class KbRefCommand {

    /** 标题 */
    @NotBlank
    @Size(max = 255)
    private String title;

    /** 分类 */
    @Size(max = 64)
    private String category;

    /** 产品标签（逗号分隔） */
    @Size(max = 255)
    private String productTags;

    /** 故障标签（逗号分隔） */
    @Size(max = 255)
    private String faultTags;

    /** 可见范围：PUBLIC / INTERNAL */
    @Pattern(regexp = "PUBLIC|INTERNAL", message = "{validation.crm.kb-ref.visible-scope-pattern}")
    private String visibleScope;

    /** whale-doc 文档 ID */
    private Long docId;

    /** 文档版本（缺省取文档当前版本） */
    private Integer docVersion;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "{validation.crm.kb-ref.status-pattern}")
    private String status;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;
}
