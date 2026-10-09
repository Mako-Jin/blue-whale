package com.bluewhale.sts.log.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 日志域服务的装配入口。
 *
 * <p><b>为什么服务模块要自带一个自动配置类，而不是让启动类写
 * {@code scanBasePackages = "com.bluewhale.sts"}：</b>
 * 后者会让依赖关系从代码里消失 —— 读启动类看不出"这个接口是哪来的"，
 * 一个包重命名就能静默失效。本类的存在使"日志域提供了哪些组件"写在明面上，
 * 并且只要依赖 {@code sts-server-log} 就自动生效，接入方零配置。
 *
 * <p>{@link ComponentScan} 的作用域被**严格限制在本模块的包内**：自动配置里做扫描
 * 的危险在于可能把别处的 Bean 一并卷进来、打乱条件装配的先后顺序；限定包名后
 * 影响面就是本模块自身。
 *
 * <p>{@link MapperScan} 同时指定 {@code basePackages} 与 {@code annotationClass}：
 * 只指定包名会把包内**所有接口**都注册成 Mapper（包括将来无意间加进来的普通接口），
 * 出错时的报错信息晦涩难查。加上 {@code annotationClass} 后，只有显式标注
 * {@code @Mapper} 的接口才会被注册 —— 想成为 Mapper 必须有一次显式声明。
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.bluewhale.sts.log")
@MapperScan(basePackages = "com.bluewhale.sts.log", annotationClass = Mapper.class)
public class LogAutoConfiguration {

}
