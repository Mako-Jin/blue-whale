package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.command.SaveBrandConfigCommand;
import com.bluewhale.auth.application.model.vo.BrandConfigVO;
import com.bluewhale.auth.interfaces.dto.request.BrandConfigSaveRequest;
import com.bluewhale.auth.interfaces.dto.response.BrandConfigResponse;
import com.bluewhale.auth.interfaces.dto.response.BrandResolveResponse;
import com.bluewhale.common.core.result.PageResult;
import org.mapstruct.Mapper;

/**
 * 品牌配置接口装配器：request → command，view object → response。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface BrandAssembler {

    /**
     * 保存请求 → 保存命令
     *
     * @param request 保存请求
     * @return 保存命令
     */
    SaveBrandConfigCommand toCommand(BrandConfigSaveRequest request);

    /**
     * 品牌视图对象 → 品牌管理响应
     *
     * @param vo 品牌视图对象
     * @return 品牌管理响应
     */
    BrandConfigResponse toResponse(BrandConfigVO vo);

    /**
     * 品牌视图对象 → 登录页解析响应
     *
     * @param vo 品牌视图对象
     * @return 登录页解析响应
     */
    BrandResolveResponse toResolveResponse(BrandConfigVO vo);

    /**
     * 品牌分页 → 品牌管理响应分页
     *
     * @param page 品牌分页
     * @return 品牌管理响应分页
     */
    default PageResult<BrandConfigResponse> toPage(PageResult<BrandConfigVO> page) {
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }
}
