package cn.erindax.bphone.client.voice.filter;

import su.plo.voice.api.client.audio.filter.AudioFilter;
import su.plo.voice.api.client.audio.filter.AudioFilterContext;

public final class PhoneCallFilter implements AudioFilter {
    private static final float TWO_PI = (float) (Math.PI * 2.0D);
    private static final float HIGH_PASS_HZ = 280.0F;
    private static final float LOW_PASS_HZ = 3400.0F;
    private static final float PRE_EMPHASIS = 0.28F;
    private static final float DRIVE_GAIN = 1.38F;
    private static final float SOFT_CLIP_THRESHOLD = 0.92F;
    private static final float STATIC_MIX = 0.0015F;
    private static final float OUTPUT_GAIN = 2.25F;
    private static final float TO_FLOAT = 1.0F / 32768.0F;

    private float sampleRate = -1.0F;
    private float alphaHighPass;
    private float alphaLowPass;
    private float[] previousInput = new float[1];
    private float[] highPassOutput = new float[1];
    private float[] lowPassOutput = new float[1];
    private float[] preEmphasisState = new float[1];
    private int noiseState = 0x13579BDF;

    @Override
    public String getName() {
        return "beat:phone_call";
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
            float input = samples[i] * TO_FLOAT;
            float preEmphasis = input - preEmphasisState[channel] * PRE_EMPHASIS;
            preEmphasisState[channel] = input;

            float highPass = alphaHighPass * (highPassOutput[channel] + preEmphasis - previousInput[channel]);
            previousInput[channel] = preEmphasis;
            highPassOutput[channel] = highPass;

            float lowPass = lowPassOutput[channel] + alphaLowPass * (highPass - lowPassOutput[channel]);
            lowPassOutput[channel] = lowPass;

            float driven = lowPass * DRIVE_GAIN;
            float clipped = applySoftClip(driven, SOFT_CLIP_THRESHOLD);
            float noisy = clipped + nextStaticSample() * STATIC_MIX;
            samples[i] = toShort(noisy * OUTPUT_GAIN);
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
        if (previousInput.length != channels) {
            previousInput = new float[channels];
            highPassOutput = new float[channels];
            lowPassOutput = new float[channels];
            preEmphasisState = new float[channels];
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

        previousInput = new float[channels];
        highPassOutput = new float[channels];
        lowPassOutput = new float[channels];
        preEmphasisState = new float[channels];
    }

    private float nextStaticSample() {
        noiseState = noiseState * 1664525 + 1013904223;
        int bits = (noiseState >>> 9) & 0x7FFF;
        return (bits / 16384.0F) - 1.0F;
    }

    private static float applySoftClip(float input, float threshold) {
        if (input >= -threshold && input <= threshold) {
            return input;
        }

        float sign = Math.signum(input);
        float over = (Math.abs(input) - threshold) / Math.max(0.0001F, 1.0F - threshold);
        return sign * (threshold + (1.0F - threshold) * (float) Math.tanh(over));
    }

    private static short toShort(float input) {
        float clamped = Math.max(-1.0F, Math.min(1.0F, input));
        return (short) Math.round(clamped * 32767.0F);
    }
}
