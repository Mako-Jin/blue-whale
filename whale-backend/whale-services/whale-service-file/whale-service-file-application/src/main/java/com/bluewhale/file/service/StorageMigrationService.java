package com.bluewhale.file.service;

import com.bluewhale.file.model.command.StorageMigrationCommand;
import com.bluewhale.file.model.result.StorageMigrationResult;

/**
 * 存储迁移服务：把指定源落盘介质的文件复制到当前活动插件并更新元数据归属。
 *
 * @author BlueWhale
 */
public interface StorageMigrationService {

    /**
     * 执行一批迁移：逐个文件复制、校验字节数与 MD5、更新落盘类型，可选删除源对象
     *
     * <p>单个文件失败不影响后续文件；失败项保留原元数据，可在下一轮重试。</p>
     *
     * @param command 迁移命令
     * @return 迁移汇总
     */
    StorageMigrationResult migrate(StorageMigrationCommand command);
}
