package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.TenantConfigEntry;
import com.bluewhale.auth.application.model.vo.ConfigBatchItemVO;
import com.bluewhale.auth.application.model.vo.UserConfigVO;
import com.bluewhale.auth.interfaces.dto.response.ConfigBatchItemResponse;
import com.bluewhale.auth.interfaces.dto.response.TenantConfigEntryResponse;
import com.bluewhale.auth.interfaces.dto.response.UserConfigResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 通用配置接口装配器：application 视图对象 → interfaces 响应 DTO。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface ConfigAssembler {

    /**
     * 用户配置视图 → 用户配置响应
     *
     * @param vo 用户配置视图
     * @return 用户配置响应
     */
    UserConfigResponse toResponse(UserConfigVO vo);

    /**
     * 用户配置视图列表 → 用户配置响应列表
     *
     * @param vos 用户配置视图列表
     * @return 用户配置响应列表
     */
    List<UserConfigResponse> toResponses(List<UserConfigVO> vos);

    /**
     * 批量读取结果项 → 批量读取响应项
     *
     * @param vo 批量读取结果项
     * @return 批量读取响应项
     */
    ConfigBatchItemResponse toBatchItemResponse(ConfigBatchItemVO vo);

    /**
     * 批量读取结果项列表 → 批量读取响应项列表
     *
     * @param vos 批量读取结果项列表
     * @return 批量读取响应项列表
     */
    List<ConfigBatchItemResponse> toBatchItemResponses(List<ConfigBatchItemVO> vos);

    /**
     * 租户配置条目 → 租户配置条目响应
     *
     * @param entry 租户配置条目
     * @return 租户配置条目响应
     */
    TenantConfigEntryResponse toTenantConfigResponse(TenantConfigEntry entry);

    /**
     * 租户配置条目列表 → 租户配置条目响应列表
     *
     * @param entries 租户配置条目列表
     * @return 租户配置条目响应列表
     */
    List<TenantConfigEntryResponse> toTenantConfigResponses(List<TenantConfigEntry> entries);
}
