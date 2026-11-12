-- Wave     : 6.43（论坛服务 M1 实机验收修复）
-- 依据     : 实机验证发现——软删改写「原编号#D{id}」时 Data too long：
--            thread_no 原值 17 字符（FM-yyyyMMdd-XXXXX）+ "#D" + 雪花ID 19 位 = 38 字符 > VARCHAR(32)。
--            表现是删除接口 500；若仅靠单测/静态检查永远发现不了（Mock 不校验列宽）。
-- 修正     : 与 board.code 同宽（64）。V2 已应用的库执行本迁移即可，无需重建。

ALTER TABLE fm_thread
  MODIFY COLUMN thread_no VARCHAR(64) NOT NULL
  COMMENT '帖子编号（FM-yyyyMMdd-XXXX；软删改写为 原编号#D{id}，最长 17+2+19=38 字符）';
