package com.bluewhale.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bluewhale.auth.dto.MenuRequest;
import com.bluewhale.auth.entity.SysMenu;
import com.bluewhale.auth.vo.MenuVO;

import java.util.List;

/**
 * 系统菜单服务
 *
 * @author BlueWhale
 */
public interface SysMenuService extends IService<SysMenu> {

    /** 菜单树（parentId=0 为根，按 sort 升序） */
    List<MenuVO> tree();

    MenuVO getDetail(Long id);

    void create(MenuRequest request);

    void update(Long id, MenuRequest request);

    void delete(Long id);
}
