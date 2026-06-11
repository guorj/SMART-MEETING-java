import os, sys

# Fix TextRunDto leading space issue
tr_path = r'D:\openclaw\workspace-clone\projects\smart-meeting-java\meeting-server\src\main\java\com\smartmeeting\api\dto\structured\TextRunDto.java'
if os.path.exists(tr_path):
    with open(tr_path, 'r', encoding='utf-8') as f:
        content = f.read()
    if content.startswith(' package'):
        content = content.lstrip()
        with open(tr_path, 'w', encoding='utf-8') as f:
            f.write(content)
        print('Fixed TextRunDto leading space')

# Check for any compilation issues in key files
files_to_check = [
    r'D:\openclaw\workspace-clone\projects\smart-meeting-java\meeting-server\src\main\java\com\smartmeeting\api\dto\AgendaDocPartDto.java',
    r'D:\openclaw\workspace-clone\projects\smart-meeting-java\meeting-server\src\main\java\com\smartmeeting\api\dto\AgendaDocContentResponse.java',
    r'D:\openclaw\workspace-clone\projects\smart-meeting-java\meeting-server\src\main\java\com\smartmeeting\service\PresetAgendaDocService.java',
    r'D:\openclaw\workspace-clone\projects\smart-meeting-java\meeting-server\src\main\java\com\smartmeeting\service\FeishuService.java',
    r'D:\openclaw\workspace-clone\projects\smart-meeting-java\meeting-server\src\main\java\com\smartmeeting\api\controller\MeetingController.java',
]
for fp in files_to_check:
    if os.path.exists(fp):
        with open(fp, 'r', encoding='utf-8') as f:
            c = f.read()
        # Quick sanity: check for balanced braces
        opens = c.count('{')
        closes = c.count('}')
        status = 'OK' if opens == closes else f'MISMATCH {{={opens} }}={closes}'
        print(f'{os.path.basename(fp)}: {status}')
    else:
        print(f'{os.path.basename(fp)}: MISSING')

# Check structured DTOs
dto_dir = r'D:\openclaw\workspace-clone\projects\smart-meeting-java\meeting-server\src\main\java\com\smartmeeting\api\dto\structured'
for name in sorted(os.listdir(dto_dir)):
    if name.endswith('.java'):
        with open(os.path.join(dto_dir, name), 'r', encoding='utf-8') as f:
            c = f.read()
        opens = c.count('{')
        closes = c.count('}')
        status = 'OK' if opens == closes else f'MISMATCH'
        print(f'  DTO {name}: {status}')

# Check structured exporters
exp_dir = r'D:\openclaw\workspace-clone\projects\smart-meeting-java\meeting-server\src\main\java\com\smartmeeting\service\structured'
if os.path.exists(exp_dir):
    for name in sorted(os.listdir(exp_dir)):
        if name.endswith('.java'):
            with open(os.path.join(exp_dir, name), 'r', encoding='utf-8') as f:
                c = f.read()
            opens = c.count('{')
            closes = c.count('}')
            status = 'OK' if opens == closes else f'MISMATCH'
            print(f'  Exp {name}: {status}')

print('Done checking')