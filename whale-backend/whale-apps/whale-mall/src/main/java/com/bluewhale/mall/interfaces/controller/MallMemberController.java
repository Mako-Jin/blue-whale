package com.bluewhale.mall.interfaces.controller;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.mall.catalog.application.command.MemberSaveCommand;
import com.bluewhale.mall.catalog.application.service.MemberAppService;
import com.bluewhale.mall.catalog.application.view.MemberView;
import com.bluewhale.mall.common.MallPermissions;
import com.bluewhale.mall.interfaces.support.MallActorContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城会员接口（运营侧）：会员价（PriceType.MEMBER）的登记入口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mall/v{version}/member", version = "1")
@RequiredArgsConstructor
public class MallMemberController {

    private final MemberAppService memberAppService;

    /**
     * 会员分页。
     *
     * @param keyword 会员号/用户 ID 关键字
     * @param status  状态
     * @param current 页码
     * @param size    每页条数
     * @param actor   操作人
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(MallPermissions.MEMBER_LIST)
    public Result<PageResult<MemberView>> page(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "1") Long current,
                                               @RequestParam(defaultValue = "20") Long size,
                                               HttpServletRequest request) {
        return Result.ok(memberAppService.page(keyword, status, current, size, MallActorContext.from(request)));
    }

    /**
     * 会员详情。
     *
     * @param id    主键
     * @param actor 操作人
     * @return 详情
     */
    @GetMapping("/{id}")
    @RequirePermission(MallPermissions.MEMBER_LIST)
    public Result<MemberView> detail(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(memberAppService.detail(id, MallActorContext.from(request)));
    }

    /**
     * 登记会员（按用户 ID upsert）。
     *
     * @param command 保存命令
     * @param actor   操作人
     * @return 主键
     */
    @PostMapping
    @RequirePermission(MallPermissions.MEMBER_MANAGE)
    public Result<Long> save(@Valid @RequestBody MemberSaveCommand command, HttpServletRequest request) {
        return Result.ok(memberAppService.save(command, MallActorContext.from(request)));
    }

    /**
     * 删除会员登记。
     *
     * @param id    主键
     * @param actor 操作人
     * @return 空结果
     */
    @PostMapping("/{id}/remove")
    @RequirePermission(MallPermissions.MEMBER_MANAGE)
    public Result<Void> remove(@PathVariable Long id, HttpServletRequest request) {
        memberAppService.remove(id, MallActorContext.from(request));
        return Result.ok();
    }
}
