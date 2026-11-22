package com.bluewhale.party.content.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 党建内容新建 / 编辑命令。
 *
 * <p>可见范围为「指定支部」时 {@link #branchIds} 必填非空；其余范围忽略该字段。
 * 封面与附件只收文件 ID，上传由前端直接调 {@code whale-file} 完成。</p>
 *
 * @author BlueWhale
 */
@Data
public class ContentSaveCommand {

    /** 分类 */
    @NotBlank(message = "{validation.party.content.category-required}")
    @Size(max = 64, message = "{validation.party.content.category-size}")
    private String category;

    /** 标题 */
    @NotBlank(message = "{validation.party.content.title-required}")
    @Size(max = 200, message = "{validation.party.content.title-size}")
    private String title;

    /** 摘要 */
    @Size(max = 500, message = "{validation.party.content.summary-size}")
    private String summary;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 正文（草稿可为空，提交审核必填） */
    @Size(max = 65000, message = "{validation.party.content.body-size}")
    private String body;

    /** 附件文件 ID 列表 */
    @Size(max = 10, message = "{validation.party.content.attachment-file-ids-size}")
    private List<Long> attachmentFileIds;

    /** 可见范围：ALL_MEMBERS / BRANCHES / ALL_STAFF */
    private String visibility;

    /** 指定支部 ID 列表（可见范围为 BRANCHES 时必填） */
    @Size(max = 20, message = "{validation.party.content.branch-ids-size}")
    private List<Long> branchIds;

    /** 备注 */
    @Size(max = 255, message = "{validation.party.content.remark-size}")
    private String remark;
}
