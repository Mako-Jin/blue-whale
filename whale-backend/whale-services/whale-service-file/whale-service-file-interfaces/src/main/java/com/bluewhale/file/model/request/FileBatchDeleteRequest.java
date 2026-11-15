package com.bluewhale.file.model.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量操作请求（批量删除）。
 *
 * <p>数量上限由 {@code whale.file.batch.max-files} 在应用层校验，避免与配置项重复硬编码。</p>
 *
 * @author BlueWhale
 */
@Data
public class FileBatchDeleteRequest {

    /** 待删除的文件ID列表 */
    @NotEmpty(message = "文件ID列表不能为空")
    private List<@NotNull(message = "文件ID不能为空") Long> ids;
}
