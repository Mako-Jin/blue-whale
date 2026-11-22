package com.bluewhale.map.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.map.dto.FenceCheckRequest;
import com.bluewhale.map.dto.FenceQuery;
import com.bluewhale.map.dto.FenceSaveRequest;
import com.bluewhale.map.service.MapFenceService;
import com.bluewhale.map.vo.FenceHitVO;
import com.bluewhale.map.vo.FenceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 围栏接口。
 *
 * <p>两类查询刻意分开：{@code /{id}/contains} 是「这个点在这条围栏里吗」，
 * 已知围栏问一点；{@code /match} 是「这个点在哪些围栏里」，已知一点问全部。
 * 打卡用后者（用户到了某个位置，要找出命中的考勤组），配送范围校验用前者。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/map/v{version}/fence", version = "1")
@RequiredArgsConstructor
public class MapFenceController {

    /** 围栏服务 */
    private final MapFenceService fenceService;

    /**
     * 保存围栏（按 id 是否为空决定新建或覆盖）
     *
     * @param request 保存请求
     * @return 保存后的围栏
     */
    @PostMapping
    @RequirePermission("sys:map:fence:manage")
    public Result<FenceVO> save(@Valid @RequestBody FenceSaveRequest request) {
        return Result.ok(fenceService.save(request));
    }

    /**
     * 分页查询围栏
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:map:fence:query")
    public Result<PageResult<FenceVO>> page(@Valid FenceQuery query) {
        return Result.ok(fenceService.page(query));
    }

    /**
     * 围栏详情
     *
     * @param id 围栏ID
     * @return 围栏
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:map:fence:query")
    public Result<FenceVO> detail(@PathVariable Long id) {
        return Result.ok(fenceService.detail(id));
    }

    /**
     * 删除围栏（逻辑删除）
     *
     * @param id 围栏ID
     * @return 空结果
     */
    @PostMapping("/{id}/remove")
    @RequirePermission("sys:map:fence:manage")
    public Result<Void> remove(@PathVariable Long id) {
        fenceService.remove(id);
        return Result.ok();
    }

    /**
     * 判定某点是否在指定围栏内
     *
     * @param id      围栏ID
     * @param request 点位
     * @return 判定结果
     */
    @PostMapping("/{id}/contains")
    @RequirePermission("sys:map:fence:query")
    public Result<FenceHitVO> contains(@PathVariable Long id,
                                       @Valid @RequestBody FenceCheckRequest request) {
        return Result.ok(fenceService.contains(id, request));
    }

    /**
     * 反查某点命中的全部围栏
     *
     * @param request 点位与业务范围
     * @return 命中的围栏
     */
    @PostMapping("/match")
    @RequirePermission("sys:map:fence:query")
    public Result<List<FenceHitVO>> match(@Valid @RequestBody FenceCheckRequest request) {
        return Result.ok(fenceService.match(request));
    }
}
