import re, os
html_path = r'D:\openclaw\workspace-clone\projects\smart-meeting-java\meeting-server\src\main\resources\static\host-meeting.html'
with open(html_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1) Update renderAgendaDocParts to try structured content first
old_render_part = 'if (p.plainText) {'
new_render_part = 'if (p.structuredContent && p.contentType && renderStructuredContent(body, p, opts)) { /* structured rendered */ } else if (p.plainText) {'
if old_render_part in content and 'p.structuredContent && p.contentType && renderStructuredContent' not in content:
    # Find the occurrence inside renderAgendaDocParts (not other functions)
    idx = content.find('function renderAgendaDocParts')
    if idx >= 0:
        sub = content[idx:]
        sub_idx = sub.find(old_render_part)
        if sub_idx >= 0:
            abs_idx = idx + sub_idx
            content = content[:abs_idx] + new_render_part + content[abs_idx + len(old_render_part):]
            print('UPDATED renderAgendaDocParts')
        else:
            print('NOT_FOUND renderAgendaDocParts.plainText')
    else:
        print('NOT_FOUND renderAgendaDocParts')
else:
    print('SKIPPED renderAgendaDocParts - already updated or marker not found')

# 2) Update the main agenda-doc-content fetch handler to pass meetingId in opts
old_fetch = "renderAgendaDocParts(mainDocEl, data.parts, {useMainPanel: true});"
new_fetch = "renderAgendaDocParts(mainDocEl, data.parts, {useMainPanel: true, meetingId: meetingId});"
if old_fetch in content:
    content = content.replace(old_fetch, new_fetch)
    print('UPDATED fetch handler meetingId')

# 3) Also update any other renderAgendaDocParts call
old_fetch2 = "renderAgendaDocParts(mainDocEl, resp.parts, {useMainPanel: true});"
new_fetch2 = "renderAgendaDocParts(mainDocEl, resp.parts, {useMainPanel: true, meetingId: meetingId});"
if old_fetch2 in content:
    content = content.replace(old_fetch2, new_fetch2)
    print('UPDATED fetch handler2 meetingId')

with open(html_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('DONE')