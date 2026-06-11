import os
css_path = os.path.join(r'D:\openclaw\workspace-clone\projects\smart-meeting-java', 'meeting-server', 'src', 'main', 'resources', 'static', 'styles', 'host-meeting.css')
new_css = '\n/* ===== Structured content rendering ===== */\n.feishu-structured-img { max-width: 100%; border-radius: 6px; margin: 8px 0; }\n.ppt-slide-panel, .pdf-page-panel { padding: 14px 16px; }\n.ppt-slide-viewer, .pdf-page-viewer { text-align: center; }\n.bitable-progress-bar { height: 6px; border-radius: 3px; }\n.feishu-kind-badge { display: inline-block; font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 4px; margin-bottom: 8px; }\n.feishu-kind-badge.feishu-kind-docx { background: #e3f2fd; color: #1565c0; }\n.feishu-kind-badge.feishu-kind-base { background: #e8f5e9; color: #2e7d32; }\n.feishu-kind-badge.feishu-kind-wiki { background: #f3e5f5; color: #7b1fa2; }\n'
with open(css_path, 'a', encoding='utf-8') as f:
    f.write(new_css)
print('CSS appended')