/**
 * OpenClaw智能会议纪要系统 - 录音控制器 v2
 * 支持AudioWorklet + ScriptProcessorNode降级 + 自动重采样 + 断线重连 + 波形可视化
 */
/** 与 index.html / host-meeting.html 中 script 的 ?v= 同步修改，用于 worklet 等子资源破缓存 */
const SM_STATIC_ASSET_V = 'sm-20260512-6';

function smAssetUrl(path) {
    const sep = path.includes('?') ? '&' : '?';
    return path + sep + 'v=' + encodeURIComponent(SM_STATIC_ASSET_V);
}

class MeetingRecorder {
    constructor(meetingId, token, wsUrl) {
        this.meetingId = meetingId;
        this.token = token;
        this.wsUrlBase = wsUrl || null;  // 自定义ws URL
        this.sessionToken = null;
        this.ws = null;
        this.audioContext = null;
        this.mediaStream = null;
        this.workletNode = null;
        this.analyser = null;  // 波形分析器
        this.isRecording = false;
        this.isPaused = false;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.pendingChunks = [];
        this._useFallback = false;
        this._sourceNode = null;
    }

    async start() {
        // 1. 请求麦克风权限（必须由用户手势触发！）
        try {
            this.mediaStream = await navigator.mediaDevices.getUserMedia({
                audio: {
                    channelCount: 1,
                    echoCancellation: true,
                    noiseSuppression: true,
                }
            });
        } catch (err) {
            if (err.name === 'NotAllowedError') {
                this.onError('麦克风权限被拒绝，请在浏览器设置中允许访问麦克风');
            } else if (err.name === 'NotFoundError') {
                this.onError('未检测到麦克风设备');
            } else {
                this.onError('麦克风访问失败: ' + err.message);
            }
            return;
        }

        // 2. 创建AudioContext（尝试16kHz）
        try {
            this.audioContext = new AudioContext({ sampleRate: 16000 });
        } catch (e) {
            this.audioContext = new AudioContext();
        }

        const deviceRate = this.audioContext.sampleRate;
        console.log(`AudioContext sampleRate: ${deviceRate}Hz`);

        // 3. 创建AnalyserNode（波形可视化）
        this.analyser = this.audioContext.createAnalyser();
        this.analyser.fftSize = 2048;

        // 4. 尝试AudioWorklet
        let useWorklet = false;
        try {
            if (this.audioContext.audioWorklet) {
                // 尝试多种路径加载worklet
                const workletPaths = [
                    smAssetUrl('/static/worklet/pcm-processor.js'),
                    smAssetUrl('/worklet/pcm-processor.js'),
                    smAssetUrl('./worklet/pcm-processor.js'),
                ];
                for (const p of workletPaths) {
                    try {
                        await this.audioContext.audioWorklet.addModule(p);
                        useWorklet = true;
                        console.log(`AudioWorklet loaded from: ${p}`);
                        break;
                    } catch (e) {
                        continue;
                    }
                }
            }
        } catch (err) {
            console.warn('AudioWorklet不可用，使用降级模式', err);
        }

        // 5. 连接音频链路
        this._sourceNode = this.audioContext.createMediaStreamSource(this.mediaStream);
        this._sourceNode.connect(this.analyser);  // 可视化链路

        // 通知外部有analyser可用
        if (this.onAnalyser) this.onAnalyser(this.analyser);

        if (useWorklet) {
            this.workletNode = new AudioWorkletNode(this.audioContext, 'pcm-processor');
            this.workletNode.port.onmessage = (event) => {
                if (this.isRecording && !this.isPaused) {
                    this._sendAudio(event.data);
                }
            };
            this._sourceNode.connect(this.workletNode);
            this.workletNode.connect(this.audioContext.destination);
        } else {
            // 降级：ScriptProcessorNode + 手动重采样
            this._useFallback = true;
            const bufferSize = Math.max(256, Math.round(640 * (deviceRate / 16000)));
            const processor = this.audioContext.createScriptProcessor(bufferSize, 1, 1);

            processor.onaudioprocess = (event) => {
                if (!this.isRecording || this.isPaused) return;
                let float32 = event.inputBuffer.getChannelData(0);

                // 重采样到16kHz
                if (deviceRate !== 16000) {
                    const ratio = 16000 / deviceRate;
                    const outputLen = Math.round(float32.length * ratio);
                    const resampled = new Float32Array(outputLen);
                    for (let i = 0; i < outputLen; i++) {
                        const srcIdx = i / ratio;
                        const floor = Math.floor(srcIdx);
                        const ceil = Math.min(floor + 1, float32.length - 1);
                        const frac = srcIdx - floor;
                        resampled[i] = float32[floor] * (1 - frac) + float32[ceil] * frac;
                    }
                    float32 = resampled;
                }

                // Float32 → Int16 PCM
                const pcm16 = new Int16Array(float32.length);
                for (let i = 0; i < float32.length; i++) {
                    const s = Math.max(-1, Math.min(1, float32[i]));
                    pcm16[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
                }
                this._sendAudio(pcm16.buffer);
            };

            this._sourceNode.connect(processor);
            processor.connect(this.audioContext.destination);
            this.onWarning?.('当前浏览器使用降级录音模式');
        }

        // 6. 建立WebSocket
        await this._connectWebSocket();

        this.isRecording = true;
        this.onStatusChange?.('recording');
    }

    _buildWsUrl() {
        if (this.wsUrlBase && !this.sessionToken) {
            return this.wsUrlBase;
        }
        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const host = location.host;
        const base = `${protocol}//${host}/ws/audio/${this.meetingId}`;
        if (this.sessionToken) {
            return `${base}?token=${encodeURIComponent(this.token)}&reconnect=true&session_token=${encodeURIComponent(this.sessionToken)}`;
        }
        return `${base}?token=${encodeURIComponent(this.token)}`;
    }

    async _connectWebSocket() {
        const wsUrl = this._buildWsUrl();
        console.log('Connecting WebSocket:', wsUrl.replace(/token=[^&]+/, 'token=***'));

        this.ws = new WebSocket(wsUrl);
        this.ws.binaryType = 'arraybuffer';

        return new Promise((resolve, reject) => {
            this.ws.onopen = () => {
                this.reconnectAttempts = 0;
                // 发送积压数据
                while (this.pendingChunks.length > 0 && this.ws.readyState === WebSocket.OPEN) {
                    this.ws.send(this.pendingChunks.shift());
                }
                resolve();
            };

            this.ws.onmessage = (event) => {
                if (typeof event.data === 'string') {
                    try {
                        const msg = JSON.parse(event.data);
                        if (msg.type === 'session') {
                            this.sessionToken = msg.session_token;
                        } else if (msg.type === 'ping') {
                            this.ws.send(JSON.stringify({ type: 'pong' }));
                        } else {
                            this.onTranscript?.(msg);
                        }
                    } catch (e) {
                        console.warn('WS消息解析失败:', e);
                    }
                }
            };

            this.ws.onclose = (event) => {
                if (this.isRecording && event.code !== 1000 && event.code !== 1001) {
                    console.warn(`WS closed: code=${event.code} reason=${event.reason}`);
                    this._reconnect();
                }
            };

            this.ws.onerror = (err) => {
                console.error('WS error:', err);
                reject(new Error('WebSocket连接失败'));
            };

            // 超时处理
            setTimeout(() => {
                if (this.ws.readyState !== WebSocket.OPEN) {
                    reject(new Error('WebSocket连接超时'));
                }
            }, 10000);
        });
    }

    _sendAudio(pcmBuffer) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(pcmBuffer);
        } else {
            this.pendingChunks.push(pcmBuffer);
            // 缓冲区最多存~30秒的音频 (750 * 40ms)
            if (this.pendingChunks.length > 750) this.pendingChunks.shift();
        }
    }

    async _reconnect() {
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            this.onError?.('连接断开，重连失败');
            return;
        }
        this.reconnectAttempts++;
        const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts - 1), 30000);
        this.onStatusChange?.('reconnecting');
        console.log(`重连中... 第${this.reconnectAttempts}次，${delay}ms后重试`);
        await new Promise(r => setTimeout(r, delay));
        try {
            await this._connectWebSocket();
            this.onStatusChange?.('recording');
        } catch {
            this._reconnect();
        }
    }

    pause() {
        this.isPaused = true;
        if (this.ws?.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify({ type: 'pause' }));
        }
        this.onStatusChange?.('paused');
    }

    resume() {
        this.isPaused = false;
        if (this.ws?.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify({ type: 'resume' }));
        }
        this.onStatusChange?.('recording');
    }

    stop() {
        this.isRecording = false;
        if (this.workletNode) this.workletNode.disconnect();
        if (this._sourceNode) this._sourceNode.disconnect();
        if (this.audioContext && this.audioContext.state !== 'closed') {
            this.audioContext.close().catch(() => {});
        }
        if (this.mediaStream) this.mediaStream.getTracks().forEach(t => t.stop());
        if (this.ws) this.ws.close(1000, "User stopped");
        this.onStatusChange?.('stopped');
    }

    // 回调（由外部覆盖）
    onStatusChange(status) {}
    onTranscript(result) {}
    onError(message) {}
    onWarning(message) {}
    onAnalyser(analyserNode) {}
}
