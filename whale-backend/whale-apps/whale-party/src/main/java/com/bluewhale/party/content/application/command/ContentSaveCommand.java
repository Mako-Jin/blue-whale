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
    @NotBlank(message = "内容分类不能为空")
    @Size(max = 64, message = "内容分类不得超过 64 字符")
    private String category;

    /** 标题 */
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不得超过 200 字符")
    private String title;

    /** 摘要 */
    @Size(max = 500, message = "摘要不得超过 500 字符")
    private String summary;

    /** 封面文件 ID */
    private Long coverFileId;

    /** 正文（草稿可为空，提交审核必填） */
    @Size(max = 65000, message = "正文不得超过 65000 字符，请改为附件上传")
    private String body;

    /** 附件文件 ID 列表 */
    @Size(max = 10, message = "附件不得超过 10 个")
    private List<Long> attachmentFileIds;

    /** 可见范围：ALL_MEMBERS / BRANCHES / ALL_STAFF */
    private String visibility;

    /** 指定支部 ID 列表（可见范围为 BRANCHES 时必填） */
    @Size(max = 20, message = "指定支部不得超过 20 个")
    private List<Long> branchIds;

    /** 备注 */
    @Size(max = 255, message = "备注不得超过 255 字符")
    private String remark;
}
