package com.bluewhale.sts.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 系统域服务启动入口。
 *
 * <p>无需显式声明 {@code scanBasePackages}：组件扫描以本类所在包
 * （{@code com.bluewhale.sts.system}）为根，跨模块的公共能力（如全局异常处理）
 * 由 {@code sts-common-web} 的自动配置提供，不靠扩大扫描范围来实现 —— 后者会让
 * 依赖关系从代码里消失。
 */
@SpringBootApplication
public class SystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
    }

}
