package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.vo.SessionVOs.LoginHistoryVO;
import com.bluewhale.auth.interfaces.dto.response.LoginHistoryResponse;
import com.bluewhale.common.core.result.PageResult;
import org.mapstruct.Mapper;

/**
 * 登录历史接口装配器：view object → response。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface LoginHistoryAssembler {

    /**
     * 登录历史视图对象 → 登录历史响应
     *
     * @param vo 登录历史视图对象
     * @return 登录历史响应
     */
    LoginHistoryResponse toResponse(LoginHistoryVO vo);

    /**
     * 登录历史分页 → 登录历史响应分页
     *
     * @param page 登录历史分页
     * @return 登录历史响应分页
     */
    default PageResult<LoginHistoryResponse> toPage(PageResult<LoginHistoryVO> page) {
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }
}
