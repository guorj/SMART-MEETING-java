AdminModules.register({
  route: '/presets',
  mount: async function (root) {
    let code = 1;
    let presetOptions = [];
    let bundleItems = [];
    let hostAgendaJson = '';
    let presetMeta = {};
    let userOptions = [];
    let userNameByFeishuId = {};
    let metaParticipants = [];
    let tab = 'table';
    let editingDocKey = null;
    let saveTimer = null;
    let metaSaveTimer = null;
    let saving = false;
    let metaSaving = false;
    let skipInlineCommit = false;
    const materialBlobCache = {};

    const esc = s => (s || '').replace(/"/g, '&quot;').replace(/</g, '&lt;');
    const docKey = (i, bi) => i + '-' + bi;
    const isLocalBinding = b => !!(b && String(b.storageKind || '').toUpperCase() === 'LOCAL' && (b.fileId || '').trim());
    const bindingHasContent = b => isLocalBinding(b) || !!((b && b.feishuDocUrl) || '').trim();
    const isImageMime = m => String(m || '').toLowerCase().startsWith('image/');
    const materialAdminUrl = fileId => '/api/v1/admin/agenda-config/materials/' + encodeURIComponent(fileId);
    const fetchMaterialBlobUrl = async fileId => {
      if (!fileId) return '';
      if (materialBlobCache[fileId]) return materialBlobCache[fileId];
      const res = await fetch(materialAdminUrl(fileId), { headers: { 'X-Admin-Token': AdminApi.token() } });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      materialBlobCache[fileId] = url;
      return url;
    };
    const renderLocalPreviewHtml = (b, asyncThumb) => {
      if (!isLocalBinding(b)) return '';
      const name = esc(b.originalFilename || b.fileId || '本地文件');
      if (isImageMime(b.mimeType)) {
        return `<div class="doc-local-preview"><img class="doc-local-thumb" data-file-id="${esc(b.fileId)}" alt="${name}"${asyncThumb ? '' : ' src=""'}/></div>`;
      }
      return `<div class="doc-local-preview"><p class="doc-local-file"><span class="doc-local-name">📄 ${name}</span>`
        + ` <button type="button" class="secondary doc-local-dl" data-file-id="${esc(b.fileId)}">下载</button></p></div>`;
    };
    const roleLabel = r => ({ SOURCE: '源资料', OUTPUT: '产出', BOTH: '双向' }[r] || r || '—');
    const text = v => (v == null ? '' : String(v));
    const agendaOrderTitlePattern = /^会序\s*\d+\s*([:：\-—]\s*)?(.*)$/;
    const parseNames = s => String(s || '').split(',').map(v => v.trim()).filter(Boolean);
    const ownerTagLabel = uid => {
      const name = userNameByFeishuId[uid];
      return name ? (name + ' · ' + uid) : uid;
    };

    const setAutosaveStatus = (state, text) => {
      const el = document.getElementById('autosave-status');
      if (!el) return;
      el.className = 'autosave-status autosave-' + state;
      el.textContent = text;
    };

    const renderPresetOptions = () => {
      const sel = document.getElementById('preset-code');
      if (!sel) return;
      sel.innerHTML = (presetOptions || []).map(p =>
        `<option value="${p.presetTypeCode}">${p.presetTypeCode} · ${esc(p.displayName || ('会务类型' + p.presetTypeCode))}</option>`
      ).join('');
      if (!presetOptions.length) {
        sel.innerHTML = '<option value="1">1 · 会务类型1</option>';
      }
      const hasCurrent = (presetOptions || []).some(p => Number(p.presetTypeCode) === Number(code));
      if (hasCurrent) {
        sel.value = String(code);
      } else if (presetOptions.length) {
        code = Number(presetOptions[0].presetTypeCode);
        sel.value = String(code);
      }
    };

    const loadPresetOptions = async () => {
      presetOptions = await AdminApi.fetch('/api/v1/admin/agenda-config/presets');
      presetOptions = (presetOptions || []).sort((a, b) => Number(a.presetTypeCode) - Number(b.presetTypeCode));
      renderPresetOptions();
    };

    const setMetaSaveStatus = (state, msg) => {
      const el = document.getElementById('meta-save-status');
      if (!el) return;
      el.className = 'autosave-status autosave-' + state;
      el.textContent = msg;
    };

    const syncAgendaFieldsFromDom = () => {
      const wrap = document.getElementById('agenda-table-wrap');
      if (!wrap) return;
      wrap.querySelectorAll('.ag-row').forEach(tr => {
        const i = parseInt(tr.dataset.i, 10);
        if (!bundleItems[i]) return;
        const titleEl = tr.querySelector('.ag-title');
        const minEl = tr.querySelector('.ag-min');
        const ownersEl = tr.querySelector('.ag-owners');
        if (titleEl) bundleItems[i].title = titleEl.value.trim();
        if (minEl) bundleItems[i].minutes = parseInt(minEl.value, 10) || 10;
        if (ownersEl) {
          bundleItems[i].owners = ownersEl.value
            .split(',')
            .map(v => v.trim())
            .filter(Boolean);
        }
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
      normalizeAgendaOrderTitles();
      return bundleItems
        .filter(r => r.title && r.title.trim())
        .map(r => ({
          title: r.title.trim(),
          minutes: r.minutes || 10,
          owners: (r.owners || []).filter(Boolean),
          bindings: normalizeBindingSlots(r.bindings || []).map(b => ({
            id: b.id || null,
            configName: b.configName,
            resourceSlot: b.resourceSlot,
            feishuDocUrl: isLocalBinding(b) ? '' : (b.feishuDocUrl || ''),
            storageKind: isLocalBinding(b) ? 'LOCAL' : 'FEISHU',
            fileId: isLocalBinding(b) ? b.fileId : null,
            originalFilename: isLocalBinding(b) ? b.originalFilename : null,
            mimeType: isLocalBinding(b) ? b.mimeType : null,
            enabled: b.enabled ?? 1,
            configRole: b.configRole || 'SOURCE',
            bitableDisplayMode: b.bitableDisplayMode || null
          }))
        }));
    };

    const normalizeAgendaOrderTitles = () => {
      bundleItems.forEach((row, idx) => {
        const raw = (row && row.title ? String(row.title) : '').trim();
        if (!raw) return;
        const m = raw.match(agendaOrderTitlePattern);
        if (!m) return;
        const tail = (m[2] || '').trim();
        row.title = tail ? ('会序' + (idx + 1) + '：' + tail) : ('会序' + (idx + 1));
      });
    };

    const USER_CACHE_KEY = 'sm-admin-user-options-v1';
    const USER_CACHE_TTL_MS = 5 * 60 * 1000;

    const applyUserOptions = (options, map) => {
      userOptions = (options || []).slice().sort((a, b) => (a.name || a.uid).localeCompare(b.name || b.uid, 'zh-CN'));
      userNameByFeishuId = map || {};
    };

    const readUserCache = () => {
      try {
        const raw = sessionStorage.getItem(USER_CACHE_KEY);
        if (!raw) return null;
        const cached = JSON.parse(raw);
        if (!cached || !cached.ts || Date.now() - cached.ts > USER_CACHE_TTL_MS) return null;
        return cached;
      } catch (_) {
        return null;
      }
    };

    const writeUserCache = () => {
      try {
        sessionStorage.setItem(USER_CACHE_KEY, JSON.stringify({
          ts: Date.now(),
          options: userOptions,
          map: userNameByFeishuId
        }));
      } catch (_) { /* quota */ }
    };

    const loadUserOptions = async (maxPages) => {
      const pageLimit = Math.max(1, Math.min(20, maxPages == null ? 20 : maxPages));
      if (pageLimit >= 20) {
        const cached = readUserCache();
        if (cached && cached.options && cached.map) {
          applyUserOptions(cached.options, cached.map);
          return;
        }
      }
      const map = {};
      const options = [];
      let page = 1;
      const size = 200;
      while (page <= pageLimit) {
        const res = await AdminApi.fetch('/api/v1/admin/users?page=' + page + '&size=' + size);
        const rows = (res && res.records) || [];
        rows.forEach(r => {
          const uid = (r.feishuUserId || '').trim();
          if (!uid || map[uid]) return;
          const name = (r.userName || '').trim();
          map[uid] = name;
          options.push({ uid: uid, name: name });
        });
        const total = Number(res && res.total ? res.total : rows.length);
        const fetched = page * size;
        if (!rows.length || fetched >= total) break;
        page += 1;
      }
      applyUserOptions(options, map);
      if (pageLimit >= 20) writeUserCache();
    };

    const refreshUserPickers = () => {
      if (tab === 'basic') syncMetaForm();
      if (tab === 'table') renderAgendaTable();
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
        storageKind: 'FEISHU',
        fileId: null,
        originalFilename: null,
        mimeType: null,
        enabled: 1,
        configRole: 'SOURCE',
        bitableDisplayMode: null
      });
      return row.bindings.length - 1;
    };

    const ensureBinding = (agendaIndex, bindingIndex) => {
      const row = bundleItems[agendaIndex];
      if (!row) return null;
      if (!row.bindings) row.bindings = [];
      while (row.bindings.length <= bindingIndex) {
        const slot = nextResourceSlot(row.bindings);
        row.bindings.push({
          configName: '',
          resourceSlot: slot,
          feishuDocUrl: '',
          storageKind: 'LOCAL',
          fileId: null,
          originalFilename: null,
          mimeType: null,
          enabled: 1,
          configRole: 'SOURCE',
          bitableDisplayMode: null
        });
      }
      return row.bindings[bindingIndex];
    };

    const unwrapUploadPayload = payload => {
      if (!payload || typeof payload !== 'object') return null;
      if (payload.fileId) return payload;
      if (payload.data && typeof payload.data === 'object' && payload.data.fileId) return payload.data;
      return null;
    };

    const assignLocalUploadToBinding = (binding, data) => {
      if (!binding || !data) return;
      Object.assign(binding, {
        storageKind: 'LOCAL',
        fileId: data.fileId,
        originalFilename: data.originalFilename,
        mimeType: data.mimeType,
        feishuDocUrl: ''
      });
      if (!binding.configName) {
        binding.configName = (data.originalFilename || data.fileId || 'local').replace(/\.[^.]+$/, '');
      }
    };

    const uploadLocalMaterial = async file => {
      const fd = new FormData();
      fd.append('file', file);
      const raw = await AdminApi.fetch('/api/v1/admin/agenda-config/materials/upload', { method: 'POST', body: fd });
      const data = unwrapUploadPayload(raw);
      if (!data || !data.fileId) {
        throw new Error('上传响应无效');
      }
      return data;
    };

    const saveBundleImmediate = async () => {
      saving = true;
      setAutosaveStatus('saving', '保存中…');
      try {
        await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/agenda-bundle', {
          method: 'PUT', body: JSON.stringify(buildPayload())
        });
        bundleItems = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/agenda-bundle');
        setAutosaveStatus('saved', '已自动保存');
      } catch (e) {
        setAutosaveStatus('error', '保存失败: ' + (e.message || ''));
        throw e;
      } finally {
        saving = false;
      }
    };

    const readInlineForm = tile => {
      const source = (tile.querySelector('.inl-source-opt.active') || {}).dataset?.source || 'FEISHU';
      const local = source === 'LOCAL';
      const prev = bundleItems[+tile.dataset.i]?.bindings?.[+tile.dataset.bi] || {};
      return {
        id: tile.dataset.id ? parseInt(tile.dataset.id, 10) : null,
        configName: tile.querySelector('.inl-name').value.trim(),
        resourceSlot: parseInt(tile.querySelector('.inl-slot').value, 10) || 0,
        storageKind: local ? 'LOCAL' : 'FEISHU',
        feishuDocUrl: local ? '' : tile.querySelector('.inl-url').value.trim(),
        fileId: local ? (prev.fileId || null) : null,
        originalFilename: local ? (prev.originalFilename || null) : null,
        mimeType: local ? (prev.mimeType || null) : null,
        enabled: 1,
        configRole: tile.querySelector('.inl-role').value,
        bitableDisplayMode: tile.querySelector('.inl-bdm').value || null
      };
    };

    const commitInlineEdit = async (agendaIndex, bindingIndex, cancel) => {
      const tile = document.querySelector(`.doc-tile-editing[data-i="${agendaIndex}"][data-bi="${bindingIndex}"]`);
      editingDocKey = null;
      if (!tile) {
        renderAgendaTable();
        return;
      }
      if (cancel) {
        const b = bundleItems[agendaIndex].bindings[bindingIndex];
        if (!b.id && !b.configName && !bindingHasContent(b)) {
          bundleItems[agendaIndex].bindings.splice(bindingIndex, 1);
        }
        renderAgendaTable();
        return;
      }
      const binding = readInlineForm(tile);
      if (!binding.configName && !bindingHasContent(binding)) {
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
      const local = isLocalBinding(d);
      const sourceFeishuCls = local ? '' : ' active';
      const sourceLocalCls = local ? ' active' : '';
      const feishuHidden = local ? ' hidden' : '';
      const localHidden = local ? '' : ' hidden';
      const localPreview = renderLocalPreviewHtml(d, true);
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
            <label>资料来源</label>
            <div class="doc-source-toggle">
              <button type="button" class="secondary inl-source-opt${sourceFeishuCls}" data-source="FEISHU">飞书链接</button>
              <button type="button" class="secondary inl-source-opt${sourceLocalCls}" data-source="LOCAL">本地上传</button>
            </div>
          </div>
          <div class="doc-source-feishu${feishuHidden}">
            <div class="doc-inline-row">
              <label>飞书链接</label>
              <textarea class="inl-url code-area" rows="3" placeholder="粘贴飞书文档 / 多维表链接">${esc(d.feishuDocUrl)}</textarea>
              <p class="doc-inline-hint-block">${AdminHints.presets.feishuUrl}</p>
            </div>
          </div>
          <div class="doc-source-local${localHidden}">
            <div class="doc-inline-row">
              <label>本地文件</label>
              <input class="inl-file" type="file" accept=".doc,.docx,image/*" multiple/>
              <p class="doc-inline-hint-block">${AdminHints.presets.localUpload || '支持 doc/docx 与常见图片，可多选；上传后立即预览'}</p>
            </div>
            ${localPreview}
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
      const local = isLocalBinding(d);
      let contentInner;
      let linkBtns = '';
      if (local) {
        contentInner = `<div class="doc-tile-local doc-editable">${renderLocalPreviewHtml(d, true)}</div>`;
        linkBtns = `<button type="button" class="secondary doc-local-dl" data-file-id="${esc(d.fileId)}">下载</button>`;
      } else {
        contentInner = url
          ? `<a class="doc-url-link" href="${esc(url)}" target="_blank" rel="noopener noreferrer" onclick="event.stopPropagation()">${esc(url)}</a>`
          : '<span class="doc-url-placeholder">点击配置资料（飞书链接或本地上传）</span>';
        contentInner = `<div class="doc-tile-url doc-editable">${contentInner}</div>`;
        if (url) {
          linkBtns = `<button type="button" class="secondary btn-link-open" data-url="${esc(url)}">打开</button>
             <button type="button" class="secondary btn-link-copy" data-url="${esc(url)}">复制</button>`;
        }
      }
      const kindPill = local ? '<span class="meta-pill meta-local">本地</span>' : '';
      return `<div class="doc-tile doc-tile-view doc-tile-clickable" data-i="${agendaIndex}" data-bi="${bindingIndex}" tabindex="0" title="点击编辑">
        <div class="doc-tile-main">
          <div class="doc-tile-head">
            <span class="doc-tile-name">${name}</span>
            <span class="meta-pill meta-role meta-role-${role}">${esc(roleLabel(d.configRole))}</span>
            <span class="meta-pill">槽位 ${d.resourceSlot ?? 0}</span>
            ${kindPill}
            ${bdm}
          </div>
          ${contentInner}
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

    const reorderAgenda = (from, to) => {
      if (from === to || from < 0 || to < 0 || from >= bundleItems.length || to >= bundleItems.length) return;
      const moved = bundleItems[from];
      bundleItems.splice(from, 1);
      bundleItems.splice(to, 0, moved);
      normalizeAgendaOrderTitles();
    };

    const bindAgendaTableEvents = el => {
      el.querySelector('#ag-add-row').onclick = () => {
        bundleItems.push({ title: '新议题', minutes: 10, owners: [], hasRollCallKeyword: false, bindings: [] });
        renderAgendaTable();
        autoSaveBundle();
      };
      el.querySelectorAll('.ag-title, .ag-min, .ag-owners').forEach(inp => {
        inp.addEventListener('blur', () => autoSaveBundle());
      });
      el.querySelectorAll('.ag-owner-add').forEach(btn => btn.onclick = () => {
        const i = +btn.dataset.i;
        const sel = el.querySelector('.ag-owner-sel[data-i="' + i + '"]');
        if (!bundleItems[i] || !sel) return;
        const uid = (sel.value || '').trim();
        if (!uid) return;
        if (!bundleItems[i].owners) bundleItems[i].owners = [];
        if (!bundleItems[i].owners.includes(uid)) {
          bundleItems[i].owners.push(uid);
          renderAgendaTable();
          autoSaveBundle();
        }
      });
      el.querySelectorAll('.ag-owner-del').forEach(btn => btn.onclick = () => {
        const i = +btn.dataset.i;
        const uid = (btn.dataset.uid || '').trim();
        if (!bundleItems[i] || !uid) return;
        bundleItems[i].owners = (bundleItems[i].owners || []).filter(v => v !== uid);
        renderAgendaTable();
        autoSaveBundle();
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
      el.querySelectorAll('.agenda-card').forEach(card => {
        card.ondragstart = e => {
          const i = Number(card.dataset.i);
          if (Number.isNaN(i)) return;
          e.dataTransfer.effectAllowed = 'move';
          e.dataTransfer.setData('text/plain', String(i));
          card.classList.add('dragging');
        };
        card.ondragend = () => {
          card.classList.remove('dragging');
          el.querySelectorAll('.agenda-card.drag-over').forEach(x => x.classList.remove('drag-over'));
        };
        card.ondragover = e => {
          e.preventDefault();
          card.classList.add('drag-over');
          e.dataTransfer.dropEffect = 'move';
        };
        card.ondragleave = () => {
          card.classList.remove('drag-over');
        };
        card.ondrop = e => {
          e.preventDefault();
          const from = Number(e.dataTransfer.getData('text/plain'));
          const to = Number(card.dataset.i);
          card.classList.remove('drag-over');
          if (Number.isNaN(from) || Number.isNaN(to)) return;
          reorderAgenda(from, to);
          renderAgendaTable();
          autoSaveBundle();
        };
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
          if (e.relatedTarget && e.relatedTarget.classList && e.relatedTarget.classList.contains('inl-file')) return;
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
      el.querySelectorAll('.inl-source-opt').forEach(btn => {
        btn.onclick = e => {
          e.preventDefault();
          e.stopPropagation();
          const tile = btn.closest('.doc-tile-editing');
          if (!tile) return;
          const mode = btn.dataset.source;
          tile.querySelectorAll('.inl-source-opt').forEach(b => b.classList.toggle('active', b.dataset.source === mode));
          tile.querySelector('.doc-source-feishu').classList.toggle('hidden', mode !== 'FEISHU');
          tile.querySelector('.doc-source-local').classList.toggle('hidden', mode !== 'LOCAL');
        };
      });
      el.querySelectorAll('.inl-file').forEach(input => {
        input.onmousedown = e => {
          e.stopPropagation();
          skipInlineCommit = true;
          const onWindowFocus = () => {
            setTimeout(() => {
              if (!input.files || !input.files.length) skipInlineCommit = false;
            }, 0);
            window.removeEventListener('focus', onWindowFocus);
          };
          window.addEventListener('focus', onWindowFocus);
        };
        input.onchange = async () => {
          const files = input.files ? Array.from(input.files) : [];
          if (!files.length) {
            skipInlineCommit = false;
            return;
          }
          const tile = input.closest('.doc-tile-editing');
          if (!tile) {
            skipInlineCommit = false;
            return;
          }
          const i = +tile.dataset.i;
          let bi = +tile.dataset.bi;
          const hadEditKey = editingDocKey;
          input.disabled = true;
          try {
            for (let fi = 0; fi < files.length; fi++) {
              if (fi > 0) bi = addEmptyBinding(i);
              const b = ensureBinding(i, bi);
              if (!b) throw new Error('资料绑定不存在');
              const data = await uploadLocalMaterial(files[fi]);
              assignLocalUploadToBinding(b, data);
            }
            await saveBundleImmediate();
            editingDocKey = files.length > 1 ? null : hadEditKey;
            renderAgendaTable();
            if (files.length === 1 && hadEditKey) startDocEdit(i, bi);
          } catch (err) {
            alert('上传失败: ' + (err.message || ''));
            renderAgendaTable();
            if (hadEditKey) startDocEdit(i, bi);
          } finally {
            input.disabled = false;
            input.value = '';
            skipInlineCommit = false;
          }
        };
      });
      el.querySelectorAll('.doc-local-dl').forEach(btn => {
        btn.onclick = async e => {
          e.stopPropagation();
          e.preventDefault();
          try {
            const blobUrl = await fetchMaterialBlobUrl(btn.dataset.fileId);
            const a = document.createElement('a');
            a.href = blobUrl;
            a.download = btn.closest('.doc-local-file')?.querySelector('.doc-local-name')?.textContent?.replace(/^📄\s*/, '') || 'file';
            a.click();
          } catch (err) {
            alert('下载失败: ' + (err.message || ''));
          }
        };
      });
      const thumbs = Array.from(el.querySelectorAll('.doc-local-thumb[data-file-id]')).filter(img => !img.src);
      let thumbIdx = 0;
      let thumbActive = 0;
      const THUMB_CONCURRENCY = 3;
      const pumpThumbs = () => {
        while (thumbActive < THUMB_CONCURRENCY && thumbIdx < thumbs.length) {
          const img = thumbs[thumbIdx++];
          thumbActive += 1;
          fetchMaterialBlobUrl(img.dataset.fileId).then(url => { img.src = url; })
            .catch(() => { img.alt = '预览加载失败'; })
            .finally(() => { thumbActive -= 1; pumpThumbs(); });
        }
      };
      pumpThumbs();
    };

    const loadBundle = async () => {
      code = parseInt(document.getElementById('preset-code').value, 10);
      if (!code || code <= 0) {
        throw new Error('请选择有效会务类型');
      }
      const preset = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code);
      presetMeta = {
        displayName: text(preset.displayName),
        company: text(preset.company),
        department: text(preset.department),
        groupName: text(preset.groupName),
        scheduleNote: text(preset.scheduleNote),
        agendaSummary: text(preset.agendaSummary),
        organizerName: text(preset.organizerName),
        leaderName: text(preset.leaderName),
        participantsNames: text(preset.participantsNames)
      };
      hostAgendaJson = preset.hostAgendaJson || '';
      bundleItems = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/agenda-bundle');
      editingDocKey = null;
      const jsonEl = document.getElementById('host-agenda-json');
      if (jsonEl) jsonEl.value = hostAgendaJson;
      syncMetaForm();
    };

    const syncMetaForm = () => {
      const set = (id, v) => {
        const el = document.getElementById(id);
        if (el) el.value = text(v);
      };
      set('meta-display-name', presetMeta.displayName);
      set('meta-company', presetMeta.company);
      set('meta-department', presetMeta.department);
      set('meta-group-name', presetMeta.groupName);
      set('meta-schedule-note', presetMeta.scheduleNote);
      set('meta-agenda-summary', presetMeta.agendaSummary);
      setupNameSelect('meta-organizer-name', presetMeta.organizerName, '选择组织者...');
      setupNameSelect('meta-leader-name', presetMeta.leaderName, '选择负责人...');
      setupNameSelect('meta-participant-select', '', '选择参会人...');
      metaParticipants = parseNames(presetMeta.participantsNames);
      renderMetaParticipantChips();
    };

    const collectMetaForm = () => {
      const get = id => {
        const el = document.getElementById(id);
        return el ? el.value.trim() : '';
      };
      return {
        displayName: get('meta-display-name'),
        company: get('meta-company'),
        department: get('meta-department'),
        groupName: get('meta-group-name'),
        scheduleNote: get('meta-schedule-note'),
        agendaSummary: get('meta-agenda-summary'),
        organizerName: get('meta-organizer-name'),
        leaderName: get('meta-leader-name'),
        participantsNames: metaParticipants.join(',')
      };
    };

    const setupNameSelect = (id, currentName, placeholder) => {
      const el = document.getElementById(id);
      if (!el) return;
      const opts = [`<option value="">${esc(placeholder || '请选择...')}</option>`];
      (userOptions || []).forEach(u => {
        const name = (u.name || '').trim();
        if (!name) return;
        opts.push(`<option value="${esc(name)}">${esc(name + ' · ' + u.uid)}</option>`);
      });
      const current = String(currentName || '').trim();
      if (current && !opts.some(o => o.includes(`value="${esc(current)}"`))) {
        opts.push(`<option value="${esc(current)}">${esc(current)}（历史值）</option>`);
      }
      el.innerHTML = opts.join('');
      el.value = current || '';
    };

    const renderMetaParticipantChips = () => {
      const box = document.getElementById('meta-participant-chips');
      if (!box) return;
      if (!metaParticipants.length) {
        box.innerHTML = '<span class="muted">未选择参会人</span>';
        return;
      }
      box.innerHTML = metaParticipants.map((name, idx) =>
        `<span class="owner-chip">${esc(name)}<button type="button" class="meta-participant-del" data-idx="${idx}">×</button></span>`
      ).join('');
    };

    const saveMeta = async (silent) => {
      presetMeta = collectMetaForm();
      if (metaSaving) return;
      metaSaving = true;
      setMetaSaveStatus('saving', '保存中…');
      try {
        await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code, {
          method: 'PUT',
          body: JSON.stringify(Object.assign({ presetTypeCode: code, hostAgendaJson }, presetMeta))
        });
        setMetaSaveStatus('saved', silent ? '已自动保存' : '已保存');
      } catch (e) {
        setMetaSaveStatus('error', '保存失败: ' + (e.message || ''));
      } finally {
        metaSaving = false;
      }
    };

    const autoSaveMeta = () => {
      clearTimeout(metaSaveTimer);
      metaSaveTimer = setTimeout(() => saveMeta(true), 550);
    };

    const bindMetaEvents = () => {
      const panel = document.getElementById('preset-meta-panel');
      if (!panel) return;
      panel.querySelectorAll('input, textarea, select').forEach(el => {
        el.addEventListener('input', autoSaveMeta);
        el.addEventListener('blur', autoSaveMeta);
        el.addEventListener('change', autoSaveMeta);
      });
      const addBtn = document.getElementById('meta-participant-add');
      const sel = document.getElementById('meta-participant-select');
      if (addBtn && sel) {
        addBtn.onclick = () => {
          const name = (sel.value || '').trim();
          if (!name) return;
          if (!metaParticipants.includes(name)) {
            metaParticipants.push(name);
            renderMetaParticipantChips();
            autoSaveMeta();
          }
        };
      }
      panel.addEventListener('click', e => {
        const btn = e.target && e.target.closest('.meta-participant-del');
        if (!btn) return;
        const idx = Number(btn.dataset.idx);
        if (Number.isNaN(idx) || idx < 0 || idx >= metaParticipants.length) return;
        metaParticipants.splice(idx, 1);
        renderMetaParticipantChips();
        autoSaveMeta();
      });
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
        const ownersText = (row.owners || []).join(', ');
        const ownerTags = (row.owners || []).map(uid =>
          `<span class="owner-chip">${esc(ownerTagLabel(uid))}<button type="button" class="ag-owner-del" data-i="${i}" data-uid="${esc(uid)}">×</button></span>`
        ).join('');
        const ownerOptions = ['<option value="">选择负责人...</option>']
          .concat(userOptions.map(o => `<option value="${esc(o.uid)}">${esc(o.name ? (o.name + ' · ' + o.uid) : o.uid)}</option>`))
          .join('');
        html += `<article class="agenda-card ag-row" data-i="${i}" draggable="true">
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
                <label class="agenda-owner-label" title="${AdminHints.presets.agendaOwners.replace(/"/g, '&quot;')}">
                  <span>负责人</span>
                  <input class="ag-owners" type="text" placeholder="feishu_user_id_a,feishu_user_id_b" value="${esc(ownersText)}"/>
                </label>
                <div class="agenda-owner-picker">
                  <select class="ag-owner-sel" data-i="${i}">${ownerOptions}</select>
                  <button type="button" class="secondary ag-owner-add" data-i="${i}">添加</button>
                </div>
                <div class="btn-group btn-group-icon">
                  <button type="button" class="secondary ag-up" data-i="${i}" ${i === 0 ? 'disabled' : ''}>↑</button>
                  <button type="button" class="secondary ag-down" data-i="${i}" ${i === bundleItems.length - 1 ? 'disabled' : ''}>↓</button>
                  <button type="button" class="danger ag-del" data-i="${i}">删除会序</button>
                </div>
              </div>
              ${tagHtml}
              <div class="agenda-owner-chips">${ownerTags || '<span class="muted">未设置负责人</span>'}</div>
            </header>
            <section class="agenda-card-docs">
              <div class="agenda-doc-toolbar">
                <span class="agenda-doc-label">资料绑定 <em>${bindings.length}</em></span>
                <button type="button" class="ghost ag-doc-add" data-i="${i}">+ 添加资料</button>
              </div>`;
        if (bindings.length === 0) {
          html += `<div class="doc-tile-empty doc-editable" data-i="${i}" tabindex="0">点击配置资料（飞书链接或本地上传）</div>`;
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
      document.getElementById('preset-meta-panel').classList.toggle('hidden', tab !== 'basic');
      document.getElementById('agenda-table-panel').classList.toggle('hidden', tab !== 'table');
      document.getElementById('agenda-json-panel').classList.toggle('hidden', tab !== 'json');
      document.querySelectorAll('.tabs button').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
      if (tab === 'basic') syncMetaForm();
      if (tab === 'table') renderAgendaTable();
    };

    root.innerHTML = `
      <div class="panel">
        <div class="panel-head"><h2>会务预设</h2><p>点击资料区域直接编辑，失焦后自动保存；会序标题/时长修改后亦会自动保存。资料「角色」决定 weekly-jobs 能否引用为源/产出。</p></div>
        <div class="toolbar toolbar-split">
          <div class="toolbar-row">
            <label class="field-inline" title="${AdminHints.presets.presetCode.replace(/"/g, '&quot;')}">会务类型<select id="preset-code"><option value="1">1</option><option value="2">2</option><option value="3">3</option><option value="4">4</option><option value="5">5</option></select></label>
            <button type="button" class="primary" id="load-preset">加载</button>
            <button type="button" class="secondary" id="create-preset">新增会务类型</button>
            <button type="button" class="secondary" id="validate-preset">校验会序</button>
            <button type="button" class="secondary" id="preview-preset">合并预览</button>
          </div>
          <hr class="toolbar-divider"/>
          <div class="toolbar-row actions-secondary">
            <button type="button" class="secondary" id="refresh-cache">刷新 preset 缓存</button>
            <button type="button" class="secondary" id="refresh-meetings-dry">预览刷新未开会</button>
            <button type="button" class="secondary" id="refresh-meetings">执行刷新未开会</button>
            <button type="button" class="secondary" id="trigger-owner-notify">手动触发会序确认通知</button>
          </div>
        </div>
      </div>
      <div class="panel">
        <div class="tabs">
          <button type="button" data-tab="basic">基础信息</button>
          <button type="button" data-tab="table" class="active">会序与资料</button>
          <button type="button" data-tab="json">JSON 高级</button>
        </div>
        <div id="preset-meta-panel" class="hidden">
          <div class="preset-meta-head">
            <p class="muted">维护会务类型基础属性（与数据库字段一一对应）。输入后自动保存，无需逐项点确认。</p>
            <span id="meta-save-status" class="autosave-status autosave-idle">修改后自动保存</span>
          </div>
          <div class="preset-meta-grid">
            ${AdminForm.field('显示名称', '<input id="meta-display-name" type="text" placeholder="如 会议议程模板"/>', '对应 int_meeting_type_preset.display_name')}
            ${AdminForm.field('集团/公司', '<input id="meta-company" type="text" placeholder="如 吉青汽车科技集团"/>', '对应 int_meeting_type_preset.company')}
            ${AdminForm.field('部门', '<input id="meta-department" type="text" placeholder="如 经营管理中心"/>', '对应 int_meeting_type_preset.department')}
            ${AdminForm.field('小组名称', '<input id="meta-group-name" type="text" placeholder="如 会议计划表"/>', '对应 int_meeting_type_preset.group_name')}
            ${AdminForm.field('组织者', '<select id="meta-organizer-name"></select>', '对应 int_meeting_type_preset.organizer_name')}
            ${AdminForm.field('负责人', '<select id="meta-leader-name"></select>', '对应 int_meeting_type_preset.leader_name')}
          </div>
          <div class="preset-meta-stack">
            ${AdminForm.field('参会人（多选）', '<div class="meta-participant-picker"><select id="meta-participant-select"></select><button type="button" class="secondary" id="meta-participant-add">添加</button></div><div id="meta-participant-chips" class="agenda-owner-chips"></div>', '对应 int_meeting_type_preset.participants_names')}
            ${AdminForm.field('排期备注', '<textarea id="meta-schedule-note" rows="2" class="code-area" placeholder="如 每周一 9:30"></textarea>', '对应 int_meeting_type_preset.schedule_note')}
            ${AdminForm.field('议题摘要', '<textarea id="meta-agenda-summary" rows="3" class="code-area" placeholder="如 集团综合职能事务汇报"></textarea>', '对应 int_meeting_type_preset.agenda_summary')}
          </div>
          <p class="preset-meta-actions"><button type="button" class="secondary" id="save-meta">立即保存</button></p>
        </div>
        <div id="agenda-table-panel"><div id="agenda-table-wrap"></div></div>
        <div id="agenda-json-panel" class="hidden">
          <p class="form-hint">${AdminHints.presets.hostAgendaJson}</p>
          <textarea id="host-agenda-json" class="code-area" rows="14"></textarea>
          <p style="margin-top:0.75rem"><button type="button" class="primary" id="save-json">保存 JSON</button></p>
        </div>
      </div>
      <pre id="preview-out" class="panel hidden"></pre>
      <pre id="validate-out" class="panel hidden"></pre>
      <div class="panel hidden form-editor" id="preset-create-editor">
        <h3>新增会务类型</h3>
        <p class="form-hint">一次填写后提交。编号可留空自动分配。</p>
        ${AdminForm.field('会务类型编号', '<input id="preset-create-code" type="number" min="1" placeholder="留空自动分配"/>', '正整数，留空自动使用下一个编号')}
        ${AdminForm.field('会务类型名称', '<input id="preset-create-name" type="text" placeholder="如 经营例会"/>', '建议填写便于识别的业务名称')}
        <p id="preset-create-msg" class="msg hidden"></p>
        <div class="toolbar">
          <button type="button" class="primary" id="preset-create-submit">提交创建</button>
          <button type="button" class="secondary" id="preset-create-cancel">取消</button>
        </div>
      </div>
      <div class="panel hidden form-editor" id="owner-notify-editor">
        <h3>手动触发会序确认通知</h3>
        <p class="form-hint">按当前会务类型（preset code）批量触发 PRE 通知流程。默认模板为 pre_10m_default。</p>
        ${AdminForm.field('模板编码', '<input id="owner-notify-template-code" type="text" placeholder="pre_10m_default（留空使用默认）"/>')}
        ${AdminForm.field('跳过已有执行', '<label class="check-label"><input id="owner-notify-skip-existing" type="checkbox" checked/> 仅触发尚未执行 PRE 的会议</label>')}
        <p id="owner-notify-msg" class="msg hidden"></p>
        <div class="toolbar">
          <button type="button" class="primary" id="owner-notify-submit">提交触发</button>
          <button type="button" class="secondary" id="owner-notify-cancel">取消</button>
        </div>
      </div>`;

    root.querySelectorAll('.tabs button').forEach(b => {
      b.onclick = () => { tab = b.dataset.tab; render(); };
    });
    document.getElementById('save-meta').onclick = async () => { await saveMeta(false); };
    document.getElementById('preset-code').onchange = async () => {
      clearTimeout(metaSaveTimer);
      await loadBundle();
      render();
      setMetaSaveStatus('idle', '修改后自动保存');
    };
    document.getElementById('load-preset').onclick = async () => { await loadBundle(); render(); setAutosaveStatus('idle', '已加载'); };
    document.getElementById('create-preset').onclick = () => {
      const ed = document.getElementById('preset-create-editor');
      document.getElementById('preset-create-code').value = '';
      document.getElementById('preset-create-name').value = '';
      const msg = document.getElementById('preset-create-msg');
      msg.className = 'msg hidden';
      msg.textContent = '';
      AdminUi.openEditor(ed);
    };
    document.getElementById('preset-create-cancel').onclick = () => {
      AdminUi.closeEditor(document.getElementById('preset-create-editor'));
    };
    document.getElementById('trigger-owner-notify').onclick = () => {
      const ed = document.getElementById('owner-notify-editor');
      document.getElementById('owner-notify-template-code').value = 'pre_10m_default';
      document.getElementById('owner-notify-skip-existing').checked = true;
      const msg = document.getElementById('owner-notify-msg');
      msg.className = 'msg hidden';
      msg.textContent = '';
      AdminUi.openEditor(ed);
    };
    document.getElementById('owner-notify-cancel').onclick = () => {
      AdminUi.closeEditor(document.getElementById('owner-notify-editor'));
    };
    document.getElementById('owner-notify-submit').onclick = async () => {
      const msg = document.getElementById('owner-notify-msg');
      const templateCode = document.getElementById('owner-notify-template-code').value.trim();
      const skipExisting = !!document.getElementById('owner-notify-skip-existing').checked;
      try {
        const res = await AdminApi.fetch('/api/v1/admin/agenda-config/presets/' + code + '/trigger-owner-confirm-notify', {
          method: 'POST',
          body: JSON.stringify({
            templateCode: templateCode,
            skipExisting: skipExisting
          })
        });
        AdminUi.closeEditor(document.getElementById('owner-notify-editor'));
        if (res && res.mode === 'preset_direct') {
          const success = Number(res.successSteps || 0);
          const failed = Number(res.failedSteps || 0);
          setAutosaveStatus('saved', '已按 code 触发：成功步骤 ' + success + '，失败步骤 ' + failed);
        } else {
          const matched = Number(res && res.matchedMeetings ? res.matchedMeetings : 0);
          const triggered = Number(res && res.triggeredMeetings ? res.triggeredMeetings : 0);
          setAutosaveStatus('saved', '已触发：匹配 ' + matched + ' 场，实际触发 ' + triggered + ' 场');
        }
      } catch (e) {
        msg.className = 'msg msg-err';
        msg.classList.remove('hidden');
        msg.textContent = e.message || '触发失败';
      }
    };
    document.getElementById('preset-create-submit').onclick = async () => {
      const msg = document.getElementById('preset-create-msg');
      const codeRaw = document.getElementById('preset-create-code').value.trim();
      const nameRaw = document.getElementById('preset-create-name').value.trim();
      const codeNum = codeRaw ? Number(codeRaw) : null;
      if (codeNum != null && (!Number.isInteger(codeNum) || codeNum <= 0)) {
        msg.className = 'msg msg-err';
        msg.classList.remove('hidden');
        msg.textContent = '编号必须为正整数';
        return;
      }
      try {
        const created = await AdminApi.fetch('/api/v1/admin/agenda-config/presets', {
          method: 'POST',
          body: JSON.stringify({
            presetTypeCode: codeNum,
            displayName: nameRaw
          })
        });
        code = Number(created.code);
        await loadPresetOptions();
        await loadBundle();
        render();
        AdminUi.closeEditor(document.getElementById('preset-create-editor'));
        setAutosaveStatus('saved', '已创建会务类型 #' + code);
      } catch (e) {
        msg.className = 'msg msg-err';
        msg.classList.remove('hidden');
        msg.textContent = e.message || '创建失败';
      }
    };
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

    await Promise.all([loadPresetOptions(), loadUserOptions(1)]);
    await loadBundle();
    bindMetaEvents();
    render();
    loadUserOptions(20).then(refreshUserPickers).catch(() => {});
  }
});
