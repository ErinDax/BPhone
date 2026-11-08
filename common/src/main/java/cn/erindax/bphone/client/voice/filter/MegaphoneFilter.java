package cn.erindax.bphone.client.voice.filter;

import su.plo.voice.api.client.audio.filter.AudioFilter;
import su.plo.voice.api.client.audio.filter.AudioFilterContext;

public final class MegaphoneFilter implements AudioFilter {
    private static final float TWO_PI = (float) (Math.PI * 2.0D);
    private static final float HIGH_PASS_HZ = 560.0F;
    private static final float LOW_PASS_HZ = 2350.0F;
    private static final float INPUT_GAIN = 1.9F;
    private static final float PRESENCE_MIX = 0.22F;
    private static final float COMPRESSOR_RELEASE = 0.965F;
    private static final float COMPRESSOR_STRENGTH = 1.8F;
    private static final float DRIVE_GAIN = 2.35F;
    private static final float BIT_CRUSH_STEPS = 480.0F;
    private static final float STATIC_MIX = 0.0025F;
    private static final float OUTPUT_GAIN = 1.18F;
    private static final float TO_FLOAT = 1.0F / 32768.0F;

    private float sampleRate = -1.0F;
    private float alphaHighPass;
    private float alphaLowPass;
    private float[] highPass1PreviousInput = new float[1];
    private float[] highPass1Output = new float[1];
    private float[] highPass2PreviousInput = new float[1];
    private float[] highPass2Output = new float[1];
    private float[] lowPass1Output = new float[1];
    private float[] lowPass2Output = new float[1];
    private float[] compressorEnvelope = new float[1];
    private int noiseState = 0x5BADC0DE;

    @Override
    public String getName() {
        return "beat:megaphone";
    }

    @Override
    public short[] process(AudioFilterContext context, short[] samples) {
        if (samples.length == 0) {
            return samples;
        }

        int channels = Math.max(1, context.getChannels());
        ensureState(context, channels);

        for (int i = 0; i < samples.length; i++) {
            int channel = i % channels;
            float input = samples[i] * TO_FLOAT * INPUT_GAIN;

            float highPassStage1 = alphaHighPass * (
                    highPass1Output[channel] + input - highPass1PreviousInput[channel]
            );
            highPass1PreviousInput[channel] = input;
            highPass1Output[channel] = highPassStage1;

            float highPassStage2 = alphaHighPass * (
                    highPass2Output[channel] + highPassStage1 - highPass2PreviousInput[channel]
            );
            highPass2PreviousInput[channel] = highPassStage1;
            highPass2Output[channel] = highPassStage2;

            float lowPassStage1 = lowPass1Output[channel] + alphaLowPass * (highPassStage2 - lowPass1Output[channel]);
            lowPass1Output[channel] = lowPassStage1;

            float lowPassStage2 = lowPass2Output[channel] + alphaLowPass * (lowPassStage1 - lowPass2Output[channel]);
            lowPass2Output[channel] = lowPassStage2;

            float bandPassed = lowPassStage2;
            float brightPresence = bandPassed + ((highPassStage2 - bandPassed) * PRESENCE_MIX);

            float envelope = Math.max(Math.abs(brightPresence), compressorEnvelope[channel] * COMPRESSOR_RELEASE);
            compressorEnvelope[channel] = envelope;
            float compressed = brightPresence * (1.35F / (1.0F + (envelope * COMPRESSOR_STRENGTH)));

            float driven = compressed * DRIVE_GAIN;
            float saturated = applyMegaphoneSaturation(driven);
            float quantized = applyBitCrush(saturated, BIT_CRUSH_STEPS);
            float finalSample = quantized + (nextStaticSample() * STATIC_MIX);

            samples[i] = toShort(finalSample * OUTPUT_GAIN);
        }

        return samples;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public int getSupportedChannels() {
        return 0;
    }

    private void ensureState(AudioFilterContext context, int channels) {
        if (highPass1PreviousInput.length != channels) {
            highPass1PreviousInput = new float[channels];
            highPass1Output = new float[channels];
            highPass2PreviousInput = new float[channels];
            highPass2Output = new float[channels];
            lowPass1Output = new float[channels];
            lowPass2Output = new float[channels];
            compressorEnvelope = new float[channels];
        }

        float currentSampleRate = context.getDevice().getFormat().getSampleRate();
        if (currentSampleRate <= 0.0F) {
            currentSampleRate = 48000.0F;
        }

        if (sampleRate == currentSampleRate) {
            return;
        }

        sampleRate = currentSampleRate;
        float dt = 1.0F / sampleRate;

        float highPassRc = 1.0F / (TWO_PI * HIGH_PASS_HZ);
        alphaHighPass = highPassRc / (highPassRc + dt);

        float lowPassRc = 1.0F / (TWO_PI * LOW_PASS_HZ);
        alphaLowPass = dt / (lowPassRc + dt);

        highPass1PreviousInput = new float[channels];
        highPass1Output = new float[channels];
        highPass2PreviousInput = new float[channels];
        highPass2Output = new float[channels];
        lowPass1Output = new float[channels];
        lowPass2Output = new float[channels];
        compressorEnvelope = new float[channels];
    }

    private float nextStaticSample() {
        noiseState = noiseState * 1664525 + 1013904223;
        int bits = (noiseState >>> 9) & 0x7FFF;
        return (bits / 16384.0F) - 1.0F;
    }

    private static float applyMegaphoneSaturation(float input) {
        float primary = (float) Math.tanh(input);
        float edge = (float) Math.tanh(input * 0.55F);
        float mixed = (primary * 0.82F) + (edge * 0.18F);
        return mixed >= 0.0F ? mixed * 0.92F : mixed * 1.05F;
    }

    private static float applyBitCrush(float input, float steps) {
        if (steps <= 0.0F) {
            return input;
        }

        return Math.round(input * steps) / steps;
    }

    private static short toShort(float input) {
        float clamped = Math.max(-1.0F, Math.min(1.0F, input));
        return (short) Math.round(clamped * 32767.0F);
    }
}
