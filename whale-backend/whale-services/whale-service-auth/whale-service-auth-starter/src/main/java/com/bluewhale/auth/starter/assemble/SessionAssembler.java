package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.vo.SessionVOs.SessionVO;
import com.bluewhale.auth.interfaces.dto.response.SessionResponse;
import com.bluewhale.common.core.result.PageResult;
import org.mapstruct.Mapper;

/**
 * 会话接口装配器：view object → response。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface SessionAssembler {

    /**
     * 会话视图对象 → 会话响应
     *
     * @param vo 会话视图对象
     * @return 会话响应
     */
    SessionResponse toResponse(SessionVO vo);

    /**
     * 会话分页 → 会话响应分页
     *
     * @param page 会话分页
     * @return 会话响应分页
     */
    default PageResult<SessionResponse> toPage(PageResult<SessionVO> page) {
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }
}
