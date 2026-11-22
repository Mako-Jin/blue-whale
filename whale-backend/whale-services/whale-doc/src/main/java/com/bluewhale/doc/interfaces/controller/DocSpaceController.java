package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocSpaceService;
import com.bluewhale.doc.interfaces.dto.DocMemberAddRequest;
import com.bluewhale.doc.interfaces.dto.DocMemberRoleRequest;
import com.bluewhale.doc.interfaces.dto.DocSpaceQueryRequest;
import com.bluewhale.doc.interfaces.dto.DocSpaceSaveRequest;
import com.bluewhale.doc.interfaces.vo.DocMemberVO;
import com.bluewhale.doc.interfaces.vo.DocSpaceVO;
import com.bluewhale.doc.support.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档空间与成员接口。
 *
 * <p>服务内路径 {@code /doc/space/**}；平台权限码控制功能入口，数据权限由
 * {@code DocAccessGuard} 按空间角色二次校验。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/doc/v{version}/space", version = "1")
@RequiredArgsConstructor
public class DocSpaceController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "doc_space";

    private final DocSpaceService spaceService;

    /**
     * 分页查询我可访问的空间。
     *
     * @param request      查询条件
     * @param httpRequest  当前请求
     * @return 空间分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:doc:space:list")
    public Result<PageResult<DocSpaceVO>> page(DocSpaceQueryRequest request,
                                               HttpServletRequest httpRequest) {
        return Result.ok(spaceService.page(request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 查询我加入或拥有的空间。
     *
     * @param httpRequest 当前请求
     * @return 空间列表
     */
    @GetMapping("/my")
    @RequirePermission("sys:doc:space:list")
    public Result<List<DocSpaceVO>> my(HttpServletRequest httpRequest) {
        return Result.ok(spaceService.my(UserContext.requireUserId(httpRequest)));
    }

    /**
     * 空间详情。
     *
     * @param id          空间ID
     * @param httpRequest 当前请求
     * @return 空间详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:doc:space:list")
    public Result<DocSpaceVO> detail(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(spaceService.detail(id, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 创建空间。
     *
     * @param request     空间信息
     * @param httpRequest 当前请求
     * @return 新建空间
     */
    @PostMapping
    @RequirePermission("sys:doc:space:add")
    @AuditLog(operation = "创建文档空间", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<DocSpaceVO> create(@Valid @RequestBody DocSpaceSaveRequest request,
                                     HttpServletRequest httpRequest) {
        return Result.ok(spaceService.create(request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 更新空间信息。
     *
     * @param id          空间ID
     * @param request     空间信息
     * @param httpRequest 当前请求
     * @return 更新后的空间
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:doc:space:manage")
    @AuditLog(operation = "更新文档空间", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<DocSpaceVO> update(@PathVariable Long id,
                                     @Valid @RequestBody DocSpaceSaveRequest request,
                                     HttpServletRequest httpRequest) {
        return Result.ok(spaceService.update(id, request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 归档空间（仅 OWNER）。
     *
     * @param id          空间ID
     * @param httpRequest 当前请求
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:doc:space:manage")
    @AuditLog(operation = "归档文档空间", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> archive(@PathVariable Long id, HttpServletRequest httpRequest) {
        spaceService.archive(id, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }

    /**
     * 查询空间成员。
     *
     * @param id          空间ID
     * @param httpRequest 当前请求
     * @return 成员列表
     */
    @GetMapping("/{id}/member")
    @RequirePermission("sys:doc:member:list")
    public Result<List<DocMemberVO>> members(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(spaceService.members(id, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 批量添加成员。
     *
     * @param id          空间ID
     * @param request     成员与角色
     * @param httpRequest 当前请求
     * @return 受影响的成员
     */
    @PostMapping("/{id}/member")
    @RequirePermission("sys:doc:member:manage")
    @AuditLog(operation = "添加文档空间成员", type = OperateType.CREATE, targetType = "doc_member")
    public Result<List<DocMemberVO>> addMembers(@PathVariable Long id,
                                                @Valid @RequestBody DocMemberAddRequest request,
                                                HttpServletRequest httpRequest) {
        return Result.ok(spaceService.addMembers(id, request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 调整成员角色。
     *
     * @param spaceId     空间ID
     * @param memberId    成员记录ID
     * @param request     新角色
     * @param httpRequest 当前请求
     * @return 更新后的成员
     */
    @PutMapping("/{spaceId}/member/{memberId}")
    @RequirePermission("sys:doc:member:manage")
    @AuditLog(operation = "调整文档空间成员角色", type = OperateType.UPDATE, targetType = "doc_member",
            targetId = "#memberId")
    public Result<DocMemberVO> updateMember(@PathVariable Long spaceId,
                                            @PathVariable Long memberId,
                                            @Valid @RequestBody DocMemberRoleRequest request,
                                            HttpServletRequest httpRequest) {
        return Result.ok(spaceService.updateMember(spaceId, memberId, request,
                UserContext.requireUserId(httpRequest)));
    }

    /**
     * 移除成员。
     *
     * @param spaceId     空间ID
     * @param memberId    成员记录ID
     * @param httpRequest 当前请求
     * @return 空响应体
     */
    @DeleteMapping("/{spaceId}/member/{memberId}")
    @RequirePermission("sys:doc:member:manage")
    @AuditLog(operation = "移除文档空间成员", type = OperateType.DELETE, targetType = "doc_member",
            targetId = "#memberId")
    public Result<Void> removeMember(@PathVariable Long spaceId, @PathVariable Long memberId,
                                     HttpServletRequest httpRequest) {
        spaceService.removeMember(spaceId, memberId, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }
}
