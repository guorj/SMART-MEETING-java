AdminModules.register({
  route: '/settings',
  mount: async function (root) {
    const schema = await AdminApi.fetch('/api/v1/admin/system-config/schema');
    const byCat = {};
    schema.forEach(s => {
      if (!byCat[s.category]) byCat[s.category] = [];
      byCat[s.category].push(s);
    });
    let html = '<div class="panel"><button class="primary" id="reload-rt">通知 meeting-server 热加载</button> <button id="show-audit">最近审计</button></div><pre id="audit-out" class="panel hidden"></pre>';
    for (const cat of Object.keys(byCat).sort()) {
      html += '<h3>' + cat + '</h3>';
      for (const s of byCat[cat]) {
        const val = s.currentValue != null ? s.currentValue : s.defaultValue;
        const parsed = s.type === 'BOOLEAN' ? (val === 'true' || val === true) : val;
        if (s.type === 'BOOLEAN') {
          html += '<div class="panel"><label><input type="checkbox" data-key="' + s.key + '" ' + (parsed ? 'checked' : '') + '/> ' + s.description + ' <code>' + s.key + '</code></label> <button data-save="' + s.key + '">保存</button> <button data-reset="' + s.key + '">恢复默认</button></div>';
        } else {
          html += '<div class="panel"><label>' + s.description + ' <code>' + s.key + '</code><input data-key="' + s.key + '" value="' + String(parsed).replace(/"/g, '&quot;') + '" style="width:100%"/></label> <button data-save="' + s.key + '">保存</button> <button data-reset="' + s.key + '">恢复默认</button></div>';
        }
      }
    }
    root.innerHTML = html;
    const saveKey = async (key) => {
      const inp = root.querySelector('[data-key="' + key + '"]');
      let v;
      if (inp.type === 'checkbox') v = JSON.stringify(inp.checked);
      else v = JSON.stringify(inp.value);
      const r = await AdminApi.fetch('/api/v1/admin/system-config/entries/' + encodeURIComponent(key), {
        method: 'PUT', body: JSON.stringify({ valueJson: v })
      });
      alert('已保存' + (r.reloaded ? '，meeting-server 已 reload' : '，reload 未成功'));
    };
    root.querySelectorAll('[data-save]').forEach(btn => { btn.onclick = () => saveKey(btn.getAttribute('data-save')); });
    root.querySelectorAll('[data-reset]').forEach(btn => {
      btn.onclick = async () => {
        const key = btn.getAttribute('data-reset');
        if (!confirm('删除 DB 覆盖，回退 YAML 默认？')) return;
        const r = await AdminApi.fetch('/api/v1/admin/system-config/entries/' + encodeURIComponent(key), { method: 'DELETE' });
        alert('已恢复' + (r.reloaded ? '并已 reload' : ''));
        location.reload();
      };
    });
    document.getElementById('reload-rt').onclick = async () => {
      const r = await AdminApi.fetch('/api/v1/admin/system-config/reload-runtime', { method: 'POST' });
      alert(r.reloaded ? 'reload 成功' : 'reload 失败');
    };
    document.getElementById('show-audit').onclick = async () => {
      const rows = await AdminApi.fetch('/api/v1/admin/system-config/audit?limit=20');
      const el = document.getElementById('audit-out');
      el.classList.remove('hidden');
      el.textContent = JSON.stringify(rows, null, 2);
    };
  }
});
