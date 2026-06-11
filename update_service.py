import os, re

svc = r'meeting-server\src\main\java\com\smartmeeting\service\PresetAgendaDocService.java'
with open(svc, 'r', encoding='utf-8') as f:
    content = f.read()

# Add new imports after existing imports
new_imports = """import com.smartmeeting.api.dto.structured.*;
import com.smartmeeting.service.structured.*;
"""
old_import = "import com.smartmeeting.service.feishu.FeishuDocRefs;"
content = content.replace(old_import, old_import + "\n" + new_imports, 1)

# Now update the buildAgendaDocContent method to add structured content
# Find the section where parts are built
old_block = '''            if (ref.canFetchPlainText()) {
                try {
                    String text = feishuService.fetchResourcePlainText(ref);
                    if (text != null && !text.isBlank()) {
                        partBuilder.plainText(text);
                        combined.append('\\u3010').append(FeishuDocRefs.kindLabel(ref.kind())).append("\\u3011\\n")
                                .append(text.trim()).append("\\n\\n");
                    } else {
                        partBuilder.fetchError("资料已读取但正文为空");
                    }
                } catch (BusinessException e) {
                    partBuilder.fetchError(e.getMessage());
                } catch (Exception e) {
                    log.warn("fetch part agendaIndex={} kind={}: {}", agendaIndex, ref.kind(), e.getMessage());
                    partBuilder.fetchError("拉取失败: " + e.getMessage());
                }
            } else {
                partBuilder.fetchError("无法内嵌拉取正文（base 须在 URL 带 table=），请使用下方链接在飞书中打开");
            }'''

new_block = '''            if (ref.canFetchPlainText()) {
                try {
                    String text = feishuService.fetchResourcePlainText(ref);
                    if (text != null && !text.isBlank()) {
                        partBuilder.plainText(text);
                        combined.append('\\u3010').append(FeishuDocRefs.kindLabel(ref.kind())).append("\\u3011\\n")
                                .append(text.trim()).append("\\n\\n");
                    } else {
                        partBuilder.fetchError("资料已读取但正文为空");
                    }
                    // Structured content export
                    try {
                        Object structured = feishuService.fetchResourceStructuredContent(ref);
                        if (structured != null) {
                            String contentType = feishuService.resolveStructuredContentType(ref);
                            partBuilder.contentType(contentType);
                            partBuilder.structuredContent(structured);
                        }
                    } catch (Exception se) {
                        log.warn("structured export failed for agendaIndex={} kind={}: {}", agendaIndex, ref.kind(), se.getMessage());
                    }
                } catch (BusinessException e) {
                    partBuilder.fetchError(e.getMessage());
                } catch (Exception e) {
                    log.warn("fetch part agendaIndex={} kind={}: {}", agendaIndex, ref.kind(), e.getMessage());
                    partBuilder.fetchError("拉取失败: " + e.getMessage());
                }
            } else {
                partBuilder.fetchError("无法内嵌拉取正文（base 须在 URL 带 table=），请使用下方链接在飞书中打开");
            }'''

content = content.replace(old_block, new_block, 1)

with open(svc, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated PresetAgendaDocService")
