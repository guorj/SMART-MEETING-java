AdminModules.register({
  route: '/users',
  mount: async function (root) {
    let pageNum = 1;
    const pageSize = 20;
    let keyword = '';
    let expiryFilter = 'ALL';

    root.innerHTML = `
      <div class="panel">
        <p class="module-intro">${AdminHints.users.moduleIntro}</p>
        <span id="usr-msg" class="msg hidden"></span>
      </div>
      <div id="usr-main"></div>`;

    const showMsg = (text, isErr) => {
      const el = document.getElementById('usr-msg');
      el.classList.remove('hidden');
      el.className = isErr ? 'msg msg-err' : 'msg msg-ok';
      el.textContent = text;
    };

    const esc = s => (s == null ? '' : String(s)).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
    const fmtDt = s => s ? String(s).replace('T', ' ').slice(0, 19) : '-';
    const cellLong = (v, max) => {
      const t = v == null ? '' : String(v);
      if (!t) return '-';
      const short = t.length > (max || 14) ? t.slice(0, max || 14) + '…' : t;
      return '<span title="' + esc(t) + '">' + esc(short) + '</span>';
    };
    const conciseErr = (e) => {
      const msg = String(e && e.message ? e.message : e || '');
      if (msg.includes('HTTP 404')) return '接口不存在（404），请确认 meeting-server internal 路径与版本已对齐';
      if (msg.includes('HTTP 401') || msg.includes('HTTP 403')) return '鉴权失败，请检查 INTERNAL_RELOAD_TOKEN 与 bridge 配置';
      return msg;
    };
    const arrayBufferToBase64 = buffer => {
      const bytes = new Uint8Array(buffer);
      const chunkSize = 0x8000;
      let binary = '';
      for (let i = 0; i < bytes.length; i += chunkSize) {
        const chunk = bytes.subarray(i, i + chunkSize);
        binary += String.fromCharCode.apply(null, chunk);
      }
      return btoa(binary);
    };

    const parseUserId = raw => {
      const s = String(raw == null ? '' : raw).trim();
      if (!s || !/^-?\d+$/.test(s)) return null;
      const n = Number(s);
      return Number.isSafeInteger(n) && n > 0 ? n : null;
    };

    const expiryBadge = st => {
      if (st === 'EXPIRED') return '<span class="msg msg-err">已过期</span>';
      if (st === 'EXPIRING') return '<span class="msg">即将过期</span>';
      if (st === 'VALID') return '<span class="msg msg-ok">有效</span>';
      if (!st) return '<span class="muted">无声纹</span>';
      return esc(st);
    };

    const grantBadge = r => {
      if (!r.feishuUserId) return '<span class="muted">无飞书ID</span>';
      if (!r.dashboardGrantEnabled) return '<span class="msg msg-err">未授权</span>';
      if (r.dashboardCanCreateMeeting) return '<span class="msg msg-ok">已授权·可建会</span>';
      return '<span class="msg msg-ok">已授权</span>';
    };

    let grantPolicy = { defaultDeny: true };

    async function loadGrantPolicy() {
      grantPolicy = await AdminApi.fetch('/api/v1/admin/users/grant-policy');
    }

    async function quickGrant(userId, preset) {
      if (!userId) return;
      const label = preset === 'FULL' ? '完整授权（含建会）' : '前台+声纹授权';
      if (!confirm('确认为用户 #' + userId + ' 应用「' + label + '」？')) return;
      try {
        await AdminApi.fetch('/api/v1/admin/users/' + userId + '/dashboard-grant/quick?preset=' + preset, {
          method: 'POST'
        });
        showMsg('用户 #' + userId + ' ' + label + ' 已生效', false);
        loadList();
      } catch (e) {
        showMsg(e.message, true);
      }
    }

    async function saveGrantPolicy() {
      const cb = document.getElementById('usr-default-deny');
      if (!cb) return;
      grantPolicy = await AdminApi.fetch('/api/v1/admin/users/grant-policy', {
        method: 'PUT',
        body: JSON.stringify({ defaultDeny: cb.checked })
      });
      showMsg('前台授权策略已保存', false);
    }

    function toLocalInput(iso) {
      if (!iso) return '';
      const d = new Date(iso);
      if (Number.isNaN(d.getTime())) return '';
      const pad = n => String(n).padStart(2, '0');
      return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
        + 'T' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    }

    function fromLocalInput(val) {
      if (!val) return null;
      const s = val.trim();
      if (!s) return null;
      if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(s)) return s + ':00';
      if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(s)) return s;
      return null;
    }

    function renderPager(containerId, page, onPage) {
      const el = document.getElementById(containerId);
      const total = page.total || 0;
      const current = page.current || 1;
      const pages = Math.max(1, Math.ceil(total / (page.size || pageSize)));
      el.innerHTML = '';
      if (pages <= 1) return;
      const prev = document.createElement('button');
      prev.type = 'button';
      prev.textContent = '上一页';
      prev.disabled = current <= 1;
      prev.onclick = () => onPage(current - 1);
      const next = document.createElement('button');
      next.type = 'button';
      next.textContent = '下一页';
      next.disabled = current >= pages;
      next.onclick = () => onPage(current + 1);
      const info = document.createElement('span');
      info.className = 'muted';
      info.textContent = ' 第 ' + current + ' / ' + pages + ' 页 ';
      el.appendChild(prev);
      el.appendChild(info);
      el.appendChild(next);
    }

    async function loadList() {
      const panel = document.getElementById('usr-main');
      panel.innerHTML = '<div class="panel"><p class="muted">加载中…</p></div>';
      try {
        const qs = new URLSearchParams({ page: pageNum, size: pageSize, expiryFilter: expiryFilter });
        if (keyword) qs.set('keyword', keyword);
        const page = await AdminApi.fetch('/api/v1/admin/users?' + qs);
        const rows = page.records || [];

        panel.innerHTML = `
          <div class="panel">
            <div class="panel-head"><h2>前台授权策略</h2></div>
            <div class="form-check-row">
              <label class="check-label">
                <input type="checkbox" id="usr-default-deny" ${grantPolicy.defaultDeny ? 'checked' : ''} />
                默认拒绝未授权用户
              </label>
              <p class="form-hint" style="margin: 0;">${AdminHints.dashboardGrants.defaultDeny}</p>
            </div>
            <button type="button" class="secondary" id="usr-save-policy">保存策略</button>
          </div>
          <div class="panel toolbar">
            <button type="button" class="primary" id="usr-new">新建用户</button>
            <input id="usr-kw" type="search" placeholder="姓名 / 飞书 ID / featureId / OA userId" value="${esc(keyword)}"/>
            <select id="usr-expiry" title="${esc(AdminHints.users.expiryFilter)}">
              <option value="ALL">声纹：全部</option>
              <option value="VALID">声纹：有效</option>
              <option value="EXPIRING">声纹：48h 内过期</option>
              <option value="EXPIRED">声纹：已过期</option>
            </select>
            <button type="button" id="usr-search">搜索</button>
            <span class="muted">共 ${page.total || 0} 人</span>
          </div>
          <div class="panel table-wrap"><table class="table-wide"><thead><tr>
            <th>OA userId</th><th>姓名</th><th>feishu_user_id</th><th>前台授权</th>
            <th>声纹</th><th>featureId</th><th>groupId</th><th>注册</th><th>过期</th><th>操作</th>
          </tr></thead><tbody id="usr-tbody"></tbody></table></div>
          <div class="panel toolbar" id="usr-pager"></div>
          <div class="panel hidden form-editor" id="usr-editor"></div>
          <div class="panel" id="usr-vp-ops"></div>`;

        document.getElementById('usr-expiry').value = expiryFilter;
        const tbody = document.getElementById('usr-tbody');
        rows.forEach(r => {
          const tr = document.createElement('tr');
          const feat = r.voiceprintCount > 1
            ? esc(r.featureId) + ' <span class="muted">(+' + (r.voiceprintCount - 1) + ')</span>'
            : esc(r.featureId || '-');
          tr.innerHTML = '<td>' + esc(r.userId) + '</td><td>' + esc(r.userName) + '</td>'
            + '<td>' + cellLong(r.feishuUserId, 12) + '</td>'
            + '<td>' + grantBadge(r) + '</td>'
            + '<td>' + expiryBadge(r.hasVoiceprint ? r.expiryStatus : null) + '</td>'
            + '<td>' + feat + '</td>'
            + '<td>' + esc(r.groupId || '-') + '</td>'
            + '<td>' + fmtDt(r.registeredAt) + '</td>'
            + '<td>' + fmtDt(r.expiresAt) + '</td>'
            + '<td><a href="#" class="usr-edit" data-id="' + r.userId + '">编辑</a> '
            + (r.feishuUserId
              ? '<a href="#" class="usr-grant-quick" data-id="' + r.userId + '" data-preset="BASIC">一键授权</a> '
                + (r.dashboardGrantEnabled && !r.dashboardCanCreateMeeting
                  ? '<a href="#" class="usr-grant-quick" data-id="' + r.userId + '" data-preset="FULL">补建会权</a> '
                  : '<a href="#" class="usr-grant-quick" data-id="' + r.userId + '" data-preset="FULL">含建会</a> ')
              : '<span class="muted" title="须先填写 feishu_user_id">一键授权</span> ')
            + '<a href="#" class="usr-del" data-id="' + r.userId + '">删除</a></td>';
          tbody.appendChild(tr);
        });

        renderPager('usr-pager', page, p => { pageNum = p; loadList(); });
        document.getElementById('usr-kw').onkeydown = e => {
          if (e.key === 'Enter') document.getElementById('usr-search').click();
        };
        document.getElementById('usr-search').onclick = () => {
          keyword = document.getElementById('usr-kw').value.trim();
          expiryFilter = document.getElementById('usr-expiry').value;
          pageNum = 1;
          loadList();
        };
        document.getElementById('usr-new').onclick = () => showEditor(null);
        document.getElementById('usr-save-policy').onclick = () => saveGrantPolicy().catch(e => showMsg(e.message, true));
        tbody.querySelectorAll('a.usr-grant-quick').forEach(a => {
          a.onclick = e => {
            e.preventDefault();
            quickGrant(a.dataset.id, a.dataset.preset || 'BASIC');
          };
        });
        tbody.querySelectorAll('a.usr-edit').forEach(a => {
          a.onclick = async e => {
            e.preventDefault();
            try {
              const detail = await AdminApi.fetch('/api/v1/admin/users/' + a.dataset.id);
              showEditor(detail);
            } catch (err) {
              showMsg(err.message, true);
            }
          };
        });
        tbody.querySelectorAll('a.usr-del').forEach(a => {
          a.onclick = async e => {
            e.preventDefault();
            if (!confirm('删除用户 #' + a.dataset.id + '？将同时删除其飞书映射与全部声纹记录。')) return;
            try {
              await AdminApi.fetch('/api/v1/admin/users/' + a.dataset.id, { method: 'DELETE' });
              showMsg('已删除用户 #' + a.dataset.id, false);
              loadList();
            } catch (err) {
              showMsg(err.message, true);
            }
          };
        });
        renderVoiceprintOps();
      } catch (err) {
        panel.innerHTML = '<div class="panel"><p class="msg msg-err">加载失败: ' + esc(err.message) + '</p></div>';
        showMsg(err.message, true);
      }
    }

    function bindAudioFileToTextarea(fileInputId, textareaId) {
      const fi = document.getElementById(fileInputId);
      const ta = document.getElementById(textareaId);
      if (!fi || !ta) return;
      fi.onchange = async () => {
        const f = fi.files && fi.files[0];
        if (!f) return;
        try {
          const ab = await f.arrayBuffer();
          ta.value = arrayBufferToBase64(ab);
          showMsg('已加载音频文件: ' + f.name + ' (' + Math.round(f.size / 1024) + 'KB)', false);
        } catch (e) {
          showMsg('音频转 base64 失败: ' + e.message, true);
        }
      };
    }

    function renderScoreRows(rows) {
      if (!rows || !rows.length) return '<tr><td colspan="6" class="muted">无结果</td></tr>';
      return rows.map(r => '<tr>'
        + '<td>' + cellLong(r.featureId, 16) + '</td>'
        + '<td>' + esc(r.score == null ? '-' : Number(r.score).toFixed(4)) + '</td>'
        + '<td>' + esc(r.userId == null ? '-' : r.userId) + '</td>'
        + '<td>' + esc(r.userName || '-') + '</td>'
        + '<td>' + cellLong(r.feishuUserId, 12) + '</td>'
        + '<td>' + cellLong(r.featureInfo, 24) + '</td>'
        + '</tr>').join('');
    }

    function renderFeatureRows(rows) {
      if (!rows || !rows.length) return '<tr><td colspan="8" class="muted">无特征</td></tr>';
      return rows.map(r => '<tr>'
        + '<td>' + cellLong(r.featureId, 16) + '</td>'
        + '<td>' + esc(r.userId == null ? '-' : r.userId) + '</td>'
        + '<td>' + esc(r.userName || '-') + '</td>'
        + '<td>' + cellLong(r.feishuUserId, 12) + '</td>'
        + '<td>' + cellLong(r.featureInfo, 24) + '</td>'
        + '<td>' + fmtDt(r.registeredAt) + '</td>'
        + '<td>' + fmtDt(r.expiresAt) + '</td>'
        + '<td><button type="button" class="usr-vp-del" data-feature-id="' + esc(r.featureId) + '">删除</button></td>'
        + '</tr>').join('');
    }

    function renderVoiceprintOps() {
      const root = document.getElementById('usr-vp-ops');
      root.innerHTML = `
        <h3>声纹运维（P0/P1/P2）</h3>
        <p class="form-hint">通过 admin 接口桥接 meeting-server internal 声纹能力：查询特征、更新/删除、1:N 检索、1:1 比对、特征库管理。</p>
        <div class="toolbar">
          <input id="vp-group-id" placeholder="groupId（留空使用默认）" />
          <button type="button" class="primary" id="vp-refresh-features">查询特征列表</button>
          <button type="button" id="vp-refresh-all-features">按 group_id 查询全部（本地）</button>
          <button type="button" id="vp-open-group-center">特征库管理中心</button>
          <span class="muted" id="vp-feature-count"></span>
        </div>
        <div class="table-wrap"><table class="table-wide"><thead><tr>
          <th>featureId</th><th>OA userId</th><th>姓名</th><th>feishu_user_id</th>
          <th>featureInfo</th><th>注册时间</th><th>过期时间</th><th>操作</th>
        </tr></thead><tbody id="vp-feature-tbody"><tr><td colspan="8" class="muted">点击“查询特征列表”加载</td></tr></tbody></table></div>

        <details id="vp-adv-box">
          <summary>展开高级运维（更新/检索）</summary>

          <h4>更新特征（P0）</h4>
          <div class="toolbar">
            <input id="vp-upd-feature-id" placeholder="featureId（必填）"/>
            <input id="vp-upd-feature-info" placeholder="featureInfo（可选）"/>
            <label><input type="checkbox" id="vp-upd-cover" checked/> 覆盖更新</label>
          </div>
          <div class="toolbar">
            <input id="vp-upd-audio-file" type="file" accept="audio/*"/>
            <button type="button" id="vp-upd-submit">提交更新</button>
          </div>
          <textarea id="vp-upd-audio-b64" rows="4" placeholder="audioBase64（可手填，或通过文件自动填充）"></textarea>

          <h4>1:N 检索（P1）</h4>
          <div class="toolbar">
            <input id="vp-1n-topk" type="number" min="1" max="10" value="3" style="width:120px" />
            <input id="vp-1n-audio-file" type="file" accept="audio/*"/>
            <button type="button" id="vp-1n-submit">执行 1:N</button>
          </div>
          <textarea id="vp-1n-audio-b64" rows="4" placeholder="audioBase64（可手填，或通过文件自动填充）"></textarea>
          <div class="table-wrap"><table class="table-wide"><thead><tr>
            <th>featureId</th><th>score</th><th>OA userId</th><th>姓名</th><th>feishu_user_id</th><th>featureInfo</th>
          </tr></thead><tbody id="vp-1n-tbody"><tr><td colspan="6" class="muted">暂无结果</td></tr></tbody></table></div>

          <h4>1:1 比对（P2）</h4>
          <div class="toolbar">
            <input id="vp-1v1-feature-id" placeholder="目标 featureId（必填）"/>
            <input id="vp-1v1-audio-file" type="file" accept="audio/*"/>
            <button type="button" id="vp-1v1-submit">执行 1:1</button>
          </div>
          <textarea id="vp-1v1-audio-b64" rows="4" placeholder="audioBase64（可手填，或通过文件自动填充）"></textarea>
          <div class="table-wrap"><table class="table-wide"><thead><tr>
            <th>featureId</th><th>score</th><th>OA userId</th><th>姓名</th><th>feishu_user_id</th><th>featureInfo</th>
          </tr></thead><tbody id="vp-1v1-tbody"><tr><td colspan="6" class="muted">暂无结果</td></tr></tbody></table></div>
        </details>

        <div class="panel hidden form-editor" id="vp-group-center">
          <h3>特征库管理中心</h3>
          <p class="form-hint">统一入口：左侧选择 groupId，右侧查看远端/本地特征并执行管理动作。</p>
          <div class="vp-center-layout">
            <div class="vp-center-left">
              <div class="toolbar">
                <button type="button" id="vp-center-refresh-groups">刷新列表</button>
              </div>
              <div id="vp-center-group-list" class="vp-center-group-list"></div>
            </div>
            <div class="vp-center-right">
              <div class="toolbar">
                <input id="vp-center-group-id" placeholder="groupId（可手动补充）"/>
                <button type="button" class="primary" id="vp-center-use-group">回填到主查询</button>
                <button type="button" id="vp-center-load-remote">远端特征</button>
                <button type="button" id="vp-center-load-local">本地特征</button>
              </div>
              <pre id="vp-center-result" class="msg"></pre>
            </div>
          </div>
          <div class="toolbar">
            <input id="vp-new-group-id" placeholder="新 groupId"/>
            <input id="vp-new-group-name" placeholder="groupName"/>
            <input id="vp-new-group-info" placeholder="groupInfo"/>
            <button type="button" id="vp-group-create">创建特征库</button>
          </div>
          <div class="toolbar">
            <input id="vp-del-group-id" placeholder="删除 groupId"/>
            <button type="button" class="danger" id="vp-group-delete">删除特征库</button>
            <button type="button" id="vp-center-close">关闭</button>
          </div>
        </div>
      `;

      bindAudioFileToTextarea('vp-upd-audio-file', 'vp-upd-audio-b64');
      bindAudioFileToTextarea('vp-1n-audio-file', 'vp-1n-audio-b64');
      bindAudioFileToTextarea('vp-1v1-audio-file', 'vp-1v1-audio-b64');

      const withBusy = async (btnId, job) => {
        const btn = document.getElementById(btnId);
        const old = btn ? btn.textContent : '';
        if (btn) {
          btn.disabled = true;
          btn.textContent = '处理中...';
        }
        try {
          return await job();
        } finally {
          if (btn) {
            btn.disabled = false;
            btn.textContent = old;
          }
        }
      };

      const fetchFeaturesAllWithFallback = async (groupId) => {
        const qs = groupId ? ('?groupId=' + encodeURIComponent(groupId)) : '';
        try {
          return await AdminApi.fetch('/api/v1/admin/voiceprints/features/all' + qs);
        } catch (err) {
          return await AdminApi.fetch('/api/v1/admin/voiceprints/features' + qs);
        }
      };

      const loadCenterGroupList = async () => {
        const list = document.getElementById('vp-center-group-list');
        list.innerHTML = '<div class="muted">加载中...</div>';
        try {
          const rows = await fetchFeaturesAllWithFallback('');
          const groups = Array.from(new Set((rows || [])
            .map(r => (r.groupId || '').trim())
            .filter(Boolean)))
            .sort((a, b) => a.localeCompare(b, 'zh-CN'));
          if (!groups.length) {
            list.innerHTML = '<div class="muted">暂无 groupId 数据</div>';
            return;
          }
          list.innerHTML = groups.map(g => '<button type="button" class="secondary vp-center-group-item" data-group-id="' + esc(g) + '">' + esc(g) + '</button>').join('');
          list.querySelectorAll('.vp-center-group-item').forEach(btn => {
            btn.onclick = () => {
              const gid = btn.dataset.groupId || '';
              document.getElementById('vp-center-group-id').value = gid;
              document.getElementById('vp-del-group-id').value = gid;
              list.querySelectorAll('.vp-center-group-item').forEach(x => x.classList.remove('primary'));
              btn.classList.add('primary');
              document.getElementById('vp-center-load-local').click();
            };
          });
        } catch (e) {
          list.innerHTML = '<div class="msg msg-err">加载 group 列表失败: ' + esc(conciseErr(e)) + '</div>';
        }
      };

      const refreshFeatures = async () => {
        try {
          const gid = document.getElementById('vp-group-id').value.trim();
          const qs = gid ? ('?groupId=' + encodeURIComponent(gid)) : '';
          const rows = await AdminApi.fetch('/api/v1/admin/voiceprints/features' + qs);
          document.getElementById('vp-feature-count').textContent = '共 ' + (rows ? rows.length : 0) + ' 条';
          const tb = document.getElementById('vp-feature-tbody');
          tb.innerHTML = renderFeatureRows(rows || []);
          tb.querySelectorAll('button.usr-vp-del').forEach(btn => {
            btn.onclick = async () => {
              const fid = btn.dataset.featureId;
              if (!confirm('确认删除 featureId=' + fid + ' ?')) return;
              try {
                await AdminApi.fetch('/api/v1/admin/voiceprints/features/' + encodeURIComponent(fid), { method: 'DELETE' });
                showMsg('已删除特征: ' + fid, false);
                refreshFeatures();
              } catch (e) {
                showMsg(e.message, true);
              }
            };
          });
        } catch (e) {
          showMsg('查询特征失败: ' + e.message, true);
        }
      };
      document.getElementById('vp-refresh-features').onclick = () => withBusy('vp-refresh-features', refreshFeatures);

      const refreshAllFeaturesByGroupId = async () => {
        try {
          const gid = document.getElementById('vp-group-id').value.trim();
          const qs = gid ? ('?groupId=' + encodeURIComponent(gid)) : '';
          const rows = await fetchFeaturesAllWithFallback(gid);
          document.getElementById('vp-feature-count').textContent = '本地共 ' + (rows ? rows.length : 0) + ' 条';
          const tb = document.getElementById('vp-feature-tbody');
          tb.innerHTML = renderFeatureRows(rows || []);
          tb.querySelectorAll('button.usr-vp-del').forEach(btn => {
            btn.onclick = async () => {
              const fid = btn.dataset.featureId;
              if (!confirm('确认删除 featureId=' + fid + ' ?')) return;
              try {
                await AdminApi.fetch('/api/v1/admin/voiceprints/features/' + encodeURIComponent(fid), { method: 'DELETE' });
                showMsg('已删除特征: ' + fid, false);
                refreshAllFeaturesByGroupId();
              } catch (e) {
                showMsg(e.message, true);
              }
            };
          });
        } catch (e) {
          showMsg('按 group_id 查询全部声纹失败: ' + conciseErr(e), true);
        }
      };
      document.getElementById('vp-refresh-all-features').onclick = () => withBusy('vp-refresh-all-features', refreshAllFeaturesByGroupId);

      document.getElementById('vp-open-group-center').onclick = () => {
        const center = document.getElementById('vp-group-center');
        const gid = document.getElementById('vp-group-id').value.trim();
        document.getElementById('vp-center-group-id').value = gid;
        document.getElementById('vp-del-group-id').value = gid;
        document.getElementById('vp-center-result').textContent = '';
        AdminUi.openEditor(center);
        loadCenterGroupList();
      };
      document.getElementById('vp-center-refresh-groups').onclick = () => withBusy('vp-center-refresh-groups', loadCenterGroupList);
      document.getElementById('vp-center-close').onclick = () => AdminUi.closeEditor(document.getElementById('vp-group-center'));
      document.getElementById('vp-center-use-group').onclick = () => {
        const gid = document.getElementById('vp-center-group-id').value.trim();
        document.getElementById('vp-group-id').value = gid;
        if (gid) document.getElementById('vp-del-group-id').value = gid;
        showMsg('已回填 groupId 到主查询区', false);
      };
      document.getElementById('vp-center-load-remote').onclick = async () => withBusy('vp-center-load-remote', async () => {
        const gid = document.getElementById('vp-center-group-id').value.trim();
        const qs = gid ? ('?groupId=' + encodeURIComponent(gid)) : '';
        const rows = await AdminApi.fetch('/api/v1/admin/voiceprints/features' + qs);
        document.getElementById('vp-center-result').textContent =
          '远端特征：' + (rows ? rows.length : 0) + ' 条\n'
          + (rows && rows.length ? rows.slice(0, 30).map(r => (r.featureId || '-') + ' | ' + (r.userName || '-') + ' | ' + (r.groupId || gid || '-')).join('\n') : '(无)');
      });
      document.getElementById('vp-center-load-local').onclick = async () => withBusy('vp-center-load-local', async () => {
        const gid = document.getElementById('vp-center-group-id').value.trim();
        const qs = gid ? ('?groupId=' + encodeURIComponent(gid)) : '';
        const rows = await fetchFeaturesAllWithFallback(gid);
        document.getElementById('vp-center-result').textContent =
          '本地特征：' + (rows ? rows.length : 0) + ' 条\n'
          + (rows && rows.length ? rows.slice(0, 30).map(r => (r.featureId || '-') + ' | ' + (r.userName || '-') + ' | ' + (r.groupId || gid || '-')).join('\n') : '(无)');
      });

      document.getElementById('vp-upd-submit').onclick = () => withBusy('vp-upd-submit', async () => {
        const gid = document.getElementById('vp-group-id').value.trim() || null;
        const fid = document.getElementById('vp-upd-feature-id').value.trim();
        const finfo = document.getElementById('vp-upd-feature-info').value.trim() || null;
        const audio = document.getElementById('vp-upd-audio-b64').value.trim();
        const cover = document.getElementById('vp-upd-cover').checked;
        if (!fid || !audio) return showMsg('更新特征需要 featureId 和 audioBase64', true);
        try {
          await AdminApi.fetch('/api/v1/admin/voiceprints/features/' + encodeURIComponent(fid) + '/update', {
            method: 'POST',
            body: JSON.stringify({ groupId: gid, featureInfo: finfo, audioBase64: audio, cover: cover })
          });
          showMsg('特征更新已提交: ' + fid, false);
          refreshFeatures();
        } catch (e) {
          showMsg('更新特征失败: ' + e.message, true);
        }
      });

      document.getElementById('vp-1n-submit').onclick = () => withBusy('vp-1n-submit', async () => {
        const gid = document.getElementById('vp-group-id').value.trim() || null;
        const topK = Number(document.getElementById('vp-1n-topk').value || '3');
        const audio = document.getElementById('vp-1n-audio-b64').value.trim();
        if (!audio) return showMsg('1:N 需要 audioBase64', true);
        try {
          const rows = await AdminApi.fetch('/api/v1/admin/voiceprints/search/1n', {
            method: 'POST',
            body: JSON.stringify({ groupId: gid, topK: topK, audioBase64: audio })
          });
          document.getElementById('vp-1n-tbody').innerHTML = renderScoreRows(rows || []);
          showMsg('1:N 检索完成', false);
        } catch (e) {
          showMsg('1:N 检索失败: ' + e.message, true);
        }
      });

      document.getElementById('vp-1v1-submit').onclick = () => withBusy('vp-1v1-submit', async () => {
        const gid = document.getElementById('vp-group-id').value.trim() || null;
        const targetFeatureId = document.getElementById('vp-1v1-feature-id').value.trim();
        const audio = document.getElementById('vp-1v1-audio-b64').value.trim();
        if (!targetFeatureId || !audio) return showMsg('1:1 需要目标 featureId 和 audioBase64', true);
        try {
          const row = await AdminApi.fetch('/api/v1/admin/voiceprints/search/1v1', {
            method: 'POST',
            body: JSON.stringify({ groupId: gid, featureId: targetFeatureId, audioBase64: audio })
          });
          document.getElementById('vp-1v1-tbody').innerHTML = renderScoreRows(row ? [row] : []);
          showMsg('1:1 比对完成', false);
        } catch (e) {
          showMsg('1:1 比对失败: ' + e.message, true);
        }
      });

      document.getElementById('vp-group-create').onclick = () => withBusy('vp-group-create', async () => {
        const groupId = document.getElementById('vp-new-group-id').value.trim();
        if (!groupId) return showMsg('请填写 groupId', true);
        try {
          await AdminApi.fetch('/api/v1/admin/voiceprints/groups', {
            method: 'POST',
            body: JSON.stringify({
              groupId: groupId,
              groupName: document.getElementById('vp-new-group-name').value.trim() || null,
              groupInfo: document.getElementById('vp-new-group-info').value.trim() || null
            })
          });
          showMsg('特征库创建请求已发送: ' + groupId, false);
          document.getElementById('vp-center-group-id').value = groupId;
          document.getElementById('vp-del-group-id').value = groupId;
        } catch (e) {
          showMsg('创建特征库失败: ' + e.message, true);
        }
        await loadCenterGroupList();
      });

      document.getElementById('vp-group-delete').onclick = () => withBusy('vp-group-delete', async () => {
        const groupId = document.getElementById('vp-del-group-id').value.trim();
        if (!groupId) return showMsg('请填写要删除的 groupId', true);
        if (!confirm('确认删除特征库 ' + groupId + ' ?')) return;
        try {
          await AdminApi.fetch('/api/v1/admin/voiceprints/groups/' + encodeURIComponent(groupId), { method: 'DELETE' });
          showMsg('特征库删除请求已发送: ' + groupId, false);
          if (document.getElementById('vp-group-id').value.trim() === groupId) {
            document.getElementById('vp-group-id').value = '';
          }
          document.getElementById('vp-center-result').textContent = '已删除特征库: ' + groupId;
        } catch (e) {
          showMsg('删除特征库失败: ' + e.message, true);
        }
        await loadCenterGroupList();
      });
    }

    function showEditor(detail) {
      const isNew = !detail;
      const mapping = isNew ? {} : (detail.mapping || {});
      const vp = isNew ? {} : (detail.primaryVoiceprint || {});
      const grant = isNew ? {} : (detail.dashboardGrant || {});
      const ed = document.getElementById('usr-editor');
      AdminUi.openEditor(ed);
      ed.innerHTML = `
        <h3>${isNew ? '新建用户' : '编辑用户 #' + esc(mapping.userId)}</h3>
        <p class="form-hint">${AdminHints.users.editorIntro}</p>
        <h4>飞书映射 · int_user_mapping_feishu</h4>
        ${AdminForm.field('OA userId', '<input id="ed-user-id" type="number"/>', AdminHints.users.userId)}
        ${AdminForm.field('姓名', '<input id="ed-user-name" type="text"/>', AdminHints.users.userName)}
        ${AdminForm.field('feishu_user_id', '<input id="ed-feishu-user" type="text"/>', AdminHints.users.feishuUserId)}
        <h4>前台授权 · dashboard.user_grants</h4>
        <p id="ed-grant-hint" class="form-hint">${AdminHints.users.dashboardGrantHint}</p>
        <div class="toolbar" style="margin-bottom:0.75rem;">
          <button type="button" class="secondary" id="ed-grant-preset-basic">一键：前台+声纹</button>
          <button type="button" class="secondary" id="ed-grant-preset-full">一键：含建会</button>
        </div>
        <div id="ed-grant-fields">
          <div class="form-check-row">
            <label class="check-label">
              <input type="checkbox" id="ed-grant-enabled" ${grant.enabled ? 'checked' : ''} />
              启用前台授权
            </label>
            <p class="form-hint" style="margin: 0;">${AdminHints.dashboardGrants.enabled}</p>
          </div>
          <div class="form-check-row">
            <label class="check-label">
              <input type="checkbox" id="ed-grant-create" ${grant.canCreateMeeting ? 'checked' : ''} />
              可创建会议
            </label>
            <p class="form-hint" style="margin: 0;">${AdminHints.dashboardGrants.canCreateMeeting}</p>
          </div>
          <div class="form-check-row">
            <label class="check-label">
              <input type="checkbox" id="ed-grant-end" ${grant.canEndMeeting ? 'checked' : ''} />
              可结束会议
            </label>
            <p class="form-hint" style="margin: 0;">${AdminHints.dashboardGrants.canEndMeeting}</p>
          </div>
          <div class="form-check-row">
            <label class="check-label">
              <input type="checkbox" id="ed-grant-vp" ${grant.canRegisterVoiceprint !== false ? 'checked' : ''} />
              可注册声纹
            </label>
            <p class="form-hint" style="margin: 0;">${AdminHints.dashboardGrants.canRegisterVoiceprint}</p>
          </div>
          ${AdminForm.field('备注', '<input id="ed-grant-remark" type="text"/>', AdminHints.dashboardGrants.remark)}
        </div>
        <h4>声纹 · int_voiceprint</h4>
        <input type="hidden" id="ed-vp-id" value="${esc(vp.id || '')}"/>
        ${AdminForm.field('featureId', '<input id="ed-feature-id" type="text"/>', AdminHints.users.featureIdOptional)}
        ${AdminForm.field('groupId', '<input id="ed-group-id" type="text"/>', AdminHints.users.groupId)}
        ${AdminForm.field('注册时间', '<input id="ed-registered" type="datetime-local"/>', AdminHints.users.registeredAt)}
        ${AdminForm.field('过期时间', '<input id="ed-expires" type="datetime-local"/>', AdminHints.users.expiresAt)}
        <div class="form-field">
          <div class="form-check-row">
            <label class="check-label"><input type="checkbox" id="ed-clear-vp"/> 清除该用户全部声纹</label>
          </div>
          ${AdminForm.hint(AdminHints.users.clearVoiceprint)}
        </div>
        <p id="ed-save-msg" class="msg hidden"></p>
        <button type="button" class="primary" id="ed-save">保存</button>
        <button type="button" id="ed-cancel">取消</button>`;

      const uidInput = document.getElementById('ed-user-id');
      uidInput.value = mapping.userId != null ? mapping.userId : '';
      uidInput.readOnly = !isNew;
      document.getElementById('ed-user-name').value = mapping.userName || '';
      document.getElementById('ed-feishu-user').value = mapping.feishuUserId || '';
      document.getElementById('ed-grant-remark').value = grant.remark || '';
      document.getElementById('ed-feature-id').value = vp.featureId || '';
      document.getElementById('ed-group-id').value = vp.groupId || '';
      document.getElementById('ed-registered').value = toLocalInput(vp.registeredAt);
      document.getElementById('ed-expires').value = toLocalInput(vp.expiresAt);
      document.getElementById('ed-clear-vp').checked = false;

      document.getElementById('ed-cancel').onclick = () => AdminUi.closeEditor(ed);
      document.getElementById('ed-save').onclick = () => saveEditor(isNew);

      const syncGrantFieldsDisabled = () => {
        const hasFeishu = !!document.getElementById('ed-feishu-user').value.trim();
        const fields = document.getElementById('ed-grant-fields');
        const hint = document.getElementById('ed-grant-hint');
        if (!fields) return;
        fields.querySelectorAll('input').forEach(el => { el.disabled = !hasFeishu; });
        ['ed-grant-preset-basic', 'ed-grant-preset-full'].forEach(id => {
          const btn = document.getElementById(id);
          if (btn) btn.disabled = !hasFeishu;
        });
        if (hint) {
          hint.textContent = hasFeishu
            ? AdminHints.users.dashboardGrantHint
            : AdminHints.users.dashboardGrantNeedFeishu;
        }
      };
      const applyGrantPresetInEditor = preset => {
        document.getElementById('ed-grant-enabled').checked = true;
        document.getElementById('ed-grant-vp').checked = true;
        if (preset === 'FULL') {
          document.getElementById('ed-grant-create').checked = true;
          document.getElementById('ed-grant-end').checked = true;
        } else {
          document.getElementById('ed-grant-create').checked = false;
          document.getElementById('ed-grant-end').checked = false;
        }
      };
      document.getElementById('ed-grant-preset-basic').onclick = () => applyGrantPresetInEditor('BASIC');
      document.getElementById('ed-grant-preset-full').onclick = () => applyGrantPresetInEditor('FULL');
      document.getElementById('ed-grant-enabled').onchange = e => {
        if (e.target.checked) {
          document.getElementById('ed-grant-create').checked = true;
          document.getElementById('ed-grant-end').checked = true;
          document.getElementById('ed-grant-vp').checked = true;
        }
      };
      document.getElementById('ed-feishu-user').oninput = syncGrantFieldsDisabled;
      syncGrantFieldsDisabled();
    }

    async function saveEditor(isNew) {
      const msgEl = document.getElementById('ed-save-msg');
      const userId = parseUserId(document.getElementById('ed-user-id').value);
      const mapping = {
        userId: userId,
        userName: document.getElementById('ed-user-name').value.trim(),
        feishuUserId: document.getElementById('ed-feishu-user').value.trim() || null
      };
      const registeredRaw = document.getElementById('ed-registered').value.trim();
      const expiresRaw = document.getElementById('ed-expires').value.trim();
      const registeredAt = fromLocalInput(registeredRaw);
      const expiresAt = fromLocalInput(expiresRaw);
      const featureId = document.getElementById('ed-feature-id').value.trim();
      const clearVoiceprint = document.getElementById('ed-clear-vp').checked;

      if (userId == null) {
        msgEl.className = 'msg msg-err'; msgEl.classList.remove('hidden');
        msgEl.textContent = 'OA userId 须为正整数';
        return;
      }
      if (!mapping.userName) {
        msgEl.className = 'msg msg-err'; msgEl.classList.remove('hidden');
        msgEl.textContent = '请填写姓名';
        return;
      }
      if (registeredRaw && !registeredAt) {
        msgEl.className = 'msg msg-err'; msgEl.classList.remove('hidden');
        msgEl.textContent = '注册时间格式无效';
        return;
      }
      if (expiresRaw && !expiresAt) {
        msgEl.className = 'msg msg-err'; msgEl.classList.remove('hidden');
        msgEl.textContent = '过期时间格式无效';
        return;
      }
      if (registeredAt && expiresAt && expiresAt <= registeredAt) {
        msgEl.className = 'msg msg-err'; msgEl.classList.remove('hidden');
        msgEl.textContent = '过期时间须晚于注册时间';
        return;
      }

      const body = {
        mapping: mapping,
        clearVoiceprint: clearVoiceprint,
        dashboardGrant: {
          enabled: document.getElementById('ed-grant-enabled').checked,
          canCreateMeeting: document.getElementById('ed-grant-create').checked,
          canEndMeeting: document.getElementById('ed-grant-end').checked,
          canRegisterVoiceprint: document.getElementById('ed-grant-vp').checked,
          remark: document.getElementById('ed-grant-remark').value.trim()
        }
      };
      if (!clearVoiceprint && featureId) {
        body.voiceprint = {
          id: document.getElementById('ed-vp-id').value.trim() || null,
          userId: userId,
          userName: mapping.userName,
          feishuUserId: mapping.feishuUserId,
          featureId: featureId,
          groupId: document.getElementById('ed-group-id').value.trim() || null,
          registeredAt: registeredAt,
          expiresAt: expiresAt
        };
      }

      try {
        if (isNew) {
          await AdminApi.fetch('/api/v1/admin/users', { method: 'POST', body: JSON.stringify(body) });
        } else {
          await AdminApi.fetch('/api/v1/admin/users/' + userId, { method: 'PUT', body: JSON.stringify(body) });
        }
        AdminUi.closeEditor(document.getElementById('usr-editor'));
        showMsg('用户档案已保存', false);
        loadList();
      } catch (err) {
        msgEl.className = 'msg msg-err'; msgEl.classList.remove('hidden');
        msgEl.textContent = err.message;
      }
    }

    loadGrantPolicy().then(loadList).catch(err => {
      showMsg(err.message, true);
      loadList();
    });
  }
});
