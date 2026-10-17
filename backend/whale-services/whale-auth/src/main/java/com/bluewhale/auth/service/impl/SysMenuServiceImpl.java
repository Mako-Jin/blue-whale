package com.bluewhale.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.bluewhale.auth.dto.MenuRequest;
import com.bluewhale.auth.entity.SysMenu;
import com.bluewhale.auth.mapper.SysMenuMapper;
import com.bluewhale.auth.service.SysMenuService;
import com.bluewhale.auth.vo.MenuVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 系统菜单服务实现
 *
 * @author BlueWhale
 */
@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    @Override
    public List<MenuVO> tree() {
        List<MenuVO> all = list(new LambdaQueryWrapper<SysMenu>()
                        .orderByAsc(SysMenu::getSort)
                        .orderByAsc(SysMenu::getId))
                .stream().map(MenuVO::from).toList();
        return buildChildren(all, 0L);
    }

    /**
     * 简单递归组树
     */
    private List<MenuVO> buildChildren(List<MenuVO> all, Long parentId) {
        return all.stream()
                .filter(vo -> vo.getParentId() != null && parentId.equals(vo.getParentId()))
                .peek(vo -> vo.setChildren(buildChildren(all, vo.getId())))
                .toList();
    }

    @Override
    public MenuVO getDetail(Long id) {
        SysMenu menu = getById(id);
        if (menu == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return MenuVO.from(menu);
    }

    @Override
    public void create(MenuRequest request) {
        SysMenu menu = new SysMenu();
        copyProperties(request, menu);
        save(menu);
    }

    @Override
    public void update(Long id, MenuRequest request) {
        SysMenu menu = getById(id);
        if (menu == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        copyProperties(request, menu);
        updateById(menu);
    }

    @Override
    public void delete(Long id) {
        Long children = baseMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "存在子菜单，不允许删除");
        }
        removeById(id);
    }

    private void copyProperties(MenuRequest request, SysMenu menu) {
        menu.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        menu.setName(request.getName());
        menu.setPath(request.getPath());
        menu.setComponent(request.getComponent());
        menu.setIcon(request.getIcon());
        menu.setSort(request.getSort() == null ? 0 : request.getSort());
        menu.setMenuType(StringUtils.hasText(request.getMenuType()) ? request.getMenuType() : "MENU");
        menu.setPermissionCode(request.getPermissionCode());
        menu.setVisible(request.getVisible() == null ? 1 : request.getVisible());
        menu.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }
}
