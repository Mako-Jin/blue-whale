package com.bluewhale.file.model.response;

import com.bluewhale.file.model.result.FileBatchDeleteItemResult;
import com.bluewhale.file.model.result.FileBatchDeleteResult;

import java.util.List;

/**
 * 批量删除响应：逐项返回结果，前端据此提示部分失败的文件。
 *
 * @param total 去重后的总条数
 * @param successCount 删除成功条数
 * @param failureCount 删除失败条数
 * @param items 逐项结果
 * @author BlueWhale
 */
public record FileBatchDeleteResponse(int total, int successCount, int failureCount, List<Item> items) {

    /**
     * 由应用层结果构造响应
     *
     * @param result 批量删除结果
     * @return 批量删除响应
     */
    public static FileBatchDeleteResponse from(FileBatchDeleteResult result) {
        return new FileBatchDeleteResponse(result.items().size(), result.successCount(), result.failureCount(),
                result.items().stream().map(Item::from).toList());
    }

    /**
     * 单项删除结果响应。
     *
     * @param id 文件ID
     * @param success 是否删除成功
     * @param message 失败原因；成功时为 {@code null}
     * @author BlueWhale
     */
    public record Item(Long id, boolean success, String message) {

        /**
         * 由应用层单项结果构造响应
         *
         * @param item 单项结果
         * @return 单项响应
         */
        static Item from(FileBatchDeleteItemResult item) {
            return new Item(item.id(), item.success(), item.message());
        }
    }
}
