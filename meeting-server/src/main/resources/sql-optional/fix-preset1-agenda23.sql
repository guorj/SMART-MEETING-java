-- 一次性：将 preset=1 会序 2/3 飞书链接恢复为 schema-data 默认值（勿随应用启动执行）
-- 执行: ProdSchemaMigrate --apply sql-optional/fix-preset1-agenda23.sql

UPDATE int_matter_progress_doc_config
SET feishu_doc_url = 'https://ovjde0k7vc1.feishu.cn/base/SnsXbyQ1Qa57fCsI8mrcRAIbnve?table=tbl7viO4AJ4ebD0B&view=vew3qfhSyY',
    updated_at = CURRENT_TIMESTAMP
WHERE preset_type_code = 1 AND agenda_index = 1 AND config_name = 'preset1-comp-agenda-01';

UPDATE int_matter_progress_doc_config
SET feishu_doc_url = 'https://ovjde0k7vc1.feishu.cn/docx/CzrSd90yMoKnsoxR4xGcEI5Fncf',
    updated_at = CURRENT_TIMESTAMP
WHERE preset_type_code = 1 AND agenda_index = 2 AND config_name = 'preset1-comp-agenda-02';
