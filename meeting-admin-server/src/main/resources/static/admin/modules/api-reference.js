AdminModules.register({
  route: '/api-reference',
  mount: async function (root) {
    const [meta, catalog] = await Promise.all([
      AdminApi.fetch('/api/v1/admin/api-reference/meta'),
      AdminApi.fetch('/api/v1/admin/api-reference/catalog')
    ]);

    const categories = [...new Set(catalog.map(e => e.category))];
    let filterCat = '';
    let filterQ = '';

    const methodClass = m => 'api-method api-method-' + (m || 'GET').toLowerCase();

    const copyText = async (text, btn) => {
      try {
        await navigator.clipboard.writeText(text);
        const o = btn.textContent;
        btn.textContent = '已复制';
        setTimeout(() => { btn.textContent = o; }, 1200);
      } catch {
        alert('复制失败');
      }
    };

    const filtered = () => catalog.filter(e => {
      if (filterCat && e.category !== filterCat) return false;
      if (!filterQ) return true;
      const q = filterQ.toLowerCase();
      return (e.title + e.path + e.description + e.method).toLowerCase().includes(q);
    });

    const renderList = () => {
      const list = filtered();
      const host = root.querySelector('#api-ref-list');
      if (!host) return;
      if (list.length === 0) {
        host.innerHTML = '<p class="muted">无匹配接口</p>';
        return;
      }
      host.innerHTML = list.map(e => {
        const notes = (e.notes || []).map(n => `<li>${n}</li>`).join('');
        const auth = (e.authHeaders || []).map(h => `<code>${h}</code>`).join(' ');
        const reqBlock = e.requestExample
          ? `<div class="api-ref-block"><div class="api-ref-block-head">请求体示例 <button type="button" class="secondary btn-copy-req" data-id="${e.id}">复制</button></div><pre class="api-ref-code">${esc(e.requestExample)}</pre></div>`
          : '<p class="muted api-ref-na">无请求体</p>';
        const resBlock = e.responseExample
          ? `<div class="api-ref-block"><div class="api-ref-block-head">响应示例</div><pre class="api-ref-code">${esc(e.responseExample)}</pre></div>`
          : '';
        const curlBlock = e.curlExample
          ? `<div class="api-ref-block"><div class="api-ref-block-head">cURL <button type="button" class="secondary btn-copy-curl" data-id="${e.id}">复制</button></div><pre class="api-ref-code api-ref-curl">${esc(e.curlExample)}</pre></div>`
          : '';
        return `<article class="api-ref-card" data-id="${e.id}">
          <header class="api-ref-card-head" data-toggle="${e.id}">
            <span class="${methodClass(e.method)}">${e.method}</span>
            <code class="api-ref-path">${esc(e.path)}</code>
            <span class="api-ref-title">${esc(e.title)}</span>
            <span class="api-ref-chevron">▼</span>
          </header>
          <div class="api-ref-body hidden" id="body-${e.id}">
            <p class="api-ref-desc">${esc(e.description)}</p>
            <p class="api-ref-meta"><strong>进程</strong> ${esc(e.targetProcess)}</p>
            <p class="api-ref-meta"><strong>鉴权</strong> ${auth || '—'}</p>
            ${notes ? `<ul class="api-ref-notes">${notes}</ul>` : ''}
            ${reqBlock}
            ${resBlock}
            ${curlBlock}
          </div>
        </article>`;
      }).join('');

      host.querySelectorAll('.api-ref-card-head').forEach(h => {
        h.onclick = () => {
          const id = h.dataset.toggle;
          const body = document.getElementById('body-' + id);
          const open = body.classList.toggle('hidden');
          h.querySelector('.api-ref-chevron').textContent = open ? '▼' : '▲';
        };
      });
      host.querySelectorAll('.btn-copy-curl').forEach(btn => {
        const e = catalog.find(x => x.id === btn.dataset.id);
        btn.onclick = ev => { ev.stopPropagation(); copyText(e.curlExample, btn); };
      });
      host.querySelectorAll('.btn-copy-req').forEach(btn => {
        const e = catalog.find(x => x.id === btn.dataset.id);
        btn.onclick = ev => { ev.stopPropagation(); copyText(e.requestExample, btn); };
      });
    };

    const esc = s => (s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

    const catOptions = categories.map(c =>
      `<option value="${esc(c)}">${esc(c)}</option>`).join('');

    root.innerHTML = `
      <div class="panel">
        <div class="panel-head">
          <h2>数据更新 API 参考</h2>
          <p>列出会写入数据库或触发运行时刷新的管理端 / internal 接口；统一响应包装 ${esc(meta.responseWrapper || '')}</p>
        </div>
        <div class="api-ref-meta-bar">
          <span>共 <strong>${meta.total}</strong> 个接口</span>
          <span>Admin <code>${esc(meta.adminBaseUrl)}</code></span>
          <span>meeting-server <code>${esc(meta.meetingServerBaseUrl)}</code></span>
        </div>
        <p class="muted">${esc(meta.authHint)}</p>
        <div class="toolbar">
          <label class="field-inline">分类
            <select id="api-ref-cat"><option value="">全部</option>${catOptions}</select>
          </label>
          <label class="field-inline">搜索
            <input id="api-ref-q" type="search" placeholder="路径、标题、说明…" style="min-width:14rem"/>
          </label>
          <button type="button" class="secondary" id="api-ref-expand">展开全部</button>
          <button type="button" class="secondary" id="api-ref-collapse">收起全部</button>
        </div>
      </div>
      <div id="api-ref-list" class="api-ref-list"></div>`;

    root.querySelector('#api-ref-cat').onchange = e => {
      filterCat = e.target.value;
      renderList();
    };
    root.querySelector('#api-ref-q').oninput = e => {
      filterQ = e.target.value.trim();
      renderList();
    };
    root.querySelector('#api-ref-expand').onclick = () => {
      root.querySelectorAll('.api-ref-body').forEach(b => b.classList.remove('hidden'));
      root.querySelectorAll('.api-ref-chevron').forEach(c => { c.textContent = '▲'; });
    };
    root.querySelector('#api-ref-collapse').onclick = () => {
      root.querySelectorAll('.api-ref-body').forEach(b => b.classList.add('hidden'));
      root.querySelectorAll('.api-ref-chevron').forEach(c => { c.textContent = '▼'; });
    };

    renderList();
    // 默认展开第一条
    const first = root.querySelector('.api-ref-card-head');
    if (first) first.click();
  }
});
