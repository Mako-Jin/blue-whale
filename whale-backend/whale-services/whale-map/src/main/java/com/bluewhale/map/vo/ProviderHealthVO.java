package com.bluewhale.map.vo;

import lombok.Data;

import java.util.List;

/**
 * 供应商健康与配额视图。
 *
 * @author BlueWhale
 */
@Data
public class ProviderHealthVO {

    /** 供应商名 */
    private String name;

    /** 是否已配置（Key 与基址齐全） */
    private boolean configured;

    /** 支持的能力 */
    private List<String> capabilities;

    /** 是否被某能力选为首选 */
    private boolean preferred;

    /** 今日已用次数 */
    private int usedToday;

    /** 今日上限（0 表示不限） */
    private int limitToday;

    /** 今日剩余（上限为 0 时返回 -1，表示不限） */
    private int remaining;
}
