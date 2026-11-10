package com.bluewhale.mail.vo;

import lombok.Data;

/**
 * 全文检索引擎状态。
 *
 * <p>前端据此决定是否展示「重建索引」：MySQL 引擎的索引随表维护，重建没有意义，
 * 展示一个点了没反应的按钮只会让人怀疑功能坏了。</p>
 *
 * @author BlueWhale
 */
@Data
public class MailSearchStatusVO {

    /** 引擎标识：mysql / lucene */
    private String engine;

    /** 是否需要独立索引（决定「重建索引」是否可用） */
    private boolean externalIndex;

    /** 单次检索最大命中数（命中超过该值会被截断） */
    private int maxHits;

    /** 索引无命中时是否回退模糊匹配 */
    private boolean likeFallback;
}
