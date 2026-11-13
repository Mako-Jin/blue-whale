package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.model.command.SavePermissionStrategyCommand;
import com.bluewhale.auth.application.model.vo.PermissionStrategyVO;
import com.bluewhale.auth.interfaces.dto.request.PermissionStrategySaveRequest;
import com.bluewhale.auth.interfaces.dto.response.PermissionStrategyResponse;
import com.bluewhale.common.core.result.PageResult;
import org.mapstruct.Mapper;

/**
 * 权限策略接口装配器：request → command，view object → response。
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface PermissionStrategyAssembler {

    /**
     * 保存请求 → 保存命令（命令 id 置空，更新主键由控制器以路径变量传入）
     *
     * @param request 保存请求
     * @return 保存命令
     */
    SavePermissionStrategyCommand toCommand(PermissionStrategySaveRequest request);

    /**
     * 策略视图对象 → 策略响应
     *
     * @param vo 策略视图对象
     * @return 策略响应
     */
    PermissionStrategyResponse toResponse(PermissionStrategyVO vo);

    /**
     * 策略分页 → 策略响应分页
     *
     * @param page 策略分页
     * @return 策略响应分页
     */
    default PageResult<PermissionStrategyResponse> toPage(PageResult<PermissionStrategyVO> page) {
        return PageResult.of(page.getRecords().stream().map(this::toResponse).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }
}
