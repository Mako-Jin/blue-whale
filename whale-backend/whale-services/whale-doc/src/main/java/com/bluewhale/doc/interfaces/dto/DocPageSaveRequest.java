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
    @NotNull(message = "baseRevision 不能为空")
    @Min(value = 0, message = "baseRevision 非法")
    private Long baseRevision;

    /** 标题（可选，null 表示不修改） */
    @Size(max = 200, message = "页面标题不能超过 200 字")
    private String title;

    /** 正文 JSON（FOLDER 不提交） */
    private JsonNode contentJson;

    /** 变更说明 */
    @Size(max = 200, message = "变更说明不能超过 200 字")
    private String changeSummary;
}
