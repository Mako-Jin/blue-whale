package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.MenuRequest;
import com.bluewhale.auth.interfaces.dto.response.MenuResponse;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 菜单 API 契约：菜单树的增删改查。
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/menu/v{version}", version = "1")
public interface MenuApi {

    /**
     * 菜单树：parentId=0 为根，按 sort 升序
     *
     * @return 菜单树节点列表
     */
    @GetMapping("/tree")
    Result<List<MenuResponse>> tree();

    /**
     * 菜单详情
     *
     * @param id 菜单 ID
     * @return 菜单响应
     */
    @GetMapping("/{id}")
    Result<MenuResponse> detail(@PathVariable Long id);

    /**
     * 创建菜单
     *
     * @param request 菜单请求参数
     * @return 操作结果
     */
    @PostMapping
    Result<Void> create(@Valid @RequestBody MenuRequest request);

    /**
     * 更新菜单
     *
     * @param id 菜单 ID
     * @param request 菜单请求参数
     * @return 操作结果
     */
    @PutMapping("/{id}")
    Result<Void> update(@PathVariable Long id, @Valid @RequestBody MenuRequest request);

    /**
     * 删除菜单
     *
     * @param id 菜单 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);
}
