package com.bluewhale.file.assemble;

import com.bluewhale.file.model.result.StoragePluginResult;
import com.bluewhale.file.model.response.StoragePluginResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 存储插件结果到接口层响应的 MapStruct 装配器。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface StoragePluginAssembler {

    /**
     * 插件结果转响应
     *
     * @param result 插件结果
     * @return 插件响应
     */
    StoragePluginResponse toResponse(StoragePluginResult result);

    /**
     * 插件结果列表转响应列表
     *
     * @param results 插件结果列表
     * @return 插件响应列表
     */
    List<StoragePluginResponse> toResponses(List<StoragePluginResult> results);
}
