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
    @NotEmpty(message = "{validation.file.common.ids-required}")
    private List<@NotNull(message = "{validation.file.common.file-id-required}") Long> ids;
}
