package com.bluewhale.file.model.result;

import java.util.List;

/**
 * 批量删除结果：逐项返回处理结果，单项失败不影响其余项。
 *
 * @param items 按请求顺序（去重后）的逐项结果
 * @author BlueWhale
 */
public record FileBatchDeleteResult(List<FileBatchDeleteItemResult> items) {

    /**
     * 成功删除的条数
     *
     * @return 成功条数
     */
    public int successCount() {
        return (int) items.stream().filter(FileBatchDeleteItemResult::success).count();
    }

    /**
     * 删除失败的条数
     *
     * @return 失败条数
     */
    public int failureCount() {
        return items.size() - successCount();
    }
}
