import subprocess
r = subprocess.run(
    [r'D:\apache-maven-3.9.15\bin\mvn.cmd', 'compile', '-q'],
    capture_output=True, text=True,
    cwd=r'D:\openclaw\workspace-clone\projects\smart-meeting-java'
)
print('STDOUT:', r.stdout[:3000] if r.stdout else '(empty)')
print('STDERR:', r.stderr[:3000] if r.stderr else '(empty)')
print('RC:', r.returncode)
