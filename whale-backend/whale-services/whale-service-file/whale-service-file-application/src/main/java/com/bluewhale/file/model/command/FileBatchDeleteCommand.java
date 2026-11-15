package com.bluewhale.file.model.command;

import java.util.List;

/**
 * 批量删除命令。
 *
 * @param ids 待删除的文件ID列表（允许重复，应用层去重后按顺序处理）
 * @param requesterId 调用者用户 ID
 * @param canManage 是否持有文件管理权限
 * @author BlueWhale
 */
public record FileBatchDeleteCommand(List<Long> ids, Long requesterId, boolean canManage) {
}
