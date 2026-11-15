package com.bluewhale.file.assemble;

import com.bluewhale.file.model.result.StorageMigrationResult;
import com.bluewhale.file.model.response.StorageMigrationResponse;
import org.mapstruct.Mapper;

/**
 * 存储迁移结果到接口层响应的 MapStruct 装配器。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface StorageMigrationAssembler {

    /**
     * 迁移结果转响应
     *
     * @param result 迁移结果
     * @return 迁移响应
     */
    StorageMigrationResponse toResponse(StorageMigrationResult result);
}
