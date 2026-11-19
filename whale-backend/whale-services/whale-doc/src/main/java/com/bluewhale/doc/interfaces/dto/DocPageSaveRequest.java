package com.bluewhale.doc.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tools.jackson.databind.JsonNode;

/**
 * 保存页面请求。
 *
 * @author BlueWhale
 */
@Data
public class DocPageSaveRequest {

    /** 客户端持有的服务端 revision，用于乐观锁 */
    @NotNull(message = "{validation.doc.page.base-revision-required}")
    @Min(value = 0, message = "{validation.doc.page.base-revision-min}")
    private Long baseRevision;

    /** 标题（可选，null 表示不修改） */
    @Size(max = 200, message = "{validation.doc.page.title-size}")
    private String title;

    /** 正文 JSON（FOLDER 不提交） */
    private JsonNode contentJson;

    /** 变更说明 */
    @Size(max = 200, message = "{validation.doc.page.change-summary-size}")
    private String changeSummary;
}
