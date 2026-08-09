/**
 * 会序 oabp 展示模板 — 简洁向导 + 专家模式 UI。
 * 简洁模式：① 自定义 SQL → ② 展示方式（含合计）→ ③ 列配置（含列内筛选与多列排序）。
 * 依赖：AdminApi、全局 esc 函数（由 presets.js 注入 ctx.esc）。
 */
(function (global) {
  'use strict';

  const PROBE_CACHE = new Map();
  const PROBE_TTL_MS = 30000;
  const previewTimers = new Map();

  let displayPresets = [];
  let displayOptions = null;
  let presetsLoaded = false;

  const DEFAULT_LABELS = {
    task_name: '待办事项',
    status_code: '状态',
    progress: '进度',
    plan_date: '截止日期',
    start_date: '开始日期',
    id: '内部编号'
  };

  const OABP_TRANSIENT_KEYS = ['_oabpProbe', '_oabpMode', '_oabpColumnsBackup', '_oabpLastPreview'];

  function deepClone(obj) {
    return obj ? JSON.parse(JSON.stringify(obj)) : null;
  }

  function normalizeSql(sql) {
    return String(sql || '').replace(/\s+/g, ' ').trim().toLowerCase();
  }

  function shouldUseExpertMode(row) {
    return row._oabpMode === 'expert';
  }

  async function ensurePresets() {
    if (presetsLoaded) return;
    const [display, options] = await Promise.all([
      AdminApi.fetch('/api/v1/admin/agenda-config/oabp-display/presets'),
      AdminApi.fetch('/api/v1/admin/agenda-config/oabp-display/options')
    ]);
    displayPresets = display || [];
    displayOptions = options || {};
    presetsLoaded = true;
  }

  function labelForSource(source, probe) {
    const labels = (probe && probe.columnLabels) || {};
    if (labels[source]) return labels[source];
    if (DEFAULT_LABELS[source]) return DEFAULT_LABELS[source];
    if (/[\u4e00-\u9fff]/.test(source)) return source;
    return source;
  }

  function inferRenderAs(source, label) {
    const s = String(source || '');
    const l = String(label || '');
    if (s === 'progress' || l.includes('进度')) return 'progress_bar';
    if (s === 'status_code' || s === 'status' || l.includes('状态')) return 'badge';
    if (s === 'task_name' || l.includes('事项') || l.includes('任务')
        || l.includes('概况') || l.includes('说明') || l.includes('备注') || l.includes('计划')) {
      return 'long_text';
    }
    return 'plain';
  }

  function inferFormat(source, label) {
    const s = String(source || '');
    const l = String(label || '');
    if (s === 'progress' || l.includes('进度')) return 'percent';
    if (s === 'status_code' || s === 'status' || l.includes('状态')) return 'enum';
    if (s.includes('date') || l.includes('日期')) return 'date';
    return 'plain';
  }

  function defaultStatusMap() {
    return { '0': '进行中', '1': '已完成', '2': '已逾期', '99': '归档' };
  }

  /** 探测采样值是否像数字状态码（0/1/2）；若 SQL 已 CASE 成中文则返回 false */
  function statusSamplesLookNumeric(probe, header) {
    const samples = (probe && probe.samples && probe.samples[header]) || [];
    if (!samples.length) return true;
    return samples.every(v => {
      if (v == null) return true;
      const s = String(v).trim();
      return s === '' || /^-?\d+$/.test(s);
    });
  }

  function isStatusColumn(header, label, probe) {
    const lbl = label || labelForSource(header, probe);
    return header === 'status_code' || header === 'status' || String(lbl).includes('状态');
  }

  /** SQL 输出数字码时用 enum+map；已在 SQL 中 CASE 成中文标签时用 plain，避免全部被 defaultValue 覆盖 */
  function applyStatusColumnFormat(col, probe, header) {
    if (!col || !isStatusColumn(header, col.label, probe)) return;
    if (statusSamplesLookNumeric(probe, header)) {
      col.format = 'enum';
      col.renderAs = col.renderAs || 'badge';
      if (!col.map || !Object.keys(col.map).length) col.map = defaultStatusMap();
      if (col.defaultValue == null || col.defaultValue === '') col.defaultValue = '未开始';
    } else {
      col.format = 'plain';
      col.renderAs = col.renderAs || 'badge';
      delete col.map;
      delete col.defaultValue;
    }
  }

  /** 将旧 source（含英文 task_name 等）解析为 probe.headers 中的实际列名 */
  function resolveHeaderForSource(src, col, headers, columnLabels) {
    if (!src || !headers || !headers.length) return null;
    if (headers.includes(src)) return src;
    const lbl = (col && col.label) || DEFAULT_LABELS[src] || src;
    for (let i = 0; i < headers.length; i++) {
      const h = headers[i];
      const hLbl = columnLabels[h] || h;
      if (h === lbl || hLbl === lbl) return h;
    }
    const zh = DEFAULT_LABELS[src];
    if (zh && headers.includes(zh)) return zh;
    return null;
  }

  function buildColumnFromHeader(h, probe, existingBySource) {
    if (existingBySource && existingBySource[h]) {
      const col = deepClone(existingBySource[h]);
      applyStatusColumnFormat(col, probe, h);
      return col;
    }
    const col = {
      source: h,
      label: labelForSource(h, probe),
      visible: h !== 'id',
      width: null,
      format: inferFormat(h, labelForSource(h, probe)),
      renderAs: inferRenderAs(h, labelForSource(h, probe))
    };
    applyStatusColumnFormat(col, probe, h);
    return col;
  }

  /**
   * 以 probe.headers 为权威列清单，对齐已保存的 columns。
   * - 移除 source 不在 headers 中的列（消除校验错误）
   * - 标签回退匹配：旧英文 source 若 label 对得上某 header，则 remap 到该 header
   * - 保留用户已拖拽顺序（仅当 source 仍合法）
   */
  function reconcileColumns(probe, existingColumns, existingTemplate) {
    const headers = (probe && probe.headers) || [];
    if (!headers.length) return existingColumns || [];
    const columnLabels = (probe && probe.columnLabels) || {};
    const existingList = existingColumns || (existingTemplate && existingTemplate.columns) || [];
    const existing = {};
    existingList.forEach(c => {
      if (c && c.source) existing[c.source] = c;
    });
    const remapped = {};
    existingList.forEach(col => {
      if (!col || !col.source) return;
      const src = col.source;
      if (headers.includes(src)) {
        remapped[src] = deepClone(col);
        return;
      }
      const target = resolveHeaderForSource(src, col, headers, columnLabels);
      if (target) {
        const merged = deepClone(col);
        merged.source = target;
        merged.label = columnLabels[target] || merged.label || DEFAULT_LABELS[src] || target;
        if (!remapped[target]) remapped[target] = merged;
      }
    });
    const orderedSources = [];
    const used = new Set();
    existingList.forEach(col => {
      if (!col || !col.source) return;
      const mapped = resolveHeaderForSource(col.source, col, headers, columnLabels);
      if (mapped && !used.has(mapped)) {
        orderedSources.push(mapped);
        used.add(mapped);
      }
    });
    headers.forEach(h => {
      if (!used.has(h)) {
        orderedSources.push(h);
        used.add(h);
      }
    });
    return orderedSources.map(h => buildColumnFromHeader(h, probe, remapped));
  }

  /** 将 row 的列/筛选/排序与 probe 对齐，并回写 oabpDisplayTemplate */
  function reconcileRowState(row) {
    const probe = row._oabpProbe;
    if (!probe || !probe.headers || !probe.headers.length) return false;
    row._oabpColumns = reconcileColumns(probe, row._oabpColumns, row.oabpDisplayTemplate);
    reconcileFilterSort(row, probe);
    row.oabpSqlPresetId = 'custom';
    row.oabpDisplayTemplate = buildTemplateFromRow(row);
    return true;
  }

  /** filter / sort 字段同步到 probe headers（label 回退匹配，无法匹配则丢弃） */
  function reconcileFilterSort(row, probe) {
    const headers = (probe && probe.headers) || [];
    if (!headers.length) return;
    const columnLabels = (probe && probe.columnLabels) || {};
    const resolveField = field => resolveHeaderForSource(field, { label: DEFAULT_LABELS[field] }, headers, columnLabels);
    row._oabpFilterRules = (row._oabpFilterRules || []).map(r => {
      const target = resolveField(r.field);
      return target ? Object.assign({}, r, { field: target }) : null;
    }).filter(Boolean);
    row._oabpSort = (row._oabpSort || []).map((s, idx) => {
      const target = resolveField(s.by);
      return target ? Object.assign({}, s, { by: target, _priority: s._priority || (idx + 1) }) : null;
    }).filter(Boolean);
    if (row._oabpTotalsSumFields) {
      row._oabpTotalsSumFields = row._oabpTotalsSumFields.map(f => resolveField(f)).filter(Boolean);
    }
  }

  function buildTemplateFromRow(row) {
    const t = deepClone(row.oabpDisplayTemplate) || {};
    t.version = 1;
    t.displayMode = row._oabpDisplayMode || t.displayMode || 'table';
    t.sheetName = row._oabpSheetName || t.sheetName || '项目任务';
    if (row._oabpColumns) {
      t.columns = deepClone(row._oabpColumns);
    }
    t.sort = (row._oabpSort && row._oabpSort.length)
      ? row._oabpSort.map(s => ({ by: s.by, dir: s.dir }))
      : [];
    if (t.displayMode === 'grouped_table') {
      t.groupBy = t.groupBy || {
        source: 'status_code',
        label: '状态',
        map: defaultStatusMap(),
        order: ['2', '0', '1'],
        showCount: true
      };
      if (row._oabpGroupOrder && row._oabpGroupOrder.length) {
        t.groupBy.order = row._oabpGroupOrder.slice();
      }
      const probe = row._oabpProbe;
      if (probe && probe.headers && probe.headers.length && t.groupBy.source) {
        const resolved = resolveHeaderForSource(
          t.groupBy.source,
          { label: t.groupBy.label },
          probe.headers,
          probe.columnLabels || {}
        );
        if (resolved) t.groupBy.source = resolved;
      }
    } else {
      t.groupBy = null;
    }
    if (row._oabpTotalsEnabled) {
      t.totals = [{
        label: row._oabpTotalsLabel || '合计',
        sum: (row._oabpTotalsSumFields && row._oabpTotalsSumFields.length)
          ? row._oabpTotalsSumFields.slice()
          : []
      }];
    } else {
      t.totals = [];
    }
    if (row._oabpFilterRules && row._oabpFilterRules.length) {
      t.content = t.content || {};
      t.content.filter = {
        type: 'group',
        op: 'and',
        children: row._oabpFilterRules.map(r => ({
          type: 'rule',
          field: r.field,
          op: r.op || '==',
          value: r.value != null ? String(r.value) : ''
        }))
      };
      t.content.includeEmptyRows = false;
      t.content.maxRows = 200;
    } else if (t.content) {
      t.content.filter = null;
    }
    return t;
  }

  function hydrateWizardState(row) {
    if (!row.oabpDisplayTemplate) {
      row._oabpDisplayMode = 'table';
      row._oabpSheetName = '项目任务';
      row._oabpColumns = [];
      row._oabpFilterRules = [];
      row._oabpSort = [];
      row._oabpTotalsEnabled = false;
      row._oabpTotalsSumFields = [];
      row._oabpGroupOrder = [];
      return;
    }
    const t = row.oabpDisplayTemplate;
    row._oabpDisplayMode = t.displayMode || 'table';
    row._oabpSheetName = t.sheetName || '项目任务';
    row._oabpColumns = deepClone(t.columns || []);
    row._oabpSort = deepClone(t.sort || []).map((s, idx) => ({
      by: s.by,
      dir: s.dir || 'asc',
      _priority: s._priority || (idx + 1)
    }));
    row._oabpTotalsEnabled = !!(t.totals && t.totals.length && t.totals[0].sum && t.totals[0].sum.length);
    row._oabpTotalsLabel = (t.totals && t.totals[0] && t.totals[0].label) || '合计';
    row._oabpTotalsSumFields = row._oabpTotalsEnabled ? (t.totals[0].sum || []).slice() : [];
    row._oabpGroupOrder = (t.groupBy && t.groupBy.order) ? t.groupBy.order.slice() : [];
    row._oabpFilterRules = [];
    const filter = t.content && t.content.filter;
    if (filter && filter.type === 'group' && filter.op === 'and' && filter.children) {
      filter.children.forEach(child => {
        if (child && child.type === 'rule' && child.field) {
          row._oabpFilterRules.push({
            field: child.field,
            op: child.op || '==',
            value: child.value || ''
          });
        }
      });
    }
  }

  function renderPreviewTable(container, sheet) {
    if (!container) return;
    container.innerHTML = '';
    if (!sheet || !sheet.headers || !sheet.headers.length) {
      container.innerHTML = '<p class="oabp-preview-empty muted">暂无预览数据</p>';
      return;
    }
    const scroll = document.createElement('div');
    scroll.className = 'oabp-preview-scroll';
    const table = document.createElement('table');
    table.className = 'oabp-preview-table';
    const thead = document.createElement('thead');
    const headRow = document.createElement('tr');
    sheet.headers.forEach(h => {
      const th = document.createElement('th');
      th.textContent = h || '';
      headRow.appendChild(th);
    });
    thead.appendChild(headRow);
    table.appendChild(thead);
    const tbody = document.createElement('tbody');
    (sheet.rows || []).slice(0, 50).forEach((row, ri) => {
      const tr = document.createElement('tr');
      if (sheet.displayMeta && sheet.displayMeta.totalRowIndex === ri) {
        tr.className = 'oabp-total-row';
      }
      (row || []).forEach(cell => {
        const td = document.createElement('td');
        td.textContent = cell != null ? cell : '';
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    scroll.appendChild(table);
    container.appendChild(scroll);
  }

  function cachePreviewResult(row, sheet, statusText, statusClass) {
    if (!row) return;
    row._oabpLastPreview = {
      sheet: sheet ? deepClone(sheet) : null,
      statusText: statusText || '',
      statusClass: statusClass || 'ag-oabp-preview-status muted'
    };
  }

  function restorePreviewCache(ctx, index) {
    const row = ctx.bundleItems[index];
    if (!row || !row._oabpLastPreview) return;
    const card = ctx.el.querySelector('.agenda-card[data-i="' + index + '"]');
    if (!card) return;
    const statusEl = card.querySelector('.ag-oabp-preview-status');
    const previewEl = card.querySelector('.ag-oabp-preview-table-wrap');
    const cached = row._oabpLastPreview;
    if (statusEl) {
      statusEl.textContent = cached.statusText;
      statusEl.className = cached.statusClass;
    }
    renderPreviewTable(previewEl, cached.sheet);
  }

  function preserveTransientState(oldItems, newItems) {
    if (!oldItems || !newItems) return;
    const n = Math.min(oldItems.length, newItems.length);
    for (let i = 0; i < n; i++) {
      const o = oldItems[i];
      const nu = newItems[i];
      if (!o || !nu) continue;
      OABP_TRANSIENT_KEYS.forEach(k => {
        if (o[k] !== undefined) nu[k] = deepClone(o[k]);
      });
    }
  }

  async function probeColumns(ctx, index, sql) {
    const key = normalizeSql(sql);
    const cached = PROBE_CACHE.get(key);
    if (cached && Date.now() - cached.ts < PROBE_TTL_MS) {
      return cached.data;
    }
    const data = await AdminApi.fetch(
      '/api/v1/admin/agenda-config/presets/' + ctx.code + '/agenda/' + index + '/oabp-columns',
      { method: 'POST', body: JSON.stringify({ oabpTaskSql: sql }) }
    );
    PROBE_CACHE.set(key, { ts: Date.now(), data: data });
    return data;
  }

  async function ensureProbeForRow(ctx, index, row) {
    if (!(row.oabpTaskSql || '').trim()) return false;
    if (row._oabpProbe && row._oabpProbe.headers && row._oabpProbe.headers.length) return true;
    try {
      row._oabpProbe = await probeColumns(ctx, index, row.oabpTaskSql.trim());
      return !!(row._oabpProbe.headers && row._oabpProbe.headers.length);
    } catch (e) {
      row._oabpProbe = { headers: [], samples: {}, error: e.message };
      return false;
    }
  }

  function schedulePreview(ctx, index) {
    const key = String(index);
    clearTimeout(previewTimers.get(key));
    previewTimers.set(key, setTimeout(() => runPreview(ctx, index), 800));
  }

  /** 取消所有待执行的预览定时器；切换会务类型时调用，防止旧预览完成后触发跨会务 autosave */
  function cancelPending() {
    previewTimers.forEach(t => clearTimeout(t));
    previewTimers.clear();
  }

  async function runPreview(ctx, index) {
    const row = ctx.bundleItems[index];
    const card = ctx.el.querySelector('.agenda-card[data-i="' + index + '"]');
    const statusEl = card ? card.querySelector('.ag-oabp-preview-status') : null;
    const previewEl = card ? card.querySelector('.ag-oabp-preview-table-wrap') : null;
    if (!card) return;
    if (!row || !(row.oabpTaskSql || '').trim()) {
      if (statusEl) statusEl.textContent = '请先填写数据源 SQL';
      if (previewEl) previewEl.innerHTML = '<p class="oabp-preview-empty muted">请先填写数据源 SQL</p>';
      return;
    }
    if (!shouldUseExpertMode(row)) {
      await ensureProbeForRow(ctx, index, row);
    }
    const colsBefore = JSON.stringify((row._oabpColumns || []).map(c => c.source));
    syncRowFromDom(ctx, index);
    const colsAfter = JSON.stringify((row._oabpColumns || []).map(c => c.source));
    if (!shouldUseExpertMode(row) && colsBefore !== colsAfter) {
      row._oabpColumnsBackup = deepClone(row._oabpColumns || []);
      ctx.renderAgendaTable();
      schedulePreview(ctx, index);
      return;
    }
    // 多列排序优先级重复校验
    const priSet = new Set();
    let dupPri = false;
    (row._oabpSort || []).forEach(s => { if (priSet.has(s._priority)) dupPri = true; priSet.add(s._priority); });
    if (statusEl) statusEl.textContent = '预览中…';
    try {
      const data = await AdminApi.fetch(
        '/api/v1/admin/agenda-config/presets/' + ctx.code + '/agenda/' + index + '/oabp-preview',
        {
          method: 'POST',
          body: JSON.stringify({
            oabpTaskSql: row.oabpTaskSql.trim(),
            oabpDisplayTemplate: row.oabpTaskSqlStrict ? null : row.oabpDisplayTemplate,
            oabpTaskSqlStrict: row.oabpTaskSqlStrict === true
          })
        }
      );
      const issues = (data && data.issues) || [];
      const count = data && data.rowCount != null ? data.rowCount : 0;
      if (statusEl) {
        const msgs = issues.slice();
        if (dupPri) msgs.unshift('排序优先级不能重复');
        statusEl.textContent = msgs.length
          ? '校验: ' + msgs.join('; ')
          : '共 ' + count + ' 条（筛选后）';
        statusEl.className = 'ag-oabp-preview-status ' + (msgs.length ? 'oabp-status-warn' : count === 0 ? 'oabp-status-empty' : 'oabp-status-ok');
        cachePreviewResult(row, data && data.sheet, statusEl.textContent, statusEl.className);
      }
      renderPreviewTable(previewEl, data && data.sheet);
      if (!issues.length && !dupPri) ctx.autoSaveBundle();
    } catch (e) {
      if (statusEl) {
        statusEl.textContent = '预览失败: ' + (e.message || '');
        statusEl.className = 'ag-oabp-preview-status oabp-status-error';
      }
    }
  }

  function syncRowFromDom(ctx, index) {
    const row = ctx.bundleItems[index];
    const card = ctx.el.querySelector('.agenda-card[data-i="' + index + '"]');
    if (!row || !card) return;
    const showEl = card.querySelector('.ag-oabp-show');
    if (showEl) row.oabpTaskShow = showEl.checked;
    const strictEl = card.querySelector('.ag-oabp-sql-strict');
    if (strictEl) row.oabpTaskSqlStrict = strictEl.checked;
    if (shouldUseExpertMode(row)) {
      const sqlEl = card.querySelector('.ag-oabp-sql');
      const templateEl = card.querySelector('.ag-oabp-template');
      if (sqlEl) row.oabpTaskSql = sqlEl.value.trim();
      if (templateEl) {
        const raw = templateEl.value.trim();
        if (!raw) {
          row.oabpDisplayTemplate = null;
        } else {
          try {
            row.oabpDisplayTemplate = JSON.parse(raw);
          } catch (_) { /* keep */ }
        }
      }
      return;
    }
    const sheetNameEl = card.querySelector('.ag-oabp-sheet-name');
    if (sheetNameEl) row._oabpSheetName = sheetNameEl.value.trim() || '项目任务';
    const sqlEl = card.querySelector('.ag-oabp-sql');
    if (sqlEl) row.oabpTaskSql = sqlEl.value.trim();
    row.oabpSqlPresetId = 'custom';
    const modeEl = card.querySelector('.ag-oabp-display-mode');
    if (modeEl) row._oabpDisplayMode = modeEl.value;
    const colBySource = {};
    (row._oabpColumnsBackup || row._oabpColumns || []).forEach(c => { if (c && c.source) colBySource[c.source] = c; });
    row._oabpColumns = [];
    row._oabpFilterRules = [];
    const sortEntries = [];
    card.querySelectorAll('.ag-oabp-col-item').forEach(item => {
      const src = item.dataset.source;
      if (!src) return;
      const cb = item.querySelector('.ag-oabp-col-cb');
      const labelEl = item.querySelector('.ag-oabp-col-label');
      const widthEl = item.querySelector('.ag-oabp-col-width');
      const filterOpEl = item.querySelector('.ag-oabp-col-filter-op');
      const filterValEl = item.querySelector('.ag-oabp-filter-value');
      const sortPriEl = item.querySelector('.ag-oabp-col-sort-pri');
      const sortDirEl = item.querySelector('.ag-oabp-col-sort-dir');
      const existing = colBySource[src] || {};
      const label = labelEl ? labelEl.value.trim() || labelForSource(src, row._oabpProbe) : labelForSource(src, row._oabpProbe);
      const col = {
        source: src,
        label,
        visible: cb ? cb.checked : true,
        width: widthEl && widthEl.value ? parseInt(widthEl.value, 10) : existing.width,
        format: existing.format || inferFormat(src, label),
        renderAs: existing.renderAs || inferRenderAs(src, label)
      };
      if (col.format === 'enum' || isStatusColumn(src, label, row._oabpProbe)) {
        if (existing.map) col.map = deepClone(existing.map);
        if (existing.defaultValue != null) col.defaultValue = existing.defaultValue;
        applyStatusColumnFormat(col, row._oabpProbe, src);
      }
      row._oabpColumns.push(col);
      const op = filterOpEl ? filterOpEl.value : 'none';
      if (op && op !== 'none') {
        row._oabpFilterRules.push({
          field: src,
          op,
          value: readFilterValueFromEl(filterValEl)
        });
      }
      const pri = sortPriEl ? parseInt(sortPriEl.value, 10) : 0;
      if (pri > 0) {
        sortEntries.push({ by: src, priority: pri, dir: sortDirEl ? sortDirEl.value : 'asc' });
      }
    });
    sortEntries.sort((a, b) => a.priority - b.priority);
    row._oabpSort = sortEntries.map(e => ({ by: e.by, dir: e.dir, _priority: e.priority }));
    const totalsCb = card.querySelector('.ag-oabp-totals-enable');
    row._oabpTotalsEnabled = totalsCb ? totalsCb.checked : false;
    const totalsLabelEl = card.querySelector('.ag-oabp-totals-label');
    row._oabpTotalsLabel = totalsLabelEl ? totalsLabelEl.value : '合计';
    row._oabpTotalsSumFields = [];
    card.querySelectorAll('.ag-oabp-totals-sum-cb:checked').forEach(cb => {
      if (cb.dataset.source) row._oabpTotalsSumFields.push(cb.dataset.source);
    });
    row._oabpGroupOrder = [];
    card.querySelectorAll('.ag-oabp-group-order-item').forEach(item => {
      if (item.dataset.key) row._oabpGroupOrder.push(item.dataset.key);
    });
    if (row._oabpProbe && row._oabpProbe.headers && row._oabpProbe.headers.length) {
      row._oabpColumns = reconcileColumns(row._oabpProbe, row._oabpColumns, row.oabpDisplayTemplate);
      reconcileFilterSort(row, row._oabpProbe);
      row.oabpSqlPresetId = 'custom';
    }
    row.oabpDisplayTemplate = buildTemplateFromRow(row);
  }

  function buildSummaryTag(row) {
    if (row && row.oabpTaskSqlStrict) {
      return '严格按 SQL';
    }
    if (!row || !row.oabpDisplayTemplate || !row.oabpDisplayTemplate.columns || !row.oabpDisplayTemplate.columns.length) {
      return '';
    }
    const t = row.oabpDisplayTemplate;
    const parts = [t.sheetName || '项目任务'];
    parts.push(t.displayMode === 'grouped_table' ? '按状态分组' : '平铺表格');
    const vis = (t.columns || []).filter(c => c.visible !== false).length;
    parts.push(vis + '列');
    let filterCount = 0;
    const f = t.content && t.content.filter;
    if (f && f.children) filterCount = f.children.filter(c => c && c.type === 'rule').length;
    if (filterCount) parts.push(filterCount + '个筛选');
    if (t.sort && t.sort.length) parts.push(t.sort.length + '级排序');
    if (t.totals && t.totals.length) parts.push('含合计');
    return parts.join(' · ');
  }

  /** 需要多选比较值的筛选运算符（值以英文逗号写入模板）。 */
  const MULTI_VALUE_FILTER_OPS = ['in', 'not_contains', 'contains', 'between'];

  function isMultiValueFilterOp(op) {
    return MULTI_VALUE_FILTER_OPS.includes(op);
  }

  function parseFilterValueList(value) {
    if (value == null || value === '') return [];
    if (Array.isArray(value)) return value.map(v => String(v));
    return String(value).split(',').map(v => v.trim()).filter(Boolean);
  }

  function readFilterValueFromEl(el) {
    if (!el) return '';
    const multiWrap = el.classList && el.classList.contains('ag-oabp-filter-multi')
      ? el
      : (el.closest ? el.closest('.ag-oabp-filter-multi') : null);
    if (multiWrap) {
      return Array.from(multiWrap.querySelectorAll('.ag-oabp-filter-value-cb:checked'))
        .map(cb => cb.value)
        .join(',');
    }
    if (el.tagName === 'SELECT' && el.multiple) {
      return Array.from(el.selectedOptions).map(o => o.value).join(',');
    }
    return el.value != null ? String(el.value) : '';
  }

  function renderFilterValueInput(rule, samples, esc) {
    const fieldSamples = (samples && rule.field && samples[rule.field]) || [];
    const opsNoValue = ['is_empty', 'is_not_empty'];
    if (opsNoValue.includes(rule.op)) return '<span class="muted">—</span>';

    const selected = parseFilterValueList(rule.value);
    const multi = isMultiValueFilterOp(rule.op);

    if (multi && fieldSamples.length > 0 && fieldSamples.length <= 20) {
      const maxPick = rule.op === 'between' ? 2 : fieldSamples.length;
      const hint = rule.op === 'between' ? '选 2 个起止值' : '勾选多项';
      const opts = fieldSamples.map(v => {
        const chk = selected.includes(String(v)) ? ' checked' : '';
        return '<label class="ag-oabp-filter-multi-opt">'
          + '<input type="checkbox" class="ag-oabp-filter-value-cb" value="' + esc(v) + '"' + chk + '/>'
          + '<span>' + esc(v) + '</span></label>';
      }).join('');
      return '<div class="ag-oabp-filter-value ag-oabp-filter-multi" data-max="' + maxPick + '" data-op="' + esc(rule.op) + '">'
        + '<span class="ag-oabp-filter-multi-hint muted">' + hint + '</span>'
        + '<div class="ag-oabp-filter-multi-options">' + opts + '</div></div>';
    }

    if (multi) {
      const placeholder = rule.op === 'between' ? '最小值,最大值' : '多个值用英文逗号分隔';
      return '<input class="ag-oabp-filter-value ui-input" type="text" value="' + esc(rule.value || '') + '" placeholder="' + esc(placeholder) + '"/>';
    }

    if (fieldSamples.length > 0 && fieldSamples.length <= 20) {
      const opts = fieldSamples.map(v =>
        '<option value="' + esc(v) + '"' + (String(rule.value) === String(v) ? ' selected' : '') + '>' + esc(v) + '</option>'
      ).join('');
      return '<select class="ag-oabp-filter-value ui-input">' + opts + '</select>';
    }
    return '<input class="ag-oabp-filter-value ui-input" type="text" value="' + esc(rule.value || '') + '"/>';
  }

  function renderColumnItem(col, row, esc) {
    const probe = row._oabpProbe || { samples: {}, columnLabels: {} };
    const checked = col.visible !== false ? ' checked' : '';
    const w = col.width != null ? col.width : '';
    const filterRule = (row._oabpFilterRules || []).find(r => r.field === col.source);
    const sortIndex = (row._oabpSort || []).findIndex(s => s.by === col.source);
    const sortEntry = sortIndex >= 0 ? row._oabpSort[sortIndex] : null;
    const sortPriority = sortEntry ? (sortEntry._priority || (sortIndex + 1)) : 0;
    const filterOps = [{ id: 'none', label: '无筛选' }].concat(displayOptions && displayOptions.filterRuleOps || []);
    const filterOpOpts = filterOps.map(o =>
      '<option value="' + esc(o.id) + '"' + ((filterRule ? filterRule.op : 'none') === o.id ? ' selected' : '') + '>' + esc(o.label) + '</option>'
    ).join('');
    const filterValueHtml = filterRule
      ? renderFilterValueInput(filterRule, probe.samples, esc)
      : '<span class="ag-oabp-filter-placeholder muted">—</span>';
    const usedPriority = (row._oabpSort || []).length;
    const maxPriority = Math.max(usedPriority + 1, sortPriority);
    const priOpts = ['<option value="0"' + (sortPriority === 0 ? ' selected' : '') + '>不排序</option>'];
    for (let p = 1; p <= maxPriority; p++) {
      priOpts.push('<option value="' + p + '"' + (sortPriority === p ? ' selected' : '') + '>' + p + '</option>');
    }
    const sortDirOpts = (displayOptions && displayOptions.sortDirs || [{ id: 'asc', label: '升序' }, { id: 'desc', label: '降序' }]).map(d =>
      '<option value="' + esc(d.id) + '"' + ((sortEntry ? sortEntry.dir : 'asc') === d.id ? ' selected' : '') + '>' + esc(d.label) + '</option>'
    ).join('');
    return `<div class="ag-oabp-col-item" draggable="true" data-source="${esc(col.source)}">
      <span class="ag-oabp-drag-handle" title="拖动排序">≡</span>
      <input type="checkbox" class="ag-oabp-col-cb" data-source="${esc(col.source)}"${checked}/>
      <input type="text" class="ag-oabp-col-label ui-input" data-source="${esc(col.source)}" value="${esc(col.label || col.source)}" title="列标题"/>
      <input type="number" class="ag-oabp-col-width ui-input" min="60" max="600" placeholder="宽" value="${w}" title="列宽(px)"/>
      <select class="ag-oabp-col-filter-op ui-input" title="筛选">${filterOpOpts}</select>
      <span class="ag-oabp-col-filter-value-wrap">${filterValueHtml}</span>
      <select class="ag-oabp-col-sort-pri ui-input" title="排序优先级">${priOpts.join('')}</select>
      <select class="ag-oabp-col-sort-dir ui-input" title="排序方向"${sortPriority ? '' : ' disabled'}>${sortDirOpts}</select>
    </div>`;
  }

  function renderGroupOrderHtml(row, esc) {
    if ((row._oabpDisplayMode || 'table') !== 'grouped_table') return '';
    const gb = row.oabpDisplayTemplate && row.oabpDisplayTemplate.groupBy;
    const map = (gb && gb.map) || defaultStatusMap();
    const order = (row._oabpGroupOrder && row._oabpGroupOrder.length)
      ? row._oabpGroupOrder
      : (gb && gb.order) || Object.keys(map);
    const items = order.map(key =>
      `<div class="ag-oabp-group-order-item" draggable="true" data-key="${esc(key)}">
        <span class="ag-oabp-drag-handle">≡</span> ${esc(map[key] || key)}
      </div>`
    ).join('');
    return `<div class="oabp-wizard-step oabp-group-order-step">
      <h4>分组顺序</h4>
      <div class="ag-oabp-group-order-list">${items}</div>
    </div>`;
  }

  function renderSimplePanel(row, index, esc, ctx) {
    hydrateWizardState(row);
    reconcileRowState(row);
    row._oabpColumnsBackup = deepClone(row._oabpColumns || []);
    const probe = row._oabpProbe || { headers: [], samples: {}, columnLabels: {} };
    const headers = probe.headers || [];
    const displayOpts = ['<option value="">选择展示模板预设…</option>'].concat(
      displayPresets.map(p => '<option value="' + esc(p.id) + '">' + esc(p.name) + '</option>')
    ).join('');
    const modeOpts = (displayOptions.displayModes || []).map(m =>
      '<option value="' + esc(m.id) + '"' + ((row._oabpDisplayMode || 'table') === m.id ? ' selected' : '') + '>' + esc(m.label) + '</option>'
    ).join('');
    const colHtml = (row._oabpColumns || []).map(col => renderColumnItem(col, row, esc)).join('');
    const numericCols = (row._oabpColumns || []).filter(c => c.format === 'percent' || c.format === 'number');
    const totalsSumHtml = numericCols.map(c =>
      `<label><input type="checkbox" class="ag-oabp-totals-sum-cb" data-source="${esc(c.source)}"${(row._oabpTotalsSumFields || []).includes(c.source) ? ' checked' : ''}/> ${esc(c.label || c.source)}</label>`
    ).join('');
    const probeStatusText = probe.error
      ? '探测失败：' + esc(probe.error)
      : (headers.length ? '已探测字段：' + esc(headers.join('、')) : '填写 SQL 后自动探测字段');
    const disabled = !(row.oabpTaskSql || '').trim() ? ' oabp-wizard-disabled' : '';
    const strictDisabled = row.oabpTaskSqlStrict ? ' oabp-wizard-strict' : '';
    return `
      <div class="oabp-wizard${disabled}${strictDisabled}">
        <div class="oabp-wizard-step">
          <h4>① 数据源（自定义 SQL）</h4>
          <textarea class="ag-oabp-sql code-area" rows="4" placeholder="SELECT 待办事项, 状态, 进度 FROM jq_project_task_tracking WHERE deleted = 0">${esc(row.oabpTaskSql || '')}</textarea>
          <p class="doc-inline-hint-block muted">仅支持只读 SELECT；修改后约 1 秒自动探测字段并更新下方列配置。勾选「严格按 SQL」时将跳过②③步展示规则。</p>
          <span class="ag-oabp-probe-status muted">${probeStatusText}</span>
        </div>
        <div class="oabp-wizard-step">
          <h4>② 展示方式</h4>
          <div class="oabp-wizard-row">
            <select class="ag-oabp-display-preset ui-input">${displayOpts}</select>
            <button type="button" class="ui-btn ui-btn-secondary ag-oabp-apply-display-preset">应用</button>
          </div>
          <div class="oabp-wizard-row">
            <select class="ag-oabp-display-mode ui-input">${modeOpts}</select>
            <label>表格标题 <input class="ag-oabp-sheet-name ui-input" type="text" value="${esc(row._oabpSheetName || '项目任务')}"/></label>
          </div>
          <div class="oabp-wizard-row oabp-totals-row">
            <label><input type="checkbox" class="ag-oabp-totals-enable"${row._oabpTotalsEnabled ? ' checked' : ''}/> 显示合计行</label>
            <input type="text" class="ag-oabp-totals-label ui-input" value="${esc(row._oabpTotalsLabel || '合计')}" placeholder="合计标签"/>
            <span class="ag-oabp-totals-sum-fields">${totalsSumHtml || '<span class="muted">暂无可求和字段</span>'}</span>
          </div>
        </div>
        <div class="oabp-wizard-step">
          <h4>③ 显示列（含筛选与排序）</h4>
          <p class="muted" style="font-size:12px;margin:0 0 6px">拖动 ≡ 调整顺序；列宽 px；每列可单独设置筛选与排序优先级（1=首要）</p>
          <div class="ag-oabp-col-list">${colHtml || '<p class="muted">请先填写数据源 SQL，字段将自动探测</p>'}</div>
        </div>
        ${renderGroupOrderHtml(row, esc)}
        <div class="oabp-wizard-actions">
          <button type="button" class="ui-btn ui-btn-secondary ag-oabp-clear-template">清除展示模板</button>
          <span class="ag-oabp-preview-status muted" data-i="${index}"></span>
        </div>
        <p class="oabp-save-hint muted">保存后若需同步到未开始会议，请点上方「执行刷新未开会」</p>
        <div class="oabp-preview-panel">
          <p class="oabp-preview-label">实时预览</p>
          <div class="ag-oabp-preview-table-wrap"></div>
        </div>
      </div>`;
  }

  function renderExpertPanel(row, index, esc) {
    const templateJson = row.oabpDisplayTemplate ? JSON.stringify(row.oabpDisplayTemplate, null, 2) : '';
    let mapEditor = '';
    if (row.oabpDisplayTemplate && row.oabpDisplayTemplate.columns) {
      const enumCols = row.oabpDisplayTemplate.columns.filter(c => c && c.format === 'enum');
      if (enumCols.length) {
        mapEditor = enumCols.map(col => {
          const entries = Object.entries(col.map || {}).map(([k, v]) =>
            `<div class="ag-oabp-map-row"><input class="ag-oabp-map-key ui-input" value="${esc(k)}" data-source="${esc(col.source)}"/><span>→</span><input class="ag-oabp-map-val ui-input" value="${esc(v)}" data-source="${esc(col.source)}"/></div>`
          ).join('');
          return `<details class="ag-oabp-map-editor"><summary>枚举映射：${esc(col.label || col.source)}</summary>${entries}<button type="button" class="secondary ag-oabp-map-add" data-source="${esc(col.source)}">+ 添加映射</button></details>`;
        }).join('');
      }
    }
    return `
      <textarea class="ag-oabp-sql code-area" rows="4" placeholder="SELECT task_name, status_code FROM jq_project_task_tracking WHERE deleted = 0">${esc(row.oabpTaskSql || '')}</textarea>
      <details class="agenda-oabp-template-details" open>
        <summary>展示模板（JSON）</summary>
        <textarea class="ag-oabp-template code-area" rows="8" placeholder='{"displayMode":"table","columns":[...]}'>${esc(templateJson)}</textarea>
        ${mapEditor}
        <div class="agenda-oabp-template-actions">
          <button type="button" class="ui-btn ui-btn-secondary ag-oabp-preview" data-i="${index}">预览效果</button>
          <button type="button" class="ui-btn ui-btn-secondary ag-oabp-probe" data-i="${index}">探测字段</button>
          <button type="button" class="ui-btn ui-btn-secondary ag-oabp-clear-template">清除展示模板</button>
          <span class="ag-oabp-preview-status muted" data-i="${index}"></span>
        </div>
      </details>`;
  }

  function renderOabpSection(row, index, esc, ctx) {
    const expert = shouldUseExpertMode(row);
    const body = expert ? renderExpertPanel(row, index, esc) : renderSimplePanel(row, index, esc, ctx);
    const hints = (global.AdminHints && global.AdminHints.presets && global.AdminHints.presets.oabpTaskSql) || 'oabp 只读 SQL；展示规则由下方模板配置';
    return `
      <section class="agenda-card-oabp">
        <div class="agenda-doc-toolbar">
          <span class="agenda-doc-label">oabp 项目任务资料</span>
          <div class="oabp-mode-toggle">
            <button type="button" class="ui-btn ui-btn-secondary ag-oabp-mode-btn${expert ? '' : ' active'}" data-mode="simple">简洁</button>
            <button type="button" class="ui-btn ui-btn-secondary ag-oabp-mode-btn${expert ? ' active' : ''}" data-mode="expert">专家</button>
          </div>
          <label class="agenda-oabp-show-label">
            <input type="checkbox" class="ag-oabp-show" ${row.oabpTaskShow !== false ? 'checked' : ''}/>
            主持页展示
          </label>
          <label class="agenda-oabp-show-label" title="严格按 SQL 行序与列值展示，不应用展示模板、不做状态分组或徽章渲染">
            <input type="checkbox" class="ag-oabp-sql-strict" ${row.oabpTaskSqlStrict ? 'checked' : ''}/>
            严格按 SQL
          </label>
        </div>
        ${expert ? '<p class="doc-inline-hint-block">' + esc(hints) + '</p>' : ''}
        ${body}
      </section>`;
  }

  function bindOabpSection(ctx, el) {
    el.querySelectorAll('.ag-oabp-mode-btn').forEach(btn => {
      btn.onclick = () => {
        const card = btn.closest('.agenda-card');
        const i = card ? parseInt(card.dataset.i, 10) : -1;
        if (i < 0 || !ctx.bundleItems[i]) return;
        ctx.bundleItems[i]._oabpMode = btn.dataset.mode;
        ctx.renderAgendaTable();
      };
    });
    el.querySelectorAll('.ag-oabp-apply-display-preset').forEach(btn => {
      btn.onclick = () => {
        const card = btn.closest('.agenda-card');
        const i = card ? parseInt(card.dataset.i, 10) : -1;
        const sel = card ? card.querySelector('.ag-oabp-display-preset') : null;
        if (i < 0 || !sel) return;
        const preset = displayPresets.find(p => p.id === sel.value);
        if (!preset || !preset.template) return;
        ctx.bundleItems[i].oabpDisplayTemplate = deepClone(preset.template);
        hydrateWizardState(ctx.bundleItems[i]);
        reconcileRowState(ctx.bundleItems[i]);
        ctx.renderAgendaTable();
        schedulePreview(ctx, i);
      };
    });
    el.querySelectorAll('.ag-oabp-clear-template').forEach(btn => {
      btn.onclick = () => {
        const card = btn.closest('.agenda-card');
        const i = card ? parseInt(card.dataset.i, 10) : -1;
        if (i < 0 || !ctx.bundleItems[i]) return;
        ctx.bundleItems[i].oabpDisplayTemplate = null;
        ctx.bundleItems[i]._oabpFilterRules = [];
        ctx.bundleItems[i]._oabpSort = [];
        ctx.bundleItems[i]._oabpLastPreview = null;
        ctx.renderAgendaTable();
        ctx.autoSaveBundle();
      };
    });
    // 简洁模式 SQL textarea：debounce 探测 → reconcile → 重渲染 + 预览
    el.querySelectorAll('.ag-oabp-sql').forEach(inp => {
      let sqlTimer = null;
      inp.addEventListener('input', () => {
        const card = inp.closest('.agenda-card');
        const i = card ? parseInt(card.dataset.i, 10) : -1;
        if (i < 0) return;
        const row = ctx.bundleItems[i];
        row.oabpTaskSql = inp.value.trim();
        row.oabpSqlPresetId = 'custom';
        if (shouldUseExpertMode(row)) return;
        clearTimeout(sqlTimer);
        sqlTimer = setTimeout(async () => {
          if (!(row.oabpTaskSql || '').trim()) return;
          try {
            const probe = await probeColumns(ctx, i, row.oabpTaskSql);
            row._oabpProbe = probe;
            reconcileRowState(row);
          } catch (e) {
            row._oabpProbe = { headers: [], samples: {}, error: e.message };
          }
          ctx.renderAgendaTable();
          schedulePreview(ctx, i);
        }, 800);
      });
    });
    // 结构性变更（需重渲染以更新列内控件）：筛选 op / 排序优先级 / 排序方向 / 展示模式
    const structuralSel = '.ag-oabp-col-filter-op, .ag-oabp-col-sort-pri, .ag-oabp-col-sort-dir, .ag-oabp-display-mode';
    el.querySelectorAll(structuralSel).forEach(inp => {
      inp.addEventListener('change', () => {
        const card = inp.closest('.agenda-card');
        const i = card ? parseInt(card.dataset.i, 10) : -1;
        if (i < 0) return;
        syncRowFromDom(ctx, i);
        ctx.renderAgendaTable();
        schedulePreview(ctx, i);
      });
    });
    // 非结构性变更：先 sync 到内存，再 debounce 预览（避免期间 render 用旧状态复原）
    const previewInputs = '.ag-oabp-sheet-name, .ag-oabp-col-cb, .ag-oabp-col-label, .ag-oabp-filter-value, .ag-oabp-show, .ag-oabp-sql-strict, .ag-oabp-col-width, .ag-oabp-totals-enable, .ag-oabp-totals-label, .ag-oabp-totals-sum-cb';
    el.querySelectorAll(previewInputs).forEach(inp => {
      inp.addEventListener('change', () => {
        const card = inp.closest('.agenda-card');
        const i = card ? parseInt(card.dataset.i, 10) : -1;
        if (i < 0) return;
        syncRowFromDom(ctx, i);
        schedulePreview(ctx, i);
      });
    });
    el.querySelectorAll('.ag-oabp-filter-value-cb').forEach(cb => {
      cb.addEventListener('change', () => {
        const wrap = cb.closest('.ag-oabp-filter-multi');
        if (wrap && wrap.dataset.op === 'between') {
          const max = parseInt(wrap.dataset.max, 10) || 2;
          const checked = wrap.querySelectorAll('.ag-oabp-filter-value-cb:checked');
          if (checked.length > max) {
            cb.checked = false;
            return;
          }
        }
        const card = cb.closest('.agenda-card');
        const i = card ? parseInt(card.dataset.i, 10) : -1;
        if (i < 0) return;
        syncRowFromDom(ctx, i);
        schedulePreview(ctx, i);
      });
    });
    el.querySelectorAll('.ag-oabp-sql-strict').forEach(inp => {
      inp.addEventListener('change', () => {
        const card = inp.closest('.agenda-card');
        const i = card ? parseInt(card.dataset.i, 10) : -1;
        if (i < 0) return;
        syncRowFromDom(ctx, i);
        ctx.renderAgendaTable();
        schedulePreview(ctx, i);
      });
    });
    el.querySelectorAll('.ag-oabp-sql, .ag-oabp-template').forEach(inp => {
      inp.addEventListener('blur', () => ctx.autoSaveBundle());
    });
    el.querySelectorAll('.ag-oabp-probe').forEach(btn => btn.onclick = async () => {
      const i = +btn.dataset.i;
      const card = el.querySelector('.agenda-card[data-i="' + i + '"]');
      const statusEl = el.querySelector('.ag-oabp-preview-status[data-i="' + i + '"]');
      const sqlEl = card ? card.querySelector('.ag-oabp-sql') : null;
      if (!sqlEl || !sqlEl.value.trim()) {
        if (statusEl) statusEl.textContent = '请先填写 SQL';
        return;
      }
      if (statusEl) statusEl.textContent = '探测中…';
      try {
        const data = await probeColumns(ctx, i, sqlEl.value.trim());
        const row = ctx.bundleItems[i];
        if (row) {
          row._oabpProbe = data;
          if (!shouldUseExpertMode(row)) reconcileRowState(row);
        }
        if (statusEl) statusEl.textContent = '字段: ' + ((data.headers || []).join(', '));
      } catch (e) {
        if (statusEl) statusEl.textContent = '探测失败: ' + (e.message || '');
      }
    });
    el.querySelectorAll('.ag-oabp-preview').forEach(btn => btn.onclick = () => runPreview(ctx, +btn.dataset.i));

    function setupDragReorder(listEl, itemSel, keyAttr) {
      if (!listEl) return;
      let dragKey = null;
      listEl.querySelectorAll(itemSel).forEach(item => {
        item.addEventListener('dragstart', e => {
          dragKey = item.getAttribute(keyAttr);
          e.dataTransfer.effectAllowed = 'move';
        });
        item.addEventListener('dragover', e => { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; });
        item.addEventListener('drop', e => {
          e.preventDefault();
          const card = item.closest('.agenda-card');
          const i = card ? parseInt(card.dataset.i, 10) : -1;
          if (i < 0 || !dragKey) return;
          const targetKey = item.getAttribute(keyAttr);
          if (!targetKey || dragKey === targetKey) return;
          const row = ctx.bundleItems[i];
          if (itemSel === '.ag-oabp-col-item') {
            const cols = row._oabpColumns || [];
            const from = cols.findIndex(c => c.source === dragKey);
            const to = cols.findIndex(c => c.source === targetKey);
            if (from < 0 || to < 0) return;
            const moved = cols.splice(from, 1)[0];
            cols.splice(to, 0, moved);
            row._oabpColumns = cols;
          } else {
            const order = row._oabpGroupOrder || [];
            const from = order.indexOf(dragKey);
            const to = order.indexOf(targetKey);
            if (from < 0 || to < 0) return;
            order.splice(from, 1);
            order.splice(to, 0, dragKey);
            row._oabpGroupOrder = order;
          }
          ctx.renderAgendaTable();
          schedulePreview(ctx, i);
        });
      });
    }
    el.querySelectorAll('.agenda-card-oabp').forEach(section => {
      setupDragReorder(section.querySelector('.ag-oabp-col-list'), '.ag-oabp-col-item', 'data-source');
      setupDragReorder(section.querySelector('.ag-oabp-group-order-list'), '.ag-oabp-group-order-item', 'data-key');
    });
    ctx.bundleItems.forEach((row, i) => {
      if ((row.oabpTaskSql || '').trim() && row._oabpLastPreview) {
        restorePreviewCache(ctx, i);
      }
    });
  }

  async function initRows(ctx) {
    await ensurePresets();
    for (let i = 0; i < ctx.bundleItems.length; i++) {
      const row = ctx.bundleItems[i];
      if (!(row.oabpTaskSql || '').trim()) continue;
      hydrateWizardState(row);
      if (shouldUseExpertMode(row)) continue;
      const needProbe = !row._oabpProbe || !(row._oabpProbe.headers && row._oabpProbe.headers.length);
      if (needProbe) {
        try {
          row._oabpProbe = await probeColumns(ctx, i, row.oabpTaskSql.trim());
        } catch (_) {
          row._oabpProbe = { headers: [], samples: {} };
        }
      }
      reconcileRowState(row);
    }
    if (ctx.el && ctx.renderAgendaTable) {
      ctx.renderAgendaTable();
      for (let i = 0; i < ctx.bundleItems.length; i++) {
        const row = ctx.bundleItems[i];
        if ((row.oabpTaskSql || '').trim() && !shouldUseExpertMode(row)) {
          schedulePreview(ctx, i);
        }
      }
    }
  }

  global.OabpDisplayWizard = {
    ensurePresets,
    initRows,
    renderOabpSection,
    bindOabpSection,
    syncRowFromDom,
    shouldUseExpertMode,
    buildSummaryTag,
    preserveTransientState,
    restorePreviewCache,
    cancelPending
  };

  if (!document.getElementById('oabp-wizard-styles')) {
    const style = document.createElement('style');
    style.id = 'oabp-wizard-styles';
    style.textContent = `
      .oabp-mode-toggle { display: inline-flex; gap: 4px; margin-right: 12px; }
      .oabp-mode-toggle .active { background: var(--ui-accent, #2563eb); color: #fff; }
      .oabp-wizard { display: grid; gap: 12px; margin-top: 8px; }
      .oabp-wizard-step h4 { margin: 0 0 6px; font-size: 13px; }
      .oabp-wizard-row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-top: 6px; }
      .oabp-wizard-disabled { opacity: 0.55; pointer-events: none; }
      .ag-oabp-probe-status { display: block; font-size: 12px; margin-top: 4px; }
      .ag-oabp-col-list { display: flex; flex-direction: column; gap: 6px; }
      .ag-oabp-col-item { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; padding: 6px; border: 1px solid rgba(0,0,0,.08); border-radius: 6px; background: #fff; }
      .ag-oabp-drag-handle { cursor: grab; color: #888; user-select: none; }
      .ag-oabp-col-label { min-width: 8rem; flex: 1 1 10rem; }
      .ag-oabp-col-width { width: 4.5rem; }
      .ag-oabp-col-filter-op { min-width: 5.5rem; }
      .ag-oabp-col-filter-value-wrap { display: inline-flex; align-items: flex-start; }
      .ag-oabp-filter-multi { display: flex; flex-direction: column; gap: 2px; min-width: 10rem; max-width: 20rem; }
      .ag-oabp-filter-multi-hint { font-size: 11px; line-height: 1.2; }
      .ag-oabp-filter-multi-options {
        display: flex; flex-wrap: wrap; gap: 4px 10px;
        max-height: 6.5rem; overflow-y: auto;
        padding: 6px 8px; border: 1px solid rgba(0,0,0,.12);
        border-radius: 6px; background: #fff;
      }
      .ag-oabp-filter-multi-opt {
        display: inline-flex; align-items: center; gap: 4px;
        font-size: 12px; white-space: nowrap; cursor: pointer; user-select: none;
      }
      .ag-oabp-filter-multi-opt input { margin: 0; cursor: pointer; }
      .ag-oabp-filter-placeholder { font-size: 12px; }
      .ag-oabp-col-sort-pri { width: 4.5rem; }
      .ag-oabp-group-order-list { display: flex; flex-direction: column; gap: 4px; }
      .ag-oabp-group-order-item { padding: 4px 8px; border: 1px dashed rgba(0,0,0,.15); border-radius: 4px; }
      .oabp-total-row { font-weight: 600; background: rgba(0,0,0,.04); }
      .oabp-save-hint { font-size: 12px; margin: 4px 0 0; }
      .oabp-preview-panel { border: 1px solid rgba(0,0,0,.08); border-radius: 8px; padding: 8px; background: rgba(0,0,0,.02); }
      .oabp-preview-scroll { overflow: auto; max-height: 240px; }
      .oabp-preview-table { width: 100%; border-collapse: collapse; font-size: 12px; }
      .oabp-preview-table th, .oabp-preview-table td { border: 1px solid rgba(0,0,0,.08); padding: 4px 8px; text-align: left; }
      .oabp-status-ok { color: #15803d; }
      .oabp-status-warn { color: #b45309; }
      .oabp-status-error { color: #b91c1c; }
      .oabp-status-empty { color: #92400e; }
      .oabp-wizard-strict .oabp-wizard-step:nth-child(n+2) {
        opacity: 0.45;
        pointer-events: none;
        user-select: none;
      }
    `;
    document.head.appendChild(style);
  }
})(typeof globalThis !== 'undefined' ? globalThis : window);
