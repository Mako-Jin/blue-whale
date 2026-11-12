package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.command.SaveOAuthClientCommand;
import com.bluewhale.auth.application.model.vo.OAuthClientSecretVO;
import com.bluewhale.auth.application.model.vo.OAuthClientVO;
import com.bluewhale.auth.interfaces.dto.request.OAuthClientSaveRequest;
import com.bluewhale.auth.interfaces.dto.response.OAuthClientResponse;
import com.bluewhale.auth.interfaces.dto.response.OAuthClientSecretResponse;
import com.bluewhale.common.core.result.PageResult;
import org.mapstruct.Mapper;

/**
 * OAuth2 客户端接口装配器：request → command，view object → response。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface OAuthClientAssembler {

    /**
     * 保存请求 → 保存命令
     *
     * @param request 保存请求
     * @return 保存命令
     */
    SaveOAuthClientCommand toCommand(OAuthClientSaveRequest request);

    /**
     * 客户端视图对象 → 客户端响应
     *
     * @param vo 客户端视图对象
     * @return 客户端响应
     */
    OAuthClientResponse toResponse(OAuthClientVO vo);

    /**
     * 密钥视图对象 → 密钥响应
     *
     * @param vo 密钥视图对象
     * @return 密钥响应
     */
    OAuthClientSecretResponse toSecretResponse(OAuthClientSecretVO vo);

    /**
     * 客户端分页 → 客户端响应分页
     *
     * @param page 客户端分页
     * @return 客户端响应分页
     */
    default PageResult<OAuthClientResponse> toPage(PageResult<OAuthClientVO> page) {
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }
}
