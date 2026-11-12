package com.bluewhale.auth.starter.assemble;

import com.bluewhale.auth.application.vo.MenuVO;
import com.bluewhale.auth.interfaces.dto.response.MenuResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 菜单接口装配器：application 视图对象 → interfaces 响应 DTO。
 *
 * <p>菜单 {@code children} 通过 {@link #toResponse(MenuVO)} 递归映射。</p>
 *
 * @author BlueWhale
 */
@Mapper(componentModel = "spring")
public interface MenuAssembler {

    /**
     * 菜单视图对象 → 菜单响应
     *
     * @param vo 菜单视图对象
     * @return 菜单响应
     */
    MenuResponse toResponse(MenuVO vo);

    /**
     * 菜单视图对象列表 → 菜单响应列表
     *
     * @param vos 菜单视图对象列表
     * @return 菜单响应列表
     */
    List<MenuResponse> toResponses(List<MenuVO> vos);
}
