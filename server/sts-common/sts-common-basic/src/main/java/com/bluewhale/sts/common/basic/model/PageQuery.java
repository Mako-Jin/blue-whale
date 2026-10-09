package com.bluewhale.sts.common.basic.model;

/**
 * 分页入参基类。
 *
 * <p>查询 DTO 继承本类即可获得分页能力，无需每个查询接口重复声明 {@code page}/{@code size}。
 *
 * <p><b>为什么在 getter 里做钳制而不是在 setter 里：</b>Spring MVC 的参数绑定在字段缺失时
 * 根本不调用 setter，字段保持默认值；把校验写在 getter 里，无论参数是"没传"、"传了 null"
 * 还是"传了非法值"，出口都只有一个且一定合法。调用方（Service）拿到的永远是安全值，
 * 不必再防一遍。
 *
 * <p><b>为什么设上限：</b>不设上限时，一次 {@code size=100000} 的请求就是一次全表扫描 +
 * 全量序列化，接口会以"正常返回 200"的方式打垮自己。上限是保护而非限制。
 */
public class PageQuery {

    /** 默认页码（从 1 开始，不是 0 —— 与前端分页组件、SQL OFFSET 的换算习惯保持一致） */
    public static final long DEFAULT_PAGE = 1L;

    /** 默认每页条数 */
    public static final long DEFAULT_SIZE = 20L;

    /** 单页最大条数，超出即按此值截断 */
    public static final long MAX_SIZE = 200L;

    private long page = DEFAULT_PAGE;

    private long size = DEFAULT_SIZE;

    public long getPage() {
        return Math.max(page, DEFAULT_PAGE);
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return Math.min(Math.max(size, 1L), MAX_SIZE);
    }

    public void setSize(long size) {
        this.size = size;
    }

    /** 换算为 SQL OFFSET，供不走 MyBatis-Plus 分页插件的场景使用 */
    public long offset() {
        return (getPage() - 1L) * getSize();
    }

}
