package com.bluewhale.sts.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档元信息。
 *
 * <p><b>这里只写"元信息"，接口本身由 springdoc 从 Controller 注解推导。</b>
 * 手写一份 openapi.yaml 的好处是契约先行，代价是它与代码会各自演化 ——
 * 而契约一旦漂移，前端就照着它写出一堆调不通的代码，且漂移是无声的。
 * 本项目的取舍是**让契约由代码生成**（`scripts/export-openapi.sh` → `docs/api/openapi.json`），
 * 生成物入库并被前端消费，从而在结构上消除漂移。
 *
 * <p>代价同样存在：契约无法先于代码评审。这在"前后端由同一团队推进"的阶段是可接受的，
 * 当出现多个消费方时，应改为契约先行 + 契约测试。详见
 * {@code docs/decisions/0006}。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI blueWhaleOpenApi() {
        return new OpenAPI().info(new Info()
                .title("BlueWhale 服务端 API")
                .version("0.1.0")
                .description("""
                        统一响应约定：**业务结果只由 `code` 表达，业务失败同样返回 HTTP 200**，
                        调用方统一判断 `code === 0`。传输层失败（401/403/404/5xx）由框架按标准返回，不套统一包装。
                        """));
    }

}
