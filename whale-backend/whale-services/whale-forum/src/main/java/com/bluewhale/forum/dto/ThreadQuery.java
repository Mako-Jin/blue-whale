package com.bluewhale.forum.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 帖子列表查询条件。
 *
 * <p>筛选口径（F-06-04）：{@code authorId} / {@code from}~{@code to} / {@code tag} / {@code essenceOnly}
 * 在列表通道与检索通道必须同时生效——只在一处加条件时，用户「搜索 + 筛选」会静默返回范围外结果。</p>
 *
 * @author BlueWhale
 */
@Data
public class ThreadQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 版块ID（为空表示全站） */
    private Long boardId;

    /** 排序：latest 最新 / hot 热帖（按最后回复时间）/ essence 精华 */
    private String sort = "latest";

    /** 标题关键词（M1 走 LIKE，M3 换 ngram 全文索引） */
    private String keyword;

    /** 只看精华 */
    private Boolean essenceOnly;

    /** 作者用户ID（个人主页「我的帖子」与发现页作者筛选） */
    private Long authorId;

    /** 起始日期（含当日，按发布时间过滤；格式 yyyy-MM-dd） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    /** 截止日期（含当日，按发布时间过滤；格式 yyyy-MM-dd） */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    /** 标签（精确匹配 fm_thread_tag.tag） */
    private String tag;

    /** 页码（从 1 起） */
    private Long current = 1L;

    /** 每页条数 */
    private Long size = 20L;
}
