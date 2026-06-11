AdminModules.register({
  route: '/settings',
  mount: async function (root) {
    const schema = await AdminApi.fetch('/api/v1/admin/system-config/schema');
    ensureSettingsStyles();

    const categoryLabel = {
      host: 'AI 主持',
      minute: '会后纪要',
      asr: '语音识别与声纹',
      pipeline: '流水线',
      scheduler: '会前调度',
      notification: '通知',
      web: 'Web 页面',
      openclaw: 'OpenClaw',
      infra: '基础设施'
    };
    const catOrder = ['host', 'minute', 'asr', 'pipeline', 'scheduler', 'notification', 'web', 'openclaw', 'infra'];

    const hotCount = schema.filter(s => reloadTier(s) === 'hot').length;
    const coldCount = schema.filter(s => reloadTier(s) === 'cold').length;

    const globalRoots = buildConfigTree(schema);
    const rootsByCat = {};
    globalRoots.forEach(root => {
      const cat = root.category || '_other';
      if (!rootsByCat[cat]) rootsByCat[cat] = [];
      rootsByCat[cat].push(root);
    });
    const sortedCats = catOrder.filter(c => rootsByCat[c]?.length)
      .concat(Object.keys(rootsByCat).filter(c => c !== '_other' && !catOrder.includes(c)).sort())
      .concat(rootsByCat._other?.length ? ['_other'] : []);

    const keyIndex = {};
    schema.forEach(s => { keyIndex[s.key] = s; });

    let html = '<div class="cfg-page">'
      + '<div class="cfg-toolbar panel">'
      + '<p class="module-intro">' + esc(AdminHints.settings.moduleIntro) + '</p>'
      + '<div class="cfg-tier-legend">'
      + '<span class="cfg-badge cfg-badge--hot">可热加载</span>'
      + '<span class="cfg-tier-legend-text">保存至 DB，点「热加载」生效</span>'
      + '<span class="cfg-badge cfg-badge--cold">冷启动</span>'
      + '<span class="cfg-tier-legend-text">改 application.yml / env 后须重启</span>'
      + '</div>'
      + '<p class="cfg-tier-stats">可热加载 ' + hotCount + ' 项 · 冷启动 ' + coldCount + ' 项</p>'
      + '<div class="cfg-tier-filter" role="group" aria-label="按配置层级筛选">'
      + '<button type="button" class="cfg-tier-chip cfg-tier-chip--active" data-tier-filter="all">全部</button>'
      + '<button type="button" class="cfg-tier-chip" data-tier-filter="hot">可热加载</button>'
      + '<button type="button" class="cfg-tier-chip" data-tier-filter="cold">冷启动</button>'
      + '</div>'
      + '<div class="cfg-toolbar-actions">'
      + '<button class="primary" id="reload-rt" title="' + escAttr(AdminHints.settings.reloadRuntime) + '">通知 meeting-server 热加载</button>'
      + '<button id="show-audit" title="' + escAttr(AdminHints.settings.audit) + '">最近审计</button>'
      + '</div></div>'
      + '<pre id="audit-out" class="panel hidden"></pre>'
      + '<div class="cfg-tree cfg-tree--unified">';

    for (const cat of sortedCats) {
      const roots = rootsByCat[cat];
      const infra = cat === 'infra';
      const branchTier = infra ? 'cold' : 'hot';
      const branchPrefix = infra ? '【冷启动 · YAML】' : '【可热加载】';
      const branchClass = infra ? ' cfg-branch-head--cold' : ' cfg-branch-head--hot';
      html += '<div class="cfg-branch-head' + branchClass + '" data-reload-tier="' + branchTier + '" role="heading" aria-level="3">'
        + esc(branchPrefix + (categoryLabel[cat] || cat)) + '</div>'
        + renderTreeNodes(roots, keyIndex, 0);
    }
    html += '</div></div>';
    root.innerHTML = html;

    bindSettingsEvents(root, keyIndex);
    bindTierFilter(root);
    refreshCascadeUi(root, keyIndex);

    document.getElementById('reload-rt').onclick = async () => {
      const r = await AdminApi.fetch('/api/v1/admin/system-config/reload-runtime', { method: 'POST' });
      toast(r.reloaded ? 'reload 成功' : 'reload 失败');
    };
    document.getElementById('show-audit').onclick = async () => {
      const rows = await AdminApi.fetch('/api/v1/admin/system-config/audit?limit=20');
      const el = document.getElementById('audit-out');
      el.classList.remove('hidden');
      el.textContent = JSON.stringify(rows, null, 2);
    };
  }
});

function ensureSettingsStyles() {
  if (document.getElementById('settings-config-css')) return;
  const link = document.createElement('link');
  link.id = 'settings-config-css';
  link.rel = 'stylesheet';
  link.href = '/static/admin/settings-config.css';
  document.head.appendChild(link);
}

function reloadTier(s) {
  if (s.requiresRestart === true) return 'cold';
  if (s.hotReloadable === true) return 'hot';
  return 'unknown';
}

function buildConfigTree(items) {
  const byKey = {};
  items.forEach(s => { byKey[s.key] = Object.assign({}, s, { children: [] }); });
  const roots = [];
  items.forEach(s => {
    const node = byKey[s.key];
    const pk = s.parentKey;
    if (pk && byKey[pk]) {
      byKey[pk].children.push(node);
    } else {
      roots.push(node);
    }
  });
  const sortNodes = nodes => {
    nodes.sort((a, b) => nodeSortKey(a).localeCompare(nodeSortKey(b), 'zh-CN'));
    nodes.forEach(n => sortNodes(n.children));
  };
  sortNodes(roots);
  return roots;
}

function nodeSortKey(n) {
  const siblingOrder = {
    'meeting.host.tts-enabled': '01',
    'meeting.host.agenda-enabled': '02',
    'meeting.host.topic-timeout-strategy': '01',
    'meeting.host.roll-call-enabled': '03',
    'meeting.host.auto-roll-call-after-opening': '01',
    'meeting.host.roll-call.window-seconds': '02',
    'meeting.host.roll-call.asr-grace-seconds': '03',
    'meeting.host.roll-call.online-inventory-seconds': '04',
    'meeting.host.reminder.topic-minutes-left': '04',
    'meeting.host.reminder.meeting-minutes-left': '05',
    'meeting.minute.llm-enabled': '01',
    'meeting.minute.ai-enhancement-enabled': '02',
    'meeting.minute.feishu-doc-enabled': '03',
    'meeting.minute.notify-enabled': '04',
    'meeting.minute.persist-enabled': '05',
    'meeting.minute.expose-content-in-api': '06',
    'meeting.todo.extraction-enabled': '07',
    'meeting.asr.offline-enabled': '01',
    'meeting.asr.realtime-enabled': '02',
    'meeting.asr.offline-role-enabled': '01',
    'meeting.asr.offline-role-mode': '02',
    'meeting.asr.offline-role-num-hint-enabled': '03',
    'meeting.asr.offline-ist-max-role-num': '04',
    'meeting.asr.offline-ist-max-feature-ids': '05',
    'meeting.asr.offline-poll-max-retries': '06',
    'meeting.asr.offline-poll-interval-ms': '07',
    'meeting.isv.enabled': '08',
    'meeting.isv.match-score-threshold': '09',
    'meeting.isv.search-top-k-max': '10',
    'meeting.isv.search-top-k-min': '11',
    'meeting.isv.min-slice-bytes': '12',
    'meeting.isv.min-segment-ms-for-slice': '13',
    'meeting.voiceprint.offline-label-enabled': '14',
    'meeting.voiceprint.offline-min-slice-ms': '15',
    'meeting.voiceprint.offline-max-slice-ms': '16',
    'meeting.voiceprint.offline-max-speakers': '17',
    'meeting.voiceprint.offline-vote-slices': '18',
    'meeting.voiceprint.offline-segment-relabel-enabled': '19',
    'meeting.voiceprint.offline-split-cluster-enabled': '20',
    'meeting.voiceprint.offline-split-min-segments': '21',
    'meeting.voiceprint.offline-min-slice-floor-ms': '22',
    'meeting.pipeline.post-auto-trigger.enabled': '01',
    'meeting.pipeline.post-auto-trigger.template-code': '02',
    'meeting.pipeline.pre-on-create-enabled': '03',
    'meeting.scheduler.pre-window-minutes': '01',
    'meeting.scheduler.pre-24h-template-code': '02',
    'meeting.scheduler.pre-10m-template-code': '03',
    'meeting.notification.fallback-direct': '01',
    'openclaw.skill-mode': '01',
    'openclaw.timeout-seconds': '02',
    'openclaw.max-concurrent-invokes': '03'
  };
  if (siblingOrder[n.key]) return siblingOrder[n.key];
  if (n.key.endsWith('.enabled')) return '0_' + n.key;
  if (!n.parentKey) return '1_' + n.key;
  return '2_' + n.key;
}

function renderTreeNodes(nodes, keyIndex, depth) {
  let html = '';
  for (const n of nodes) {
    html += renderConfigRow(n, keyIndex, depth);
    if (n.children && n.children.length) {
      html += '<div class="cfg-children" style="--depth:' + depth + '">' + renderTreeNodes(n.children, keyIndex, depth + 1) + '</div>';
    }
  }
  return html;
}

function renderConfigRow(s, keyIndex, depth) {
  const val = s.currentValue != null ? s.currentValue : s.defaultValue;
  const parsed = s.type === 'BOOLEAN' ? (val === 'true' || val === true) : val;
  const hint = AdminHints.settings[s.key] || '';
  const readOnly = s.requiresRestart === true;
  const disabled = readOnly || s.editable === false;
  const parentLabel = s.parentKey ? shortKey(s.parentKey) : '';
  const blocked = !readOnly && s.parentKey && !s.editable;

  const tier = reloadTier(s);

  let badges = '';
  if (readOnly) badges += '<span class="cfg-badge cfg-badge--cold">冷启动</span>';
  else if (s.hotReloadable) badges += '<span class="cfg-badge cfg-badge--hot">可热加载</span>';
  if (blocked) badges += '<span class="cfg-badge cfg-badge--warn">上级已关闭</span>';
  else if (!readOnly && parentLabel) badges += '<span class="cfg-badge cfg-badge--dep">依赖 ' + esc(parentLabel) + '</span>';

  const indentClass = depth === 0 ? 'cfg-indent cfg-indent--root' : 'cfg-indent';
  const rowClass = 'cfg-row'
    + (depth === 0 && s.key.endsWith('.enabled') ? ' cfg-row--master' : '')
    + (blocked ? ' cfg-row--blocked' : '')
    + (readOnly ? ' cfg-row--cold cfg-row--readonly' : '')
    + (!readOnly && s.hotReloadable ? ' cfg-row--hot' : '');

  let control = '';
  if (s.type === 'BOOLEAN') {
    control = '<label class="cfg-switch cfg-control" title="' + escAttr(s.description) + '">'
      + '<input type="checkbox" data-key="' + escAttr(s.key) + '" data-type="boolean"'
      + (parsed ? ' checked' : '') + (disabled ? ' disabled' : '') + '/>'
      + '<span class="cfg-switch-slider"></span></label>';
  } else if (s.type === 'INTEGER') {
    const minAttr = s.minValue != null ? ' min="' + escAttr(String(s.minValue)) + '"' : '';
    const maxAttr = s.maxValue != null ? ' max="' + escAttr(String(s.maxValue)) + '"' : '';
    control = '<input type="number" class="cfg-input cfg-control" data-key="' + escAttr(s.key) + '" data-type="integer"'
      + minAttr + maxAttr
      + ' value="' + escAttr(String(parsed)) + '"' + (disabled ? ' disabled' : '') + '/>';
  } else {
    const dmin = s.doubleMinValue != null ? ' data-double-min="' + escAttr(String(s.doubleMinValue)) + '"' : '';
    const dmax = s.doubleMaxValue != null ? ' data-double-max="' + escAttr(String(s.doubleMaxValue)) + '"' : '';
    control = '<input class="cfg-input cfg-control" data-key="' + escAttr(s.key) + '" data-type="text"'
      + dmin + dmax
      + ' value="' + escAttr(String(parsed)) + '"' + (disabled ? ' disabled' : '') + '/>';
  }

  let actions = '';
  if (!readOnly) {
    actions = '<div class="cfg-actions">'
      + '<button type="button" data-save="' + escAttr(s.key) + '"' + (disabled ? ' disabled' : '') + '>保存</button>'
      + '<button type="button" class="cfg-btn-reset" data-reset="' + escAttr(s.key) + '">恢复默认</button>'
      + '</div>';
  }

  let hints = '';
  if (hint) hints += '<div class="cfg-hint">' + esc(hint) + '</div>';
  if (s.valueRangeHint) hints += '<div class="cfg-hint cfg-hint--range">取值范围：' + esc(s.valueRangeHint) + '</div>';
  if (readOnly) hints += '<div class="cfg-hint">冷启动：在 application.yml / 环境变量修改并重启 meeting-server</div>';
  else if (s.hotReloadable) hints += '<div class="cfg-hint">可热加载：保存后写入 DB，可点「通知 meeting-server 热加载」</div>';

  return '<div class="cfg-node" data-node-key="' + escAttr(s.key) + '" data-parent-key="' + escAttr(s.parentKey || '') + '" data-reload-tier="' + escAttr(tier) + '">'
    + '<div class="' + rowClass + '" style="--depth:' + depth + '">'
    + '<div class="cfg-row-main">'
    + '<div class="' + indentClass + '" aria-hidden="true"></div>'
    + '<div class="cfg-row-text">'
    + '<div class="cfg-title">' + esc(s.description) + badges + '</div>'
    + '<div class="cfg-key">' + esc(s.key) + '</div>'
    + hints
    + '</div>'
    + control
    + '</div>'
    + actions
    + '</div></div>';
}

function bindTierFilter(root) {
  const tree = root.querySelector('.cfg-tree--unified');
  if (!tree) return;
  root.querySelectorAll('[data-tier-filter]').forEach(btn => {
    btn.onclick = () => {
      root.querySelectorAll('[data-tier-filter]').forEach(b => b.classList.remove('cfg-tier-chip--active'));
      btn.classList.add('cfg-tier-chip--active');
      applyTierFilter(tree, btn.getAttribute('data-tier-filter'));
    };
  });
}

function applyTierFilter(tree, filter) {
  tree.querySelectorAll('.cfg-branch-head[data-reload-tier]').forEach(head => {
    const tier = head.getAttribute('data-reload-tier');
    head.classList.toggle('cfg-tier-hidden', filter !== 'all' && tier !== filter);
  });
  tree.querySelectorAll('.cfg-node[data-reload-tier]').forEach(node => {
    const tier = node.getAttribute('data-reload-tier');
    node.classList.toggle('cfg-tier-hidden', filter !== 'all' && tier !== filter);
  });
}

function bindSettingsEvents(root, keyIndex) {
  const saveKey = async (key) => {
    const inp = root.querySelector('[data-key="' + key + '"]');
    if (!inp || inp.disabled) {
      toast('不可编辑');
      return;
    }
    let v;
    if (inp.type === 'checkbox') v = JSON.stringify(inp.checked);
    else if (inp.getAttribute('data-type') === 'integer') {
      const meta = keyIndex[key];
      const num = parseInt(inp.value, 10);
      if (Number.isNaN(num)) {
        toast('请输入有效整数');
        return;
      }
      if (meta && meta.minValue != null && num < meta.minValue) {
        toast('不能小于 ' + meta.minValue);
        return;
      }
      if (meta && meta.maxValue != null && num > meta.maxValue) {
        toast('不能大于 ' + meta.maxValue);
        return;
      }
      v = JSON.stringify(num);
    } else {
      const dmin = inp.getAttribute('data-double-min');
      const dmax = inp.getAttribute('data-double-max');
      if (dmin != null || dmax != null) {
        const num = parseFloat(inp.value);
        if (Number.isNaN(num)) {
          toast('请输入有效数字');
          return;
        }
        if (dmin != null && num < parseFloat(dmin)) {
          toast('不能小于 ' + dmin);
          return;
        }
        if (dmax != null && num > parseFloat(dmax)) {
          toast('不能大于 ' + dmax);
          return;
        }
      }
      v = JSON.stringify(inp.value);
    }
    const r = await AdminApi.fetch('/api/v1/admin/system-config/entries/' + encodeURIComponent(key), {
      method: 'PUT', body: JSON.stringify({ valueJson: v })
    });
    toast('已保存' + (r.reloaded ? '，已热加载' : '（reload 未成功）'));
    refreshCascadeUi(root, keyIndex);
  };

  root.querySelectorAll('[data-save]').forEach(btn => {
    btn.onclick = () => saveKey(btn.getAttribute('data-save'));
  });
  root.querySelectorAll('[data-reset]').forEach(btn => {
    btn.onclick = async () => {
      const key = btn.getAttribute('data-reset');
      if (!confirm('删除 DB 覆盖，回退 Java 出厂默认？')) return;
      const r = await AdminApi.fetch('/api/v1/admin/system-config/entries/' + encodeURIComponent(key), { method: 'DELETE' });
      const inp = root.querySelector('[data-key="' + key + '"]');
      const meta = keyIndex[key];
      if (inp && meta) {
        if (inp.type === 'checkbox') inp.checked = meta.defaultValue === 'true';
        else {
          try {
            inp.value = typeof meta.defaultValue === 'string' ? JSON.parse(meta.defaultValue) : meta.defaultValue;
          } catch (e) {
            inp.value = meta.defaultValue != null ? String(meta.defaultValue) : '';
          }
        }
      }
      toast('已恢复默认' + (r.reloaded ? '并已 reload' : ''));
      refreshCascadeUi(root, keyIndex);
    };
  });

  root.querySelectorAll('input[data-type="boolean"]').forEach(cb => {
    cb.addEventListener('change', () => refreshCascadeUi(root, keyIndex));
  });
}

function refreshCascadeUi(root, keyIndex) {
  const boolVal = key => {
    const inp = root.querySelector('input[data-key="' + key + '"][data-type="boolean"]');
    if (inp) return inp.checked;
    const meta = keyIndex[key];
    if (!meta) return true;
    const raw = meta.currentValue != null ? meta.currentValue : meta.defaultValue;
    return raw === 'true' || raw === true;
  };

  const isChainEffective = parentKey => {
    if (!parentKey) return true;
    let pk = parentKey;
    const visited = new Set();
    while (pk) {
      if (visited.has(pk)) return true;
      visited.add(pk);
      const meta = keyIndex[pk];
      if (meta && meta.type === 'BOOLEAN' && !boolVal(pk)) return false;
      pk = meta && meta.parentKey ? meta.parentKey : null;
    }
    return true;
  };

  root.querySelectorAll('.cfg-node[data-node-key]').forEach(node => {
    const key = node.getAttribute('data-node-key');
    const meta = keyIndex[key];
    if (!meta || meta.requiresRestart) return;
    const parentKey = meta.parentKey;
    const row = node.querySelector('.cfg-row');
    const inp = node.querySelector('[data-key="' + key + '"]');
    const saveBtn = node.querySelector('[data-save]');
    if (!parentKey) return;

    const ok = isChainEffective(parentKey);
    if (inp && meta.type === 'BOOLEAN') {
      inp.disabled = !ok;
    } else if (inp) {
      inp.disabled = !ok;
    }
    if (saveBtn) saveBtn.disabled = !ok;

    row.classList.toggle('cfg-row--blocked', !ok);
    const title = row.querySelector('.cfg-title');
    if (!title) return;
    title.querySelectorAll('.cfg-badge--warn').forEach(el => el.remove());
    if (!ok) {
      const b = document.createElement('span');
      b.className = 'cfg-badge cfg-badge--warn';
      b.textContent = '上级已关闭';
      title.appendChild(b);
    }
  });
}

function shortKey(key) {
  const parts = key.split('.');
  return parts.length > 1 ? parts[parts.length - 1] : key;
}

function esc(s) {
  if (s == null) return '';
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function escAttr(s) {
  return esc(s).replace(/'/g, '&#39;');
}

function toast(msg) {
  const old = document.querySelector('.cfg-toast');
  if (old) old.remove();
  const el = document.createElement('div');
  el.className = 'cfg-toast';
  el.textContent = msg;
  document.body.appendChild(el);
  setTimeout(() => el.remove(), 2800);
}
