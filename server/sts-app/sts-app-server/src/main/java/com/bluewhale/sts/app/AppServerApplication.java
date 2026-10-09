package com.bluewhale.sts.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 服务端应用启动入口。
 *
 * <p><b>组件扫描以本类所在包（{@code com.bluewhale.sts.app}）为根，不写
 * {@code scanBasePackages} 扩大范围。</b>跨模块的组件由各模块**自己的自动配置**提供：
 * <ul>
 *   <li>{@code sts-common-web} → 全局异常处理、跨域</li>
 *   <li>{@code sts-common-db} → MyBatis-Plus 拦截器链</li>
 *   <li>{@code sts-server-log} → 日志域的 Controller / Service / Mapper</li>
 * </ul>
 * 这样"依赖哪个模块就得到哪些能力"是显式的：看依赖树即可推断，
 * 而不必去猜一个包扫描路径覆盖了谁。包扫描写法的真正问题在于"删除依赖但代码照常运行"——
 * 因为那个类碰巧落在扫描路径里，依赖关系早已名存实亡。
 */
@SpringBootApplication
public class AppServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppServerApplication.class, args);
    }

}
