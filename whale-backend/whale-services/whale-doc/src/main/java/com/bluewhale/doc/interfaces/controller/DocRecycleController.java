package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocRecycleService;
import com.bluewhale.doc.interfaces.vo.DocRecycleItemVO;
import com.bluewhale.doc.support.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档回收站接口（M2）。
 *
 * <p>回收站以「删除时的子树根节点」为操作单位：列表只展示根节点，恢复与彻底清除
 * 均作用于整棵子树。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/doc/v{version}", version = "1")
@RequiredArgsConstructor
public class DocRecycleController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "doc_recycle";

    private final DocRecycleService recycleService;

    /**
     * 分页查询空间回收站。
     *
     * @param spaceId     空间ID
     * @param current     当前页（从 1 开始）
     * @param size        每页条数
     * @param httpRequest 当前请求
     * @return 回收站分页
     */
    @GetMapping("/space/{spaceId}/recycle/page")
    @RequirePermission("sys:doc:page:list")
    public Result<PageResult<DocRecycleItemVO>> page(@PathVariable Long spaceId,
                                                     @RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "20") long size,
                                                     HttpServletRequest httpRequest) {
        return Result.ok(recycleService.page(spaceId, current, size, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 恢复回收站子树。
     *
     * @param id          回收站根页面ID
     * @param httpRequest 当前请求
     * @return 空响应体
     */
    @PostMapping("/page/{id}/trash/restore")
    @RequirePermission("sys:doc:page:edit")
    @AuditLog(operation = "恢复回收站页面", type = OperateType.UPDATE, targetType = TARGET_TYPE,
            targetId = "#id")
    public Result<Void> restore(@PathVariable Long id, HttpServletRequest httpRequest) {
        recycleService.restore(id, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }

    /**
     * 彻底清除回收站子树。
     *
     * @param id          回收站根页面ID
     * @param httpRequest 当前请求
     * @return 空响应体
     */
    @DeleteMapping("/page/{id}/purge")
    @RequirePermission("sys:doc:page:delete")
    @AuditLog(operation = "彻底删除回收站页面", type = OperateType.DELETE, targetType = TARGET_TYPE,
            targetId = "#id", recordParams = false)
    public Result<Void> purge(@PathVariable Long id, HttpServletRequest httpRequest) {
        recycleService.purge(id, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }

    /**
     * 清空空间回收站。
     *
     * @param spaceId     空间ID
     * @param httpRequest 当前请求
     * @return 空响应体
     */
    @DeleteMapping("/space/{spaceId}/recycle")
    @RequirePermission("sys:doc:page:delete")
    @AuditLog(operation = "清空文档回收站", type = OperateType.DELETE, targetType = TARGET_TYPE,
            targetId = "#spaceId", recordParams = false)
    public Result<Void> clear(@PathVariable Long spaceId, HttpServletRequest httpRequest) {
        recycleService.purgeSpace(spaceId, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }
}
