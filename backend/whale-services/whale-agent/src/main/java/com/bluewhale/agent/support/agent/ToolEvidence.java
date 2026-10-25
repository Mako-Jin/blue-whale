package com.bluewhale.agent.support.agent;

/**
 * 工具取证得到的一条证据（与 {@code Citation} 同构，编号由编排层统一分配）。
 *
 * @param kind      证据类型：{@code fact} / {@code chunk}
 * @param path      仓库相对路径
 * @param title     标题
 * @param startLine 起始行
 * @param endLine   结束行
 * @param snippet   片段
 * @param factType  事实类型（fact 专用）
 * @param factKey   事实键（fact 专用）
 * @param score     检索分数（chunk 专用）
 * @param fileId    文件服务文件ID（业务文件/档案知识块专用）
 * @author BlueWhale
 */
public record ToolEvidence(String kind, String path, String title, Integer startLine, Integer endLine,
                           String snippet, String factType, String factKey, Double score, Long fileId) {

    /**
     * 去重键：同一路径行区间或同一事实只保留一次。
     *
     * @return 去重键
     */
    public String dedupeKey() {
        if ("fact".equals(kind)) {
            return "fact|" + factType + "|" + factKey;
        }
        return "chunk|" + path + "|" + startLine + "|" + endLine;
    }
}
