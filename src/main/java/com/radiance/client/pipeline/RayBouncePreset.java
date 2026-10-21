package com.radiance.client.pipeline;

/** Bounce limits independent of pipeline topology, denoising and upscaling. */
public enum RayBouncePreset {
    DEFAULT(4, "render_pipeline.bounces.default"),
    BALANCED(3, "render_pipeline.bounces.balanced"),
    LOW(2, "render_pipeline.bounces.low");

    public final int bounces;
    public final String translationKey;

    RayBouncePreset(int bounces, String translationKey) {
        this.bounces = bounces;
        this.translationKey = translationKey;
    }
}
