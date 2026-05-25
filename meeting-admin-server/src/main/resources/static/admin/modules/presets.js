AdminModules.register({
  route: '/presets',
  mount: async function (root) {
    let code = 1;
    let agendaItems = [];
    let docBindings = [];
    let tab = 'table';

    const closeDrawer = () => {
      const b = document.getElementById('preset-drawer-backdrop');
      if (b) b.remove();
    };

    const openDocDrawer = (existing) => {
      closeDrawer();
      const backdrop = document.createElement('div');
      backdrop.id = 'preset-drawer-backdrop';
      backdrop.className = 'drawer-backdrop';
      backdrop.onclick = e => { if (e.target === backdrop) closeDrawer(); };
      const d = document.createElement('div');
      d.className = 'drawer';
      d.onclick = e => e.stopPropagation();
      const ex = existing || {};
      d.innerHTML = `
        <h3>${existing ? '编辑' : '新增'}资料绑定</h3>
        <label>config_name</label><input id="df-name" value="${ex.configName || ''}"/>
        <label>agenda_index</label><input id="df-idx" type="number" value="${ex.agendaIndex ?? 0}"/>
        <label>resource_slot</label><input id="df-slot" type="number" value="${ex.resourceSlot ?? 0}"/>
        <label>config_role</label><select id="df-role"><option>SOURCE</option><option>OUTPUT</option><option>BOTH</option></select>
        <label>bitable_display_mode</label><select id="df-bdm"><option value="">(默认)</option><option>RAW</option><option>GROUPED</option></select>
        <label>feishu_doc_url</label><textarea id="df-url" rows="3">${ex.feishuDocUrl || ''}</textarea>
        <button class="primary" id="df-save">保存</button>
        <button class="secondary" id="df-cancel">取消</button>`;
      if (ex.configRole) d.querySelector('#df-role').value = ex.configRole;
      if (ex.bitableDisplayMode) d.querySelector('#df-bdm').value = ex.bitableDisplayMode;
      backdrop.appendChild(d);
      document.body.appendChild(backdrop);
      d.querySelector('#df-cancel').onclick = closeDrawer;
      d.querySelector('#df-save').onclick = async () => {
        const body = {
          id: ex.id || null,
          configName: d.querySelector('#df-name').value.trim(),
          presetTypeCode: code,
          agendaIndex: parseInt(d.querySelector('#df-idx').value, 10) || 0,
          resourceSlot: parseInt(d.querySelector('#df-slot').value, 10) || 0,
          feishuDocUrl: d.querySelector('#df-url').value.trim(),
          enabled: 1,
          configRole: d.querySelector('#df-role').value,
          bitableDisplayMode: d.querySelector('#df-bdm').value || null
        };
        if (ex.id) {
          await AdminApi.fetch('/api/v1/admin/agenda-config/doc-bindings/' + ex.id, { method: 'PUT', body: JSON.stringify(body) });
        } else {
          await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/doc-bindings', { method: 'POST', body: JSON.stringify(body) });
        }
        closeDrawer();
        await loadBundle();
        render();
      };
    };

    const loadBundle = async () => {
      code = parseInt(document.getElementById('preset-code').value, 10);
      const data = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code);
      agendaItems = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/agenda-items');
      docBindings = data.docBindings || [];
      document.getElementById('host-agenda-json').value = data.hostAgendaJson || '';
    };

    const renderAgendaTable = () => {
      const el = document.getElementById('agenda-table-wrap');
      if (!el) return;
      let html = '<table><tr><th>#</th><th>标题</th><th>分钟</th><th></th><th>排序</th></tr>';
      agendaItems.forEach((row, i) => {
        const rc = row.hasRollCallKeyword ? '<span class="tag">检点</span>' : '';
        html += `<tr data-i="${i}"><td>${i + 1}</td>
          <td><input class="ag-title" value="${(row.title || '').replace(/"/g, '&quot;')}"/> ${rc}</td>
          <td><input class="ag-min" type="number" style="width:4rem" value="${row.minutes || 10}"/></td>
          <td></td>
          <td><button class="secondary ag-up" data-i="${i}">↑</button><button class="secondary ag-down" data-i="${i}">↓</button>
          <button class="danger ag-del" data-i="${i}">删</button></td></tr>`;
      });
      html += '</table><button class="secondary" id="ag-add-row">+ 会序项</button> <button class="primary" id="ag-save-table">保存会序表</button>';
      el.innerHTML = html;
      el.querySelector('#ag-add-row').onclick = () => {
        agendaItems.push({ index: agendaItems.length, title: '新议题', minutes: 10, hasRollCallKeyword: false });
        renderAgendaTable();
      };
      el.querySelector('#ag-save-table').onclick = async () => {
        const rows = [];
        el.querySelectorAll('tr[data-i]').forEach(tr => {
          const title = tr.querySelector('.ag-title').value.trim();
          const minutes = parseInt(tr.querySelector('.ag-min').value, 10) || 10;
          if (title) rows.push({ title, minutes });
        });
        await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/agenda-items', { method: 'PUT', body: JSON.stringify(rows) });
        await loadBundle();
        render();
        alert('会序表已保存');
      };
      el.querySelectorAll('.ag-up').forEach(btn => btn.onclick = () => {
        const i = +btn.dataset.i;
        if (i > 0) { const t = agendaItems[i]; agendaItems[i] = agendaItems[i - 1]; agendaItems[i - 1] = t; renderAgendaTable(); }
      });
      el.querySelectorAll('.ag-down').forEach(btn => btn.onclick = () => {
        const i = +btn.dataset.i;
        if (i < agendaItems.length - 1) { const t = agendaItems[i]; agendaItems[i] = agendaItems[i + 1]; agendaItems[i + 1] = t; renderAgendaTable(); }
      });
      el.querySelectorAll('.ag-del').forEach(btn => btn.onclick = () => {
        agendaItems.splice(+btn.dataset.i, 1);
        renderAgendaTable();
      });
    };

    const render = () => {
      document.getElementById('agenda-table-panel').classList.toggle('hidden', tab !== 'table');
      document.getElementById('agenda-json-panel').classList.toggle('hidden', tab !== 'json');
      document.querySelectorAll('.tabs button').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
      if (tab === 'table') renderAgendaTable();
      const dp = document.getElementById('doc-panel');
      dp.innerHTML = '<h3>资料绑定 <button class="primary" id="doc-add">新增</button></h3><table><tr><th>id</th><th>name</th><th>idx</th><th>role</th><th>bdm</th><th>url</th><th></th></tr>' +
        docBindings.map(d => `<tr><td>${d.id}</td><td>${d.configName || ''}</td><td>${d.agendaIndex}</td><td>${d.configRole}</td><td>${d.bitableDisplayMode || ''}</td>
          <td title="${(d.feishuDocUrl || '').replace(/"/g, '&quot;')}">${((d.feishuDocUrl || '').slice(0, 28))}</td>
          <td><button class="secondary doc-edit" data-id="${d.id}">编辑</button> <button class="danger doc-del" data-id="${d.id}">删</button></td></tr>`).join('') + '</table>';
      dp.querySelector('#doc-add').onclick = () => openDocDrawer(null);
      dp.querySelectorAll('.doc-edit').forEach(b => b.onclick = () => openDocDrawer(docBindings.find(x => String(x.id) === b.dataset.id)));
      dp.querySelectorAll('.doc-del').forEach(b => b.onclick = async () => {
        if (!confirm('删除 ' + b.dataset.id + '?')) return;
        await AdminApi.fetch('/api/v1/admin/agenda-config/doc-bindings/' + b.dataset.id, { method: 'DELETE' });
        await loadBundle();
        render();
      });
    };

    root.innerHTML = `
      <div class="panel toolbar">
        <label>会务 <select id="preset-code"><option value="1">1</option><option value="2">2</option><option value="3">3</option><option value="4">4</option><option value="5">5</option></select></label>
        <button class="primary" id="load-preset">加载</button>
        <button id="validate-preset">校验</button>
        <button id="preview-preset">合并预览</button>
        <button id="refresh-cache">刷新 preset 缓存</button>
        <button id="refresh-meetings-dry">预览刷新未开会（enrich）</button>
        <button class="secondary" id="refresh-meetings">执行刷新未开会（enrich）</button>
      </div>
      <div class="panel">
        <div class="tabs">
          <button data-tab="table" class="active">会序表</button>
          <button data-tab="json">JSON 高级</button>
        </div>
        <div id="agenda-table-panel"><div id="agenda-table-wrap"></div></div>
        <div id="agenda-json-panel" class="hidden">
          <textarea id="host-agenda-json" rows="12" style="width:100%;font-family:monospace"></textarea>
          <button class="primary" id="save-json">保存 JSON</button>
        </div>
      </div>
      <div class="panel" id="doc-panel"></div>
      <pre id="preview-out" class="panel"></pre>
      <pre id="validate-out" class="panel hidden"></pre>`;

    root.querySelectorAll('.tabs button').forEach(b => {
      b.onclick = () => { tab = b.dataset.tab; render(); };
    });
    document.getElementById('preset-code').onchange = async () => { await loadBundle(); render(); };
    document.getElementById('load-preset').onclick = async () => { await loadBundle(); render(); };
    document.getElementById('save-json').onclick = async () => {
      await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code, {
        method: 'PUT', body: JSON.stringify({ presetTypeCode: code, hostAgendaJson: document.getElementById('host-agenda-json').value })
      });
      alert('JSON 已保存');
      await loadBundle();
      render();
    };
    document.getElementById('validate-preset').onclick = async () => {
      const issues = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/validate-agenda', {
        method: 'POST', body: JSON.stringify({ hostAgendaJson: document.getElementById('host-agenda-json').value })
      });
      const vo = document.getElementById('validate-out');
      vo.classList.remove('hidden');
      vo.textContent = issues.length ? issues.join('\n') : '校验通过';
    };
    document.getElementById('preview-preset').onclick = async () => {
      document.getElementById('preview-out').textContent = (await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/preview', { method: 'POST' })).join('\n');
    };
    document.getElementById('refresh-cache').onclick = async () => {
      await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/refresh-cache', { method: 'POST' });
      alert('已通知 meeting-server 刷新 preset Redis 缓存');
    };
    document.getElementById('refresh-meetings-dry').onclick = async () => {
      const r = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/refresh-meetings-host-agenda?dryRun=true', { method: 'POST' });
      alert('将刷新 ' + r.count + ' 场（' + (r.note || 'enrich') + '）\n' + (r.meetingIds || []).join('\n'));
    };
    document.getElementById('refresh-meetings').onclick = async () => {
      if (!confirm('经 meeting-server 合并 preset+doc 写回 ISSUE_COLLECTING/INVITED 会议？')) return;
      const r = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/refresh-meetings-host-agenda?dryRun=false', { method: 'POST' });
      alert('已刷新 ' + r.count + ' 场\n' + (r.note || ''));
    };

    await loadBundle();
    render();
  }
});
