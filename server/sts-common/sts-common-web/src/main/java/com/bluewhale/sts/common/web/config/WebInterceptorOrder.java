package com.bluewhale.sts.common.web.config;

/**
 * Web 层拦截器的注册顺序。
 *
 * <p><b>为什么要有一个集中的地方放这几个数字：</b>拦截器的先后顺序是一个
 * 完全静默的约束 —— 改错了不报错、不警告，只是某个 {@code afterCompletion} 不再被调用。
 * 散成两处字面量（一处 {@code .order(-100)}、一处 {@code .order(-50)}）时，
 * 后来者看不出它们之间有依赖，顺手把其中一个改成默认值就会踩坑。
 *
 * <p>而这条约束本身来自 Spring 的语义（已核对 6.2.11 与 7.0.1 的
 * {@code HandlerExecutionChain} 源码）：{@code applyPreHandle} 逐个调用，
 * 过程中若某个拦截器返回 false 或抛异常，只有**排在它前面、已经成功执行过 preHandle 的**
 * 拦截器会被回调 {@code afterCompletion}。
 *
 * <p>因此：
 * <ul>
 *   <li>{@link #AUDIT} 必须小于 {@link #AUTHORIZATION}。否则"越权被拒"这件事
 *       根本不会被审计拦截器看到 —— 而它的 {@code afterCompletion} 静默不执行，
 *       没有日志、没有异常，表现为"审计表里查不到越权记录"；</li>
 *   <li>两者都取负数，把 0 及以上的位置留给业务自己注册的拦截器 ——
 *       它们是最后一道，本不该被框架内部的顺序变化挤走。</li>
 * </ul>
 */
public final class WebInterceptorOrder {

    /** 审计：必须最先执行，见类注释 */
    public static final int AUDIT = -100;

    /** 鉴权：排在审计之后，这样鉴权失败也能被审计看到 */
    public static final int AUTHORIZATION = -50;

    /** 业务拦截器的默认位置 */
    public static final int DEFAULT = 0;

    private WebInterceptorOrder() {
    }

}
