(function () {
  const TOKEN_KEY = 'sm-admin-token';
  let modules = [];
  let scriptsLoaded = {};

  window.AdminApi = {
    token: () => sessionStorage.getItem(TOKEN_KEY) || '',
    fetch: async (path, opts = {}) => {
      const headers = Object.assign({ 'X-Admin-Token': AdminApi.token(), 'Content-Type': 'application/json' }, opts.headers || {});
      const res = await fetch(path, Object.assign({}, opts, { headers }));
      const json = await res.json();
      if (json.code !== 0) throw new Error(json.message || 'request failed');
      return json.data;
    }
  };

  window.AdminModules = {
    registry: {},
    register: function (def) { this.registry[def.route] = def; }
  };

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
    const sb = document.getElementById('sidebar');
    sb.innerHTML = '';
    for (const m of modules) {
      if (!m.enabled) continue;
      const btn = document.createElement('button');
      btn.textContent = m.displayName;
      btn.dataset.hash = m.uiRouteHash;
      btn.onclick = () => navigate(m.uiRouteHash);
      sb.appendChild(btn);
    }
  }

  async function ensureScript(m) {
    if (!m.scriptPath || scriptsLoaded[m.moduleId]) return;
    await new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.src = m.scriptPath;
      s.onload = resolve;
      s.onerror = reject;
      document.body.appendChild(s);
    });
    scriptsLoaded[m.moduleId] = true;
  }

  async function navigate(hash) {
    location.hash = hash || '#/presets';
    const route = (location.hash || '#/presets').replace('#', '');
    const m = modules.find(x => x.uiRouteHash === '#' + route || x.uiRouteHash === location.hash);
    if (!m) return;
    await ensureScript(m);
    document.querySelectorAll('#sidebar button').forEach(b => {
      b.classList.toggle('active', b.dataset.hash === m.uiRouteHash);
    });
    document.getElementById('route-title').textContent = m.displayName;
    const root = document.getElementById('module-root');
    root.innerHTML = '';
    const mod = AdminModules.registry[route] || AdminModules.registry[m.uiRouteHash.replace('#', '')];
    if (mod && mod.mount) mod.mount(root);
    else root.innerHTML = '<p class="msg">模块脚本未加载</p>';
  }

  document.getElementById('login-btn').onclick = async () => {
    const t = document.getElementById('token-input').value.trim();
    if (!t) return alert('请输入 Token');
    sessionStorage.setItem(TOKEN_KEY, t);
    try {
      await AdminApi.fetch('/api/v1/admin/modules');
      showApp();
      await loadManifest();
      navigate(location.hash || '#/presets');
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
    loadManifest().then(() => navigate(location.hash || '#/presets')).catch(showLogin);
  } else {
    showLogin();
  }
})();
