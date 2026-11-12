package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.request.DeptRequest;
import com.bluewhale.auth.interfaces.dto.response.DeptResponse;
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
 * 部门 API 契约：部门树的增删改查。
 *
 * <p>本接口只声明 HTTP 契约（路径、方法、参数与返回 DTO），业务实现位于 starter 层。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/dept/v{version}", version = "1")
public interface DeptApi {

    /**
     * 部门树：parentId=0 为根，按 sort 升序
     *
     * @return 部门树节点列表
     */
    @GetMapping("/tree")
    Result<List<DeptResponse>> tree();

    /**
     * 部门详情
     *
     * @param id 部门 ID
     * @return 部门响应
     */
    @GetMapping("/{id}")
    Result<DeptResponse> detail(@PathVariable Long id);

    /**
     * 创建部门
     *
     * @param request 部门请求参数
     * @return 操作结果
     */
    @PostMapping
    Result<Void> create(@Valid @RequestBody DeptRequest request);

    /**
     * 更新部门
     *
     * @param id 部门 ID
     * @param request 部门请求参数
     * @return 操作结果
     */
    @PutMapping("/{id}")
    Result<Void> update(@PathVariable Long id, @Valid @RequestBody DeptRequest request);

    /**
     * 删除部门
     *
     * @param id 部门 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);
}
