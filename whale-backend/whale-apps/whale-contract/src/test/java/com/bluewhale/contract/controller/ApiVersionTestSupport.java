package com.bluewhale.contract.controller;

import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.accept.DefaultApiVersionStrategy;
import org.springframework.web.accept.PathApiVersionResolver;
import org.springframework.web.accept.SemanticApiVersionParser;

import java.util.List;

/**
 * 控制器独立 MockMvc 测试的 API 版本策略。
 *
 * <p>真实运行时由 {@code ApiVersionConfig} + Boot 自动配置提供策略；
 * {@code MockMvcBuilders.standaloneSetup} 不会加载 Spring 上下文，
 * 必须显式设置策略，否则带 {@code version} 条件的映射在构建时直接失败。</p>
 *
 * @author BlueWhale
 */
final class ApiVersionTestSupport {

    private ApiVersionTestSupport() {
    }

    /**
     * 构建与生产一致（路径第 2 段取版本）的测试策略。
     *
     * @return API 版本策略
     */
    static ApiVersionStrategy strategy() {
        return new DefaultApiVersionStrategy(
                List.of(new PathApiVersionResolver(1)),
                new SemanticApiVersionParser(),
                null,
                null,
                false,
                version -> true,
                null);
    }
}
