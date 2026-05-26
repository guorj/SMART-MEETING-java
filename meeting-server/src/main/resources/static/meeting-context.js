/**
 * meeting-server 公网路径前缀（与 server.servlet.context-path、Nginx /meeting-server 一致）。
 * 页面在 /meeting-server/rec|host|join 下时返回 '/meeting-server'；裸端口调试无前缀时返回 ''。
 */
function meetingContextPath() {
    const p = location.pathname || '';
    if (p === '/meeting-server' || p.startsWith('/meeting-server/')) {
        return '/meeting-server';
    }
    return '';
}

/** @param {string} path 须以 / 开头，如 /api/v1/meetings/xxx */
function meetingApi(path) {
    const ctx = meetingContextPath();
    const p = path.startsWith('/') ? path : '/' + path;
    return ctx + p;
}

/** @param {string} suffix 如 /ws/audio/{id}?token=... */
function meetingWsUrl(suffix) {
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const s = suffix.startsWith('/') ? suffix : '/' + suffix;
    return proto + '//' + location.host + meetingContextPath() + s;
}

/** @param {string} path 如 /static/recorder.js */
function meetingAsset(path) {
    const p = path.startsWith('/') ? path : '/' + path;
    return meetingContextPath() + p;
}
