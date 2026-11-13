package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.request.UserGroupMembersSaveRequest;
import com.bluewhale.auth.interfaces.dto.request.UserGroupSaveRequest;
import com.bluewhale.auth.interfaces.dto.response.UserGroupResponse;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户组 API 契约（权限中心扩展 M3）。
 *
 * <p>静态组成员显式维护；动态组成员由规则求值快照重建，不可通过成员端点改写。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/user-group/v{version}", version = "1")
public interface UserGroupApi {

    /**
     * 分页查询用户组
     *
     * @param pageQuery 分页参数
     * @param keyword 关键字（模糊匹配编码/名称，可空）
     * @param category 类别过滤（STATIC / DYNAMIC，可空）
     * @return 用户组分页结果
     */
    @GetMapping("/page")
    Result<PageResult<UserGroupResponse>> page(PageQuery pageQuery,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String category);

    /**
     * 新增用户组
     *
     * @param request 保存请求
     * @return 新建用户组
     */
    @PostMapping
    Result<UserGroupResponse> create(@Valid @RequestBody UserGroupSaveRequest request);

    /**
     * 编辑用户组（组编码不可变）
     *
     * @param id 主键
     * @param request 保存请求
     * @return 更新后的用户组
     */
    @PutMapping("/{id}")
    Result<UserGroupResponse> update(@PathVariable Long id,
                                     @Valid @RequestBody UserGroupSaveRequest request);

    /**
     * 删除用户组（并清理成员关系）
     *
     * @param id 主键
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);

    /**
     * 查询组成员用户 ID 列表
     *
     * @param id 组 ID
     * @return 成员用户 ID 列表
     */
    @GetMapping("/{id}/members")
    Result<List<Long>> listMembers(@PathVariable Long id);

    /**
     * 全量替换静态组成员（空列表表示清空；动态组走规则重算，不可手工改）
     *
     * @param id 组 ID
     * @param request 成员保存请求
     * @return 操作结果
     */
    @PutMapping("/{id}/members")
    Result<Void> replaceMembers(@PathVariable Long id,
                                @Valid @RequestBody UserGroupMembersSaveRequest request);
}
