AdminModules.register({
  route: '/event-outbox',
  mount: async function (root) {
    const esc = s => (s == null ? '' : String(s)).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
    const fmtDt = s => s ? String(s).replace('T', ' ').slice(0, 19) : '-';
    const STATUSES = ['PENDING', 'RETRY', 'PUBLISHED', 'FAILED'];

    root.innerHTML = `
      <div class="panel">
        <div class="panel-head">
          <h2>事件投递</h2>
          <p>事务外盒（Outbox）事件表运维：查看卡住的事件、手动重投失败事件、清理已投递历史。</p>
        </div>
        <div class="toolbar" style="margin-bottom:0.75rem;">
          <button class="secondary" id="ob-reload">刷新</button>
          <button class="primary" id="ob-republish-failed">重投全部 FAILED</button>
          <button class="secondary" id="ob-clean">清理已投递</button>
          <span id="ob-msg" class="msg hidden"></span>
        </div>
        <div id="ob-stats" class="form-hint"></div>
      </div>

      <div class="panel">
        <h3>筛选</h3>
        <div class="toolbar" style="flex-wrap:wrap;gap:0.5rem;">
          <select id="ob-f-status">
            <option value="">全部状态</option>
            ${STATUSES.map(s => `<option value="${s}">${s}</option>`).join('')}
          </select>
          <input id="ob-f-agg-type" placeholder="aggregateType" style="min-width:10rem"/>
          <input id="ob-f-agg-id" placeholder="aggregateId" style="min-width:14rem"/>
          <input id="ob-f-event-type" placeholder="eventType" style="min-width:12rem"/>
          <button class="secondary" id="ob-search">查询</button>
        </div>
      </div>

      <div class="panel table-wrap">
        <h3>事件列表</h3>
        <table><thead><tr>
          <th>ID</th><th>类型</th><th>聚合</th><th>状态</th><th>重试</th><th>下次重试</th><th>错误</th><th>投递成功</th><th>创建</th><th>操作</th>
        </tr></thead><tbody id="ob-body"></tbody></table>
        <div class="toolbar" style="margin-top:0.5rem;">
          <button class="secondary" id="ob-prev">上一页</button>
          <span id="ob-page-info" class="form-hint"></span>
          <button class="secondary" id="ob-next">下一页</button>
        </div>
      </div>

      <div class="panel hidden" id="ob-detail-panel">
        <h3>事件详情</h3>
        <div id="ob-detail-meta" class="form-hint"></div>
        <pre id="ob-detail-payload" style="max-height:360px;overflow:auto;background:var(--ui-bg-muted,#f6f7f9);padding:0.75rem;border-radius:0.5rem;"></pre>
      </div>

      <div class="panel">
        <div class="toolbar" style="justify-content:space-between;margin-bottom:0.5rem;">
          <h3 style="margin:0;">幂等命令记录</h3>
          <div class="toolbar" style="gap:0.4rem;">
            <input id="pc-f-cmd-type" placeholder="commandType" style="min-width:10rem"/>
            <input id="pc-f-agg-type" placeholder="aggregateType" style="min-width:8rem"/>
            <input id="pc-f-agg-id" placeholder="aggregateId" style="min-width:10rem"/>
            <button class="secondary" id="pc-search">查询</button>
          </div>
        </div>
        <p class="form-hint">已处理命令的幂等去重记录，只读。用于排查重复执行问题时查看命令处理记录。</p>
        <table><thead><tr>
          <th>ID</th><th>commandKey</th><th>commandType</th><th>聚合</th><th>处理时间</th>
        </tr></thead><tbody id="pc-body"></tbody></table>
        <div class="toolbar" style="margin-top:0.5rem;">
          <button class="secondary" id="pc-prev">上一页</button>
          <span id="pc-page-info" class="form-hint"></span>
          <button class="secondary" id="pc-next">下一页</button>
        </div>
      </div>
    `;

    const showMsg = (text, isErr) => {
      const el = document.getElementById('ob-msg');
      el.classList.remove('hidden');
      el.className = isErr ? 'msg msg-err' : 'msg msg-ok';
      el.textContent = text;
    };

    let page = 1;
    const size = 20;
    let totalPages = 1;

    const loadStats = async () => {
      try {
        const s = await AdminApi.fetch('/api/v1/admin/event-outbox/stats');
        const bs = s.byStatus || {};
        document.getElementById('ob-stats').textContent =
          `总计 ${s.total || 0} · PENDING ${bs.PENDING || 0} · RETRY ${bs.RETRY || 0} · PUBLISHED ${bs.PUBLISHED || 0} · FAILED ${bs.FAILED || 0}`;
      } catch (e) { /* ignore */ }
    };

    const loadList = async () => {
      const params = new URLSearchParams();
      params.set('page', page);
      params.set('size', size);
      const status = document.getElementById('ob-f-status').value;
      const aggType = document.getElementById('ob-f-agg-type').value.trim();
      const aggId = document.getElementById('ob-f-agg-id').value.trim();
      const eventType = document.getElementById('ob-f-event-type').value.trim();
      if (status) params.set('status', status);
      if (aggType) params.set('aggregateType', aggType);
      if (aggId) params.set('aggregateId', aggId);
      if (eventType) params.set('eventType', eventType);
      const data = await AdminApi.fetch('/api/v1/admin/event-outbox?' + params.toString());
      const rows = data.records || [];
      totalPages = data.pages || 1;
      document.getElementById('ob-page-info').textContent = `第 ${page} / ${totalPages} 页，共 ${data.total || 0} 条`;
      document.getElementById('ob-body').innerHTML = rows.length ? rows.map(e => `
        <tr>
          <td>${e.id}</td>
          <td>${esc(e.eventType || '')}</td>
          <td>${esc(e.aggregateType || '')}:${esc(e.aggregateId || '')}</td>
          <td><span class="tag">${esc(e.status || '')}</span></td>
          <td>${e.retryCount || 0}</td>
          <td>${fmtDt(e.nextRetryAt)}</td>
          <td>${esc((e.errorMessage || '').slice(0, 60))}</td>
          <td>${fmtDt(e.publishedAt)}</td>
          <td>${fmtDt(e.createdAt)}</td>
          <td>
            <button type="button" class="secondary ob-view" data-id="${e.id}">详情</button>
            <button type="button" class="secondary ob-republish" data-id="${e.id}">重投</button>
          </td>
        </tr>
      `).join('') : '<tr><td colspan="10" class="form-hint">暂无事件</td></tr>';
      bindRowEvents();
    };

    const bindRowEvents = () => {
      document.querySelectorAll('button.ob-view').forEach(btn => {
        btn.onclick = async () => {
          try {
            const e = await AdminApi.fetch('/api/v1/admin/event-outbox/' + encodeURIComponent(btn.dataset.id));
            document.getElementById('ob-detail-panel').classList.remove('hidden');
            document.getElementById('ob-detail-meta').textContent =
              `#${e.id} ${e.eventType} · ${e.aggregateType}:${e.aggregateId} · status=${e.status} · retry=${e.retryCount || 0}` +
              (e.errorMessage ? ` · error=${e.errorMessage}` : '');
            let pretty = e.payloadJson || '(无)';
            try { pretty = JSON.stringify(JSON.parse(pretty), null, 2); } catch (_) { /* keep raw */ }
            document.getElementById('ob-detail-payload').textContent = pretty;
          } catch (err) {
            showMsg('加载详情失败: ' + err.message, true);
          }
        };
      });
      document.querySelectorAll('button.ob-republish').forEach(btn => {
        btn.onclick = async () => {
          try {
            await AdminApi.fetch('/api/v1/admin/event-outbox/' + encodeURIComponent(btn.dataset.id) + '/republish', { method: 'POST' });
            showMsg('已重置为 PENDING，等待投递', false);
            await loadStats();
            await loadList();
          } catch (err) {
            showMsg('重投失败: ' + err.message, true);
          }
        };
      });
    };

    document.getElementById('ob-reload').onclick = async () => { await loadStats(); await loadList(); };
    document.getElementById('ob-search').onclick = () => { page = 1; loadList(); };
    document.getElementById('ob-prev').onclick = () => { if (page > 1) { page--; loadList(); } };
    document.getElementById('ob-next').onclick = () => { if (page < totalPages) { page++; loadList(); } };
    document.getElementById('ob-republish-failed').onclick = async () => {
      if (!window.confirm('确认将所有 FAILED 状态的事件重置为 PENDING 重新投递？')) return;
      try {
        const res = await AdminApi.fetch('/api/v1/admin/event-outbox/republish-batch', {
          method: 'POST',
          body: JSON.stringify({ status: 'FAILED' })
        });
        showMsg('已重置 ' + (res.reset || 0) + ' 条 FAILED 事件', false);
        await loadStats();
        await loadList();
      } catch (err) {
        showMsg('批量重投失败: ' + err.message, true);
      }
    };
    document.getElementById('ob-clean').onclick = async () => {
      if (!window.confirm('确认清理 24 小时前已成功投递（PUBLISHED）的事件？此操作不可恢复。')) return;
      try {
        const res = await AdminApi.fetch('/api/v1/admin/event-outbox/clean-published', {
          method: 'POST',
          body: JSON.stringify({ retainHours: 24 })
        });
        showMsg('已清理 ' + (res.deleted || 0) + ' 条已投递事件', false);
        await loadStats();
        await loadList();
      } catch (err) {
        showMsg('清理失败: ' + err.message, true);
      }
    };

    let pcPage = 1;
    const pcSize = 20;
    let pcTotalPages = 1;

    const loadPcList = async () => {
      const params = new URLSearchParams();
      params.set('page', pcPage);
      params.set('size', pcSize);
      const cmdType = document.getElementById('pc-f-cmd-type').value.trim();
      const aggType = document.getElementById('pc-f-agg-type').value.trim();
      const aggId = document.getElementById('pc-f-agg-id').value.trim();
      if (cmdType) params.set('commandType', cmdType);
      if (aggType) params.set('aggregateType', aggType);
      if (aggId) params.set('aggregateId', aggId);
      try {
        const data = await AdminApi.fetch('/api/v1/admin/processed-commands?' + params.toString());
        const rows = data.records || [];
        pcTotalPages = data.pages || 1;
        document.getElementById('pc-page-info').textContent = `第 ${pcPage} / ${pcTotalPages} 页，共 ${data.total || 0} 条`;
        document.getElementById('pc-body').innerHTML = rows.length ? rows.map(c => `
          <tr>
            <td>${c.id}</td>
            <td title="${esc(c.commandKey)}">${esc(c.commandKey || '')}</td>
            <td>${esc(c.commandType || '')}</td>
            <td>${esc(c.aggregateType || '')}:${esc(c.aggregateId || '')}</td>
            <td>${fmtDt(c.processedAt)}</td>
          </tr>
        `).join('') : '<tr><td colspan="5" class="form-hint">暂无记录</td></tr>';
      } catch (e) { /* ignore */ }
    };

    document.getElementById('pc-search').onclick = () => { pcPage = 1; loadPcList(); };
    document.getElementById('pc-prev').onclick = () => { if (pcPage > 1) { pcPage--; loadPcList(); } };
    document.getElementById('pc-next').onclick = () => { if (pcPage < pcTotalPages) { pcPage++; loadPcList(); } };

    await loadStats();
    await loadList();
    await loadPcList();
  }
});
