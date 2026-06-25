(function () {
  const TOKEN_KEY = 'sm-admin-token';
  const LAST_ROUTE_KEY = 'sm-admin-last-route';
  const STATIC_ASSET_VERSION = 'sm-ui-20260624-1';
  let modules = [];
  let scriptsLoaded = {};
  let navigateSeq = 0;

  function readJsonSafely(text) {
    if (!text) return null;
    try {
      return JSON.parse(text);
    } catch (_) {
      return null;
    }
  }

  function shorten(text, max) {
    const s = String(text == null ? '' : text);
    return s.length > max ? (s.slice(0, max) + '...') : s;
  }

  function renderModuleError(message) {
    const root = document.getElementById('module-root');
    root.innerHTML = '<div class="ui-card card admin-card"><p class="msg msg-err">' + message + '</p></div>';
  }

  window.AdminApi = {
    token: () => sessionStorage.getItem(TOKEN_KEY) || '',
    fetch: async (path, opts = {}) => {
      const reqOpts = Object.assign({}, opts);
      const headers = Object.assign({ 'X-Admin-Token': AdminApi.token() }, reqOpts.headers || {});
      const hasContentType = headers['Content-Type'] || headers['content-type'];
      if (!(reqOpts.body instanceof FormData) && !hasContentType) {
        headers['Content-Type'] = 'application/json';
      }
      const res = await fetch(path, Object.assign({}, reqOpts, { headers }));
      const text = await res.text();
      const json = readJsonSafely(text);
      if (!res.ok) {
        if (json && json.message) throw new Error(json.message);
        throw new Error('HTTP ' + res.status + ': ' + shorten(text, 160));
      }
      if (!json) {
        throw new Error('服务返回非JSON: ' + shorten(text, 160));
      }
      if (json.code !== 0) throw new Error(json.message || 'request failed');
      return json.data;
    }
  };

  window.AdminModules = {
    registry: {},
    register: function (def) { this.registry[def.route] = def; }
  };

  let resultModalEl = null;
  let resultModalBackdrop = null;
  let resultModalEscHandler = null;
  let resultModalConfirmResolve = null;

  function ensureResultModal() {
    if (resultModalEl) return resultModalEl;
    const el = document.createElement('div');
    el.id = 'admin-result-modal';
    el.className = 'ui-card card admin-card hidden';
    el.innerHTML = '<h3 id="admin-result-modal-title"></h3>'
      + '<div id="admin-result-modal-body-wrap"><pre id="admin-result-modal-body" class="admin-result-body"></pre></div>'
      + '<div class="admin-result-modal-actions admin-actions-row" id="admin-result-modal-actions"></div>';
    document.body.appendChild(el);
    resultModalEl = el;
    return el;
  }

  function teardownResultModal() {
    if (resultModalEscHandler) {
      document.removeEventListener('keydown', resultModalEscHandler);
      resultModalEscHandler = null;
    }
    if (resultModalBackdrop && resultModalBackdrop.parentNode) {
      resultModalBackdrop.parentNode.removeChild(resultModalBackdrop);
    }
    resultModalBackdrop = null;
    if (resultModalEl) {
      resultModalEl.classList.add('hidden');
      resultModalEl.classList.remove('admin-editor-modal');
    }
    if (!document.querySelector('.admin-editor-modal')) {
      document.body.classList.remove('admin-modal-lock');
    }
    resultModalConfirmResolve = null;
  }

  window.AdminUi = {
    openResultModal: function (opts) {
      opts = opts || {};
      const el = ensureResultModal();
      teardownResultModal();
      const titleEl = document.getElementById('admin-result-modal-title');
      const bodyWrap = document.getElementById('admin-result-modal-body-wrap');
      const actions = document.getElementById('admin-result-modal-actions');
      titleEl.textContent = opts.title || (opts.isError ? '操作失败' : '操作结果');
      titleEl.className = opts.isError ? 'admin-result-title admin-result-title-err' : 'admin-result-title';
      if (opts.preformatted === false) {
        bodyWrap.innerHTML = '<div id="admin-result-modal-body" class="admin-result-body admin-result-html"></div>';
        document.getElementById('admin-result-modal-body').innerHTML = opts.body || '';
      } else {
        bodyWrap.innerHTML = '<pre id="admin-result-modal-body" class="admin-result-body"></pre>';
        document.getElementById('admin-result-modal-body').textContent = opts.body == null ? '' : String(opts.body);
      }
      actions.innerHTML = '<button type="button" class="ui-btn ui-btn-primary" id="admin-result-modal-close">关闭</button>';
      document.getElementById('admin-result-modal-close').onclick = () => this.closeResultModal();
      const backdrop = document.createElement('div');
      backdrop.className = 'admin-editor-backdrop';
      backdrop.onclick = () => this.closeResultModal();
      resultModalBackdrop = backdrop;
      resultModalEscHandler = (e) => {
        if (e.key === 'Escape') this.closeResultModal();
      };
      document.addEventListener('keydown', resultModalEscHandler);
      document.body.appendChild(backdrop);
      el.classList.remove('hidden');
      el.classList.add('admin-editor-modal');
      document.body.classList.add('admin-modal-lock');
    },
    closeResultModal: function () {
      teardownResultModal();
    },
    openConfirmModal: function (opts) {
      opts = opts || {};
      return new Promise((resolve) => {
        const el = ensureResultModal();
        teardownResultModal();
        resultModalConfirmResolve = resolve;
        const titleEl = document.getElementById('admin-result-modal-title');
        const bodyWrap = document.getElementById('admin-result-modal-body-wrap');
        const actions = document.getElementById('admin-result-modal-actions');
        titleEl.textContent = opts.title || '请确认';
        titleEl.className = 'admin-result-title';
        bodyWrap.innerHTML = '<pre id="admin-result-modal-body" class="admin-result-body"></pre>';
        document.getElementById('admin-result-modal-body').textContent = opts.body == null ? '' : String(opts.body);
        actions.innerHTML = '<button type="button" class="ui-btn ui-btn-primary" id="admin-result-modal-ok">'
          + (opts.okLabel || '确认') + '</button>'
          + '<button type="button" class="ui-btn ui-btn-secondary" id="admin-result-modal-cancel">'
          + (opts.cancelLabel || '取消') + '</button>';
        const finish = (ok) => {
          const cb = resultModalConfirmResolve;
          teardownResultModal();
          if (cb) cb(ok);
        };
        document.getElementById('admin-result-modal-ok').onclick = () => finish(true);
        document.getElementById('admin-result-modal-cancel').onclick = () => finish(false);
        const backdrop = document.createElement('div');
        backdrop.className = 'admin-editor-backdrop';
        backdrop.onclick = () => finish(false);
        resultModalBackdrop = backdrop;
        resultModalEscHandler = (e) => {
          if (e.key === 'Escape') finish(false);
        };
        document.addEventListener('keydown', resultModalEscHandler);
        document.body.appendChild(backdrop);
        el.classList.remove('hidden');
        el.classList.add('admin-editor-modal');
        document.body.classList.add('admin-modal-lock');
      });
    },
    openEditor: function (editorEl) {
      if (!editorEl) return;
      this.closeEditor(editorEl);
      const backdrop = document.createElement('div');
      backdrop.className = 'admin-editor-backdrop';
      const close = () => this.closeEditor(editorEl);
      backdrop.onclick = close;
      editorEl.__adminEditorBackdrop = backdrop;
      editorEl.__adminEditorEscHandler = (e) => {
        if (e.key === 'Escape') close();
      };
      document.addEventListener('keydown', editorEl.__adminEditorEscHandler);
      document.body.appendChild(backdrop);
      editorEl.classList.remove('hidden');
      editorEl.classList.add('admin-editor-modal');
      document.body.classList.add('admin-modal-lock');
    },
    closeEditor: function (editorEl) {
      if (!editorEl) return;
      if (editorEl.__adminEditorEscHandler) {
        document.removeEventListener('keydown', editorEl.__adminEditorEscHandler);
        editorEl.__adminEditorEscHandler = null;
      }
      if (editorEl.__adminEditorBackdrop && editorEl.__adminEditorBackdrop.parentNode) {
        editorEl.__adminEditorBackdrop.parentNode.removeChild(editorEl.__adminEditorBackdrop);
      }
      editorEl.__adminEditorBackdrop = null;
      editorEl.classList.add('hidden');
      editorEl.classList.remove('admin-editor-modal');
      if (!document.querySelector('.admin-editor-modal')) {
        document.body.classList.remove('admin-modal-lock');
      }
    }
  };

  function getIconForModule(routeHash, displayName) {
    if (window.Iconsax && Iconsax.renderModule) {
      return Iconsax.renderModule(routeHash, displayName);
    }
    return '<span class="sidebar-icon-fallback" aria-hidden="true">•</span>';
  }
  function showLogin() {
    document.getElementById('login-panel').classList.remove('hidden');
    document.getElementById('app').classList.add('hidden');
  }

  function showApp() {
    document.getElementById('login-panel').classList.add('hidden');
    document.getElementById('app').classList.remove('hidden');
  }

  async function loadManifest() {
    modules = await AdminApi.fetch('/api/v1/admin/modules');
    const nav = document.getElementById('sidebar-nav');
    nav.innerHTML = '';
    for (const m of modules) {
      if (!m.enabled) continue;
      if (!m.displayName || !String(m.displayName).trim()) continue;
      if (!m.uiRouteHash || !String(m.uiRouteHash).trim()) continue;
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.innerHTML = getIconForModule(m.uiRouteHash, m.displayName) + '<span>' + m.displayName + '</span>';
      btn.dataset.hash = m.uiRouteHash;
      btn.onclick = () => navigate(m.uiRouteHash);
      nav.appendChild(btn);
    }
  }

  async function ensureScript(m) {
    if (!m.scriptPath || scriptsLoaded[m.moduleId]) return;
    await new Promise((resolve, reject) => {
      const s = document.createElement('script');
      const sep = m.scriptPath.indexOf('?') >= 0 ? '&' : '?';
      s.src = m.scriptPath + sep + 'v=' + encodeURIComponent(STATIC_ASSET_VERSION);
      s.onload = resolve;
      s.onerror = () => reject(new Error('模块脚本加载失败: ' + m.scriptPath));
      document.body.appendChild(s);
    });
    scriptsLoaded[m.moduleId] = true;
  }

  async function navigate(hash) {
    const seq = ++navigateSeq;
    if (hash) {
      location.hash = hash;
    }
    const routePath = (location.hash || '#/presets').replace('#', '').split('?')[0];
    if (!routePath || routePath === '/') {
      const restore = sessionStorage.getItem(LAST_ROUTE_KEY) || '#/presets';
      if (restore && location.hash !== restore) {
        location.hash = restore;
      }
      return;
    }
    const fullHash = hash || location.hash || sessionStorage.getItem(LAST_ROUTE_KEY) || '#/presets';
    try {
      sessionStorage.setItem(LAST_ROUTE_KEY, fullHash);
    } catch (_) { /* ignore */ }
    const m = modules.find(x => x.uiRouteHash === '#' + routePath);
    const root = document.getElementById('module-root');
    if (window.SmMotion && SmMotion.showModuleSkeleton) {
      SmMotion.showModuleSkeleton(root);
    } else {
      root.innerHTML = '<div class="ui-card card admin-card"><p class="muted">加载中...</p></div>';
    }
    if (!m) {
      document.getElementById('route-title').textContent = '模块不可用';
      renderModuleError('当前模块不存在或未启用：' + routePath);
      return;
    }
    try {
      await ensureScript(m);
    } catch (e) {
      if (seq !== navigateSeq) return;
      document.getElementById('route-title').textContent = m.displayName;
      renderModuleError(e.message || '模块脚本加载失败');
      return;
    }
    if (seq !== navigateSeq) return;
    document.querySelectorAll('#sidebar-nav button').forEach(b => {
      b.classList.toggle('active', b.dataset.hash === m.uiRouteHash);
    });
    document.getElementById('route-title').textContent = m.displayName;
    root.innerHTML = '';
    const mod = AdminModules.registry[routePath];
    if (!mod || !mod.mount) {
      renderModuleError('模块脚本未注册：' + routePath);
      return;
    }
    try {
      await Promise.resolve(mod.mount(root));
      if (seq !== navigateSeq) return;
      if (window.SmMotion && SmMotion.initBlurFade) SmMotion.initBlurFade(root);
    } catch (e) {
      if (seq !== navigateSeq) return;
      renderModuleError('模块加载失败：' + (e && e.message ? e.message : 'unknown error'));
    }
  }

  document.getElementById('login-btn').onclick = async () => {
    const t = document.getElementById('token-input').value.trim();
    if (!t) return alert('请输入 Token');
    sessionStorage.setItem(TOKEN_KEY, t);
    try {
      await AdminApi.fetch('/api/v1/admin/modules');
      showApp();
      await loadManifest();
      navigate(location.hash || sessionStorage.getItem(LAST_ROUTE_KEY) || '#/presets');
    } catch (e) {
      sessionStorage.removeItem(TOKEN_KEY);
      alert(e.message);
    }
  };

  window.addEventListener('hashchange', () => navigate(location.hash));

  (function applyOAuthRedirect() {
    const params = new URLSearchParams(window.location.search);
    const oauthToken = params.get('oauth_token');
    if (oauthToken) {
      sessionStorage.setItem(TOKEN_KEY, oauthToken);
      window.history.replaceState({}, '', '/admin' + (window.location.hash || ''));
    }
  })();

  if (AdminApi.token()) {
    showApp();
    loadManifest().then(() => navigate(location.hash || sessionStorage.getItem(LAST_ROUTE_KEY) || '#/presets')).catch(showLogin);
  } else {
    showLogin();
  }
})();
