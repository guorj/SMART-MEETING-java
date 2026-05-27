AdminModules.register({
  route: '/presets',
  mount: async function (root) {
    let code = 1;
    let bundleItems = [];
    let hostAgendaJson = '';
    let tab = 'table';
    let editingDocKey = null;
    let saveTimer = null;
    let saving = false;
    let skipInlineCommit = false;

    const esc = s => (s || '').replace(/"/g, '&quot;').replace(/</g, '&lt;');
    const docKey = (i, bi) => i + '-' + bi;
    const roleLabel = r => ({ SOURCE: '源资料', OUTPUT: '产出', BOTH: '双向' }[r] || r || '—');

    const setAutosaveStatus = (state, text) => {
      const el = document.getElementById('autosave-status');
      if (!el) return;
      el.className = 'autosave-status autosave-' + state;
      el.textContent = text;
    };

    const syncAgendaFieldsFromDom = () => {
      const wrap = document.getElementById('agenda-table-wrap');
      if (!wrap) return;
      wrap.querySelectorAll('.ag-row').forEach(tr => {
        const i = parseInt(tr.dataset.i, 10);
        if (!bundleItems[i]) return;
        const titleEl = tr.querySelector('.ag-title');
        const minEl = tr.querySelector('.ag-min');
        if (titleEl) bundleItems[i].title = titleEl.value.trim();
        if (minEl) bundleItems[i].minutes = parseInt(minEl.value, 10) || 10;
      });
    };

    const nextResourceSlot = bindings => {
      if (!bindings || bindings.length === 0) return 0;
      let max = -1;
      bindings.forEach(b => {
        const s = b.resourceSlot != null ? parseInt(b.resourceSlot, 10) : 0;
        if (!Number.isNaN(s) && s > max) max = s;
      });
      return max + 1;
    };

    const normalizeBindingSlots = bindings => {
      const used = new Set();
      return (bindings || []).map(b => {
        let slot = b.resourceSlot != null ? parseInt(b.resourceSlot, 10) : 0;
        if (Number.isNaN(slot) || slot < 0) slot = 0;
        while (used.has(slot)) slot++;
        used.add(slot);
        return Object.assign({}, b, { resourceSlot: slot });
      });
    };

    const buildPayload = () => {
      syncAgendaFieldsFromDom();
      return bundleItems
        .filter(r => r.title && r.title.trim())
        .map(r => ({
          title: r.title.trim(),
          minutes: r.minutes || 10,
          bindings: normalizeBindingSlots(r.bindings || []).map(b => ({
            id: b.id || null,
            configName: b.configName,
            resourceSlot: b.resourceSlot,
            feishuDocUrl: b.feishuDocUrl || '',
            enabled: b.enabled ?? 1,
            configRole: b.configRole || 'SOURCE',
            bitableDisplayMode: b.bitableDisplayMode || null
          }))
        }));
    };

    const autoSaveBundle = () => {
      clearTimeout(saveTimer);
      saveTimer = setTimeout(async () => {
        if (saving || editingDocKey) return;
        saving = true;
        setAutosaveStatus('saving', '保存中…');
        try {
          await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/agenda-bundle', {
            method: 'PUT', body: JSON.stringify(buildPayload())
          });
          const fresh = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/agenda-bundle');
          bundleItems = fresh;
          setAutosaveStatus('saved', '已自动保存');
          renderAgendaTable();
        } catch (e) {
          setAutosaveStatus('error', '保存失败: ' + (e.message || ''));
        } finally {
          saving = false;
        }
      }, 500);
    };

    const startDocEdit = (agendaIndex, bindingIndex) => {
      editingDocKey = docKey(agendaIndex, bindingIndex);
      renderAgendaTable();
      requestAnimationFrame(() => {
        const tile = document.querySelector('.doc-tile-editing');
        const focusEl = tile && (tile.querySelector('.inl-url') || tile.querySelector('.inl-name'));
        if (focusEl) focusEl.focus();
      });
    };

    const addEmptyBinding = agendaIndex => {
      const row = bundleItems[agendaIndex];
      if (!row.bindings) row.bindings = [];
      const slot = nextResourceSlot(row.bindings);
      row.bindings.push({
        configName: '',
        resourceSlot: slot,
        feishuDocUrl: '',
        enabled: 1,
        configRole: 'SOURCE',
        bitableDisplayMode: null
      });
      return row.bindings.length - 1;
    };

    const readInlineForm = tile => ({
      id: tile.dataset.id ? parseInt(tile.dataset.id, 10) : null,
      configName: tile.querySelector('.inl-name').value.trim(),
      resourceSlot: parseInt(tile.querySelector('.inl-slot').value, 10) || 0,
      feishuDocUrl: tile.querySelector('.inl-url').value.trim(),
      enabled: 1,
      configRole: tile.querySelector('.inl-role').value,
      bitableDisplayMode: tile.querySelector('.inl-bdm').value || null
    });

    const commitInlineEdit = async (agendaIndex, bindingIndex, cancel) => {
      const tile = document.querySelector(`.doc-tile-editing[data-i="${agendaIndex}"][data-bi="${bindingIndex}"]`);
      editingDocKey = null;
      if (!tile) {
        renderAgendaTable();
        return;
      }
      if (cancel) {
        const b = bundleItems[agendaIndex].bindings[bindingIndex];
        if (!b.id && !b.configName && !b.feishuDocUrl) {
          bundleItems[agendaIndex].bindings.splice(bindingIndex, 1);
        }
        renderAgendaTable();
        return;
      }
      const binding = readInlineForm(tile);
      if (!binding.configName && !binding.feishuDocUrl) {
        bundleItems[agendaIndex].bindings.splice(bindingIndex, 1);
        renderAgendaTable();
        return;
      }
      if (!binding.configName) {
        binding.configName = 'doc-' + (agendaIndex + 1) + '-' + (bindingIndex + 1);
      }
      const prev = bundleItems[agendaIndex].bindings[bindingIndex] || {};
      bundleItems[agendaIndex].bindings[bindingIndex] = Object.assign({}, prev, binding);
      renderAgendaTable();
      saving = true;
      setAutosaveStatus('saving', '保存中…');
      try {
        await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/agenda-bundle', {
          method: 'PUT', body: JSON.stringify(buildPayload())
        });
        bundleItems = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/agenda-bundle');
        setAutosaveStatus('saved', '已自动保存');
        renderAgendaTable();
      } catch (e) {
        setAutosaveStatus('error', '保存失败: ' + (e.message || ''));
      } finally {
        saving = false;
      }
    };

    const renderDocTileEdit = (d, agendaIndex, bindingIndex) => {
      const role = d.configRole || 'SOURCE';
      const bdm = d.bitableDisplayMode || '';
      const idAttr = d.id ? ` data-id="${d.id}"` : '';
      return `<div class="doc-tile doc-tile-editing" data-i="${agendaIndex}" data-bi="${bindingIndex}"${idAttr}>
        <div class="doc-inline-form">
          <div class="doc-inline-row">
            <label>配置名</label>
            <input class="inl-name" type="text" placeholder="如 preset1-weekly-report-out" value="${esc(d.configName)}"/>
            <p class="doc-inline-hint-block">${AdminHints.presets.configName}</p>
          </div>
          <div class="doc-inline-row doc-inline-meta">
            <label>角色</label>
            <select class="inl-role" title="SOURCE/OUTPUT/BOTH">
              <option value="SOURCE" ${role === 'SOURCE' ? 'selected' : ''}>源资料</option>
              <option value="OUTPUT" ${role === 'OUTPUT' ? 'selected' : ''}>产出</option>
              <option value="BOTH" ${role === 'BOTH' ? 'selected' : ''}>双向</option>
            </select>
            <label>槽位</label>
            <input class="inl-slot" type="number" min="0" value="${d.resourceSlot ?? 0}" title="${AdminHints.presets.resourceSlot.replace(/"/g, '&quot;')}"/>
            <label>展示</label>
            <select class="inl-bdm" title="多维表解析方式">
              <option value="" ${!bdm ? 'selected' : ''}>默认</option>
              <option value="RAW" ${bdm === 'RAW' ? 'selected' : ''}>RAW</option>
              <option value="GROUPED" ${bdm === 'GROUPED' ? 'selected' : ''}>GROUPED</option>
            </select>
          </div>
          <p class="doc-inline-hint-block">角色：${AdminHints.presets.configRole.SOURCE} ${AdminHints.presets.configRole.OUTPUT} ${AdminHints.presets.configRole.BOTH} · 槽位：${AdminHints.presets.resourceSlot} · 展示：${AdminHints.presets.bitableDisplayMode['']} / ${AdminHints.presets.bitableDisplayMode.RAW} / ${AdminHints.presets.bitableDisplayMode.GROUPED}</p>
          <div class="doc-inline-row">
            <label>飞书链接</label>
            <textarea class="inl-url code-area" rows="3" placeholder="粘贴飞书文档 / 多维表链接">${esc(d.feishuDocUrl)}</textarea>
            <p class="doc-inline-hint-block">${AdminHints.presets.feishuUrl}</p>
          </div>
          <p class="doc-inline-hint muted">点击外侧或按 Tab 离开即自动保存 · Esc 取消</p>
        </div>
        <div class="doc-tile-actions">
          <button type="button" class="danger ag-doc-rm" data-i="${agendaIndex}" data-bi="${bindingIndex}">删除</button>
        </div>
      </div>`;
    };

    const renderDocTileView = (d, agendaIndex, bindingIndex) => {
      const url = (d.feishuDocUrl || '').trim();
      const bdm = d.bitableDisplayMode
        ? `<span class="meta-pill meta-bdm">${esc(d.bitableDisplayMode)}</span>` : '';
      const role = (d.configRole || 'SOURCE').toLowerCase();
      const name = d.configName ? esc(d.configName) : '<em class="muted">未命名配置</em>';
      const urlInner = url
        ? `<a class="doc-url-link" href="${esc(url)}" target="_blank" rel="noopener noreferrer" onclick="event.stopPropagation()">${esc(url)}</a>`
        : '<span class="doc-url-placeholder">点击配置飞书链接</span>';
      const linkBtns = url
        ? `<button type="button" class="secondary btn-link-open" data-url="${esc(url)}">打开</button>
           <button type="button" class="secondary btn-link-copy" data-url="${esc(url)}">复制</button>`
        : '';
      return `<div class="doc-tile doc-tile-view doc-tile-clickable" data-i="${agendaIndex}" data-bi="${bindingIndex}" tabindex="0" title="点击编辑">
        <div class="doc-tile-main">
          <div class="doc-tile-head">
            <span class="doc-tile-name">${name}</span>
            <span class="meta-pill meta-role meta-role-${role}">${esc(roleLabel(d.configRole))}</span>
            <span class="meta-pill">槽位 ${d.resourceSlot ?? 0}</span>
            ${bdm}
          </div>
          <div class="doc-tile-url doc-editable">${urlInner}</div>
        </div>
        <div class="doc-tile-actions" onclick="event.stopPropagation()">
          ${linkBtns}
          <button type="button" class="secondary ag-doc-edit" data-i="${agendaIndex}" data-bi="${bindingIndex}">编辑</button>
          <button type="button" class="danger ag-doc-rm" data-i="${agendaIndex}" data-bi="${bindingIndex}">删除</button>
        </div>
      </div>`;
    };

    const renderDocTile = (d, agendaIndex, bindingIndex) => {
      if (editingDocKey === docKey(agendaIndex, bindingIndex)) {
        return renderDocTileEdit(d, agendaIndex, bindingIndex);
      }
      return renderDocTileView(d, agendaIndex, bindingIndex);
    };

    const bindAgendaTableEvents = el => {
      el.querySelector('#ag-add-row').onclick = () => {
        bundleItems.push({ title: '新议题', minutes: 10, hasRollCallKeyword: false, bindings: [] });
        renderAgendaTable();
        autoSaveBundle();
      };
      el.querySelectorAll('.ag-title, .ag-min').forEach(inp => {
        inp.addEventListener('blur', () => autoSaveBundle());
      });
      el.querySelectorAll('.ag-up').forEach(btn => btn.onclick = () => {
        const i = +btn.dataset.i;
        if (i > 0) {
          const t = bundleItems[i];
          bundleItems[i] = bundleItems[i - 1];
          bundleItems[i - 1] = t;
          renderAgendaTable();
          autoSaveBundle();
        }
      });
      el.querySelectorAll('.ag-down').forEach(btn => btn.onclick = () => {
        const i = +btn.dataset.i;
        if (i < bundleItems.length - 1) {
          const t = bundleItems[i];
          bundleItems[i] = bundleItems[i + 1];
          bundleItems[i + 1] = t;
          renderAgendaTable();
          autoSaveBundle();
        }
      });
      el.querySelectorAll('.ag-del').forEach(btn => btn.onclick = async () => {
        if (!confirm('删除该会序项及其全部资料？')) return;
        bundleItems.splice(+btn.dataset.i, 1);
        editingDocKey = null;
        renderAgendaTable();
        saving = true;
        setAutosaveStatus('saving', '保存中…');
        try {
          await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/agenda-bundle', {
            method: 'PUT', body: JSON.stringify(buildPayload())
          });
          bundleItems = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/agenda-bundle');
          setAutosaveStatus('saved', '已自动保存');
          renderAgendaTable();
        } catch (e) {
          setAutosaveStatus('error', '保存失败: ' + (e.message || ''));
        } finally {
          saving = false;
        }
      });
      el.querySelectorAll('.ag-doc-add, .doc-tile-empty.doc-editable').forEach(btn => {
        btn.onclick = () => {
          const i = +btn.dataset.i;
          const bi = addEmptyBinding(i);
          startDocEdit(i, bi);
        };
      });
      el.querySelectorAll('.doc-tile-clickable').forEach(tile => {
        tile.onclick = () => startDocEdit(+tile.dataset.i, +tile.dataset.bi);
        tile.onkeydown = e => {
          if (e.key === 'Enter') startDocEdit(+tile.dataset.i, +tile.dataset.bi);
        };
      });
      el.querySelectorAll('.ag-doc-edit').forEach(btn => {
        btn.onclick = e => {
          e.stopPropagation();
          startDocEdit(+btn.dataset.i, +btn.dataset.bi);
        };
      });
      el.querySelectorAll('.doc-tile-editing').forEach(tile => {
        const i = +tile.dataset.i;
        const bi = +tile.dataset.bi;
        tile.addEventListener('focusout', e => {
          if (tile.contains(e.relatedTarget)) return;
          setTimeout(() => {
            if (skipInlineCommit) {
              skipInlineCommit = false;
              return;
            }
            if (editingDocKey === docKey(i, bi)) commitInlineEdit(i, bi, false);
          }, 80);
        });
        tile.addEventListener('keydown', e => {
          if (e.key === 'Escape') {
            e.preventDefault();
            commitInlineEdit(i, bi, true);
          }
        });
      });
      el.querySelectorAll('.ag-doc-rm').forEach(btn => {
        btn.onmousedown = e => {
          e.preventDefault();
          skipInlineCommit = true;
        };
        btn.onclick = async e => {
        e.stopPropagation();
        if (!confirm('删除该资料绑定？')) return;
        const i = +btn.dataset.i;
        const bi = +btn.dataset.bi;
        editingDocKey = null;
        bundleItems[i].bindings.splice(bi, 1);
        renderAgendaTable();
        saving = true;
        setAutosaveStatus('saving', '保存中…');
        try {
          await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/agenda-bundle', {
            method: 'PUT', body: JSON.stringify(buildPayload())
          });
          bundleItems = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/agenda-bundle');
          setAutosaveStatus('saved', '已自动保存');
          renderAgendaTable();
        } catch (err) {
          setAutosaveStatus('error', '保存失败: ' + (err.message || ''));
        } finally {
          saving = false;
        }
      };
      });
      el.querySelectorAll('.btn-link-open').forEach(btn => btn.onclick = e => {
        e.stopPropagation();
        window.open(btn.dataset.url, '_blank', 'noopener');
      });
      el.querySelectorAll('.btn-link-copy').forEach(btn => btn.onclick = async e => {
        e.stopPropagation();
        try {
          await navigator.clipboard.writeText(btn.dataset.url);
          const orig = btn.textContent;
          btn.textContent = '已复制';
          setTimeout(() => { btn.textContent = orig; }, 1200);
        } catch {
          alert('复制失败');
        }
      });
    };

    const loadBundle = async () => {
      code = parseInt(document.getElementById('preset-code').value, 10);
      const preset = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code);
      hostAgendaJson = preset.hostAgendaJson || '';
      bundleItems = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/agenda-bundle');
      editingDocKey = null;
      const jsonEl = document.getElementById('host-agenda-json');
      if (jsonEl) jsonEl.value = hostAgendaJson;
    };

    const renderAgendaTable = () => {
      const el = document.getElementById('agenda-table-wrap');
      if (!el) return;
      if (bundleItems.length === 0) {
        el.innerHTML = '<div class="agenda-empty">暂无会序项，点击下方添加</div><div class="agenda-footer"><button type="button" class="ghost" id="ag-add-row">+ 会序项</button></div>';
        bindAgendaTableEvents(el);
        return;
      }
      const totalMin = bundleItems.reduce((s, r) => s + (r.minutes || 0), 0);
      let html = `<div class="agenda-summary"><span>共 ${bundleItems.length} 项会序 · 约 ${totalMin} 分钟</span><span id="autosave-status" class="autosave-status autosave-idle">修改后自动保存</span></div>`;
      html += '<div class="agenda-timeline">';
      bundleItems.forEach((row, i) => {
        const tags = [];
        if (row.hasRollCallKeyword) tags.push('<span class="tag">检点</span>');
        if (row.hasOrphanDocs) tags.push('<span class="tag tag-warn">含未挂载资料</span>');
        const tagHtml = tags.length ? `<div class="agenda-tags">${tags.join('')}</div>` : '';
        const bindings = row.bindings || [];
        html += `<article class="agenda-card ag-row" data-i="${i}">
          <div class="agenda-rail">
            <span class="agenda-num">${i + 1}</span>
            ${i < bundleItems.length - 1 ? '<span class="agenda-rail-line" aria-hidden="true"></span>' : ''}
          </div>
          <div class="agenda-body">
            <header class="agenda-card-head">
              <input class="ag-title field-grow" placeholder="会序标题" title="${AdminHints.presets.agendaTitle.replace(/"/g, '&quot;')}" value="${esc(row.title)}"/>
              <div class="agenda-meta">
                <label class="agenda-min-label" title="${AdminHints.presets.agendaMinutes.replace(/"/g, '&quot;')}"><span>时长</span>
                  <input class="ag-min" type="number" min="1" max="999" value="${row.minutes || 10}"/><span class="agenda-min-unit">分钟</span>
                </label>
                <div class="btn-group btn-group-icon">
                  <button type="button" class="secondary ag-up" data-i="${i}" ${i === 0 ? 'disabled' : ''}>↑</button>
                  <button type="button" class="secondary ag-down" data-i="${i}" ${i === bundleItems.length - 1 ? 'disabled' : ''}>↓</button>
                  <button type="button" class="danger ag-del" data-i="${i}">删除会序</button>
                </div>
              </div>
              ${tagHtml}
            </header>
            <section class="agenda-card-docs">
              <div class="agenda-doc-toolbar">
                <span class="agenda-doc-label">资料绑定 <em>${bindings.length}</em></span>
                <button type="button" class="ghost ag-doc-add" data-i="${i}">+ 添加资料</button>
              </div>`;
        if (bindings.length === 0) {
          html += `<div class="doc-tile-empty doc-editable" data-i="${i}" tabindex="0">点击配置飞书资料</div>`;
        } else {
          html += '<div class="doc-tile-list">';
          bindings.forEach((d, bi) => { html += renderDocTile(d, i, bi); });
          html += '</div>';
        }
        html += '</section></div></article>';
      });
      html += '</div><div class="agenda-footer"><button type="button" class="ghost" id="ag-add-row">+ 会序项</button></div>';
      el.innerHTML = html;
      bindAgendaTableEvents(el);
    };

    const render = () => {
      document.getElementById('agenda-table-panel').classList.toggle('hidden', tab !== 'table');
      document.getElementById('agenda-json-panel').classList.toggle('hidden', tab !== 'json');
      document.querySelectorAll('.tabs button').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
      if (tab === 'table') renderAgendaTable();
    };

    root.innerHTML = `
      <div class="panel">
        <div class="panel-head"><h2>会务预设</h2><p>点击资料区域直接编辑，失焦后自动保存；会序标题/时长修改后亦会自动保存。资料「角色」决定 weekly-jobs 能否引用为源/产出。</p></div>
        <div class="toolbar toolbar-split">
          <div class="toolbar-row">
            <label class="field-inline" title="${AdminHints.presets.presetCode.replace(/"/g, '&quot;')}">会务类型<select id="preset-code"><option value="1">1</option><option value="2">2</option><option value="3">3</option><option value="4">4</option><option value="5">5</option></select></label>
            <button type="button" class="primary" id="load-preset">加载</button>
            <button type="button" class="secondary" id="validate-preset">校验会序</button>
            <button type="button" class="secondary" id="preview-preset">合并预览</button>
          </div>
          <hr class="toolbar-divider"/>
          <div class="toolbar-row actions-secondary">
            <button type="button" class="secondary" id="refresh-cache">刷新 preset 缓存</button>
            <button type="button" class="secondary" id="refresh-meetings-dry">预览刷新未开会</button>
            <button type="button" class="secondary" id="refresh-meetings">执行刷新未开会</button>
          </div>
        </div>
      </div>
      <div class="panel">
        <div class="tabs">
          <button type="button" data-tab="table" class="active">会序与资料</button>
          <button type="button" data-tab="json">JSON 高级</button>
        </div>
        <div id="agenda-table-panel"><div id="agenda-table-wrap"></div></div>
        <div id="agenda-json-panel" class="hidden">
          <p class="form-hint">${AdminHints.presets.hostAgendaJson}</p>
          <textarea id="host-agenda-json" class="code-area" rows="14"></textarea>
          <p style="margin-top:0.75rem"><button type="button" class="primary" id="save-json">保存 JSON</button></p>
        </div>
      </div>
      <pre id="preview-out" class="panel hidden"></pre>
      <pre id="validate-out" class="panel hidden"></pre>`;

    root.querySelectorAll('.tabs button').forEach(b => {
      b.onclick = () => { tab = b.dataset.tab; render(); };
    });
    document.getElementById('preset-code').onchange = async () => { await loadBundle(); render(); };
    document.getElementById('load-preset').onclick = async () => { await loadBundle(); render(); setAutosaveStatus('idle', '已加载'); };
    document.getElementById('save-json').onclick = async () => {
      if (!confirm('仅保存会序 JSON，资料 agenda_index 不会联动。继续？')) return;
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
      const out = document.getElementById('preview-out');
      out.classList.remove('hidden');
      out.textContent = (await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/preview', { method: 'POST' })).join('\n');
    };
    document.getElementById('refresh-cache').onclick = async () => {
      await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/refresh-cache', { method: 'POST' });
      alert('已通知 meeting-server 刷新 preset 缓存');
    };
    document.getElementById('refresh-meetings-dry').onclick = async () => {
      const r = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/refresh-meetings-host-agenda?dryRun=true', { method: 'POST' });
      alert('将刷新 ' + r.count + ' 场\n' + (r.meetingIds || []).join('\n'));
    };
    document.getElementById('refresh-meetings').onclick = async () => {
      if (!confirm('写回未开始会议 host_agenda？')) return;
      const r = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/refresh-meetings-host-agenda?dryRun=false', { method: 'POST' });
      alert('已刷新 ' + r.count + ' 场');
    };

    await loadBundle();
    render();
  }
});
