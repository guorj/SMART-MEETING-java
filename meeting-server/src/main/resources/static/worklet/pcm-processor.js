/**
 * OpenClaw智能会议系统 - AudioWorklet录音引擎
 * 含自动重采样（设备采样率→16kHz）
 */
class PCMProcessor extends AudioWorkletProcessor {
    constructor() {
        super();
        this._targetSampleRate = 16000;
        this._sourceSampleRate = sampleRate; // AudioWorklet全局变量
        this._needsResample = this._sourceSampleRate !== this._targetSampleRate;
        this._resampleRatio = this._targetSampleRate / this._sourceSampleRate;
    }

    process(inputs) {
        const input = inputs[0];
        if (input.length > 0) {
            let channelData = input[0];

            // 重采样（如果设备采样率不是16kHz）
            if (this._needsResample) {
                const outputLength = Math.round(channelData.length * this._resampleRatio);
                const resampled = new Float32Array(outputLength);
                for (let i = 0; i < outputLength; i++) {
                    const srcIndex = i / this._resampleRatio;
                    const floor = Math.floor(srcIndex);
                    const ceil = Math.min(floor + 1, channelData.length - 1);
                    const frac = srcIndex - floor;
                    resampled[i] = channelData[floor] * (1 - frac) + channelData[ceil] * frac;
                }
                channelData = resampled;
            }

            // Float32 → Int16 PCM
            const pcm16 = new Int16Array(channelData.length);
            for (let i = 0; i < channelData.length; i++) {
                const s = Math.max(-1, Math.min(1, channelData[i]));
                pcm16[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
            }

            this.port.postMessage(pcm16.buffer, [pcm16.buffer]);
        }
        return true;
    }
}

registerProcessor('pcm-processor', PCMProcessor);
