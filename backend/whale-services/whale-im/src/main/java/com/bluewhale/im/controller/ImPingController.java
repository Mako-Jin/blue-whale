package com.bluewhale.im.controller;

import com.bluewhale.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WebIM 连通性探针。
 *
 * <p>服务内路径 {@code /im/ping}，经网关按 {@code Path=/im/**} 转发后外部路径一致（REQ-ARCH-003）。
 * 仅用于确认「模块可启动 + 网关不剥离前缀」；业务接口在 M1/T4 起逐步补齐。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/im")
public class ImPingController {

    /**
     * 返回服务名，证明请求已到达 whale-im。
     *
     * @return 服务标识
     */
    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.ok("whale-im");
    }
}
