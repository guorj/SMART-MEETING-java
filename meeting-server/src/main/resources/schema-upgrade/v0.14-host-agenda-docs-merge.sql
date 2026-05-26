-- =============================================================================
-- v0.14 会序资料并入 host_agenda JSON v2（items[].docs[]）
-- =============================================================================
-- 说明：JSON 合并需应用层逻辑，本脚本仅作文档锚点；请执行 Java 迁移（幂等）：
--
--   cd meeting-server
--   mvn test-compile exec:java -Dexec.mainClass=com.smartmeeting.tools.HostAgendaV2DataMigrate -Dexec.classpathScope=test
--   mvn test-compile exec:java -Dexec.mainClass=com.smartmeeting.tools.HostAgendaV2DataMigrate -Dexec.classpathScope=test -Dexec.args="--apply"
--
-- 行为：
--   - preset 1–5：若 host_agenda.version != 2，将 int_matter_progress_doc_config 同行合并进 items[].docs[]
--   - legacy config_name=default（preset NULL）挂到 preset 1 / agenda_index=0（若有 URL）
--   - 已 version=2 的 preset 跳过
--
-- 发布顺序：先部署能读 v1+v2 的代码 → 本迁移 --apply → 再停写旧表 → v0.15 DROP
-- =============================================================================

SELECT 'Run HostAgendaV2DataMigrate --apply for data merge; this SQL file is a version marker only' AS _v014_note;
