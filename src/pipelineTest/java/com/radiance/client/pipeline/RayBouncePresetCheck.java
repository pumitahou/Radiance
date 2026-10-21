package com.radiance.client.pipeline;

import com.radiance.client.RadianceClient;
import com.radiance.client.pipeline.config.AttributeConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Exercises the real pipeline setting and its per-pack persistence without a game or GPU. */
public final class RayBouncePresetCheck {
    private static final String BOUNCES = "render_pipeline.module.main_render.attribute.num_ray_bounces";

    private static AttributeConfig attribute(String name, String type, String value) {
        AttributeConfig result = new AttributeConfig();
        result.name = name;
        result.type = type;
        result.value = value;
        result.defaultValue = value;
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("radiance-bounce-presets-");
        try {
            RadianceClient.radianceDir = directory;
            Pipeline.initFolderPath(directory);
            Pipeline.clear();
            Module module = new Module();
            module.name = "render_pipeline.module.main_render.name";
            AttributeConfig pack = attribute("render_pipeline.module.main_render.attribute.shader_pack_path",
                    "string", "shaders/world/ray_tracing/vanilla-pt.zip");
            AttributeConfig bounces = attribute(BOUNCES, "int_range:1-128", "4");
            AttributeConfig water = attribute("render_pipeline.module.main_render.attribute.water_surface_mode",
                    "string", "unchanged-water");
            module.staticAttributeConfigs = List.of(pack);
            module.attributeConfigs = new ArrayList<>(List.of(pack, bounces, water));
            module.inputImageConfigs = List.of();
            module.outputImageConfigs = List.of();
            var field = Pipeline.class.getDeclaredField("modules");
            field.setAccessible(true);
            ((List<Module>) field.get(Pipeline.INSTANCE)).add(module);
            require(Pipeline.getRayBouncePreset() == RayBouncePreset.DEFAULT, "Default must recognize 4");
            for (RayBouncePreset preset : List.of(RayBouncePreset.LOW, RayBouncePreset.BALANCED,
                    RayBouncePreset.DEFAULT)) {
                require(Pipeline.applyRayBouncePreset(preset), "Selection must change the setting");
                require(Pipeline.getRayBouncePreset() == preset, "Selection is not synchronized");
                require(water.value.equals("unchanged-water"), "Selection modified another setting");
                Pipeline.savePipeline();
                try (var paths = Files.list(directory.resolve("shader-pack-settings"))) {
                    Path settings = paths.findFirst().orElseThrow();
                    String text = Files.readString(settings);
                    require(text.contains(BOUNCES + "=" + preset.bounces + "\n"), "Bounce limit was not persisted");
                    require(text.contains(water.name + "=unchanged-water\n"), "Other pack settings were lost");
                }
                require(!Pipeline.applyRayBouncePreset(preset), "Selecting an active preset should be a no-op");
            }
            bounces.value = "5";
            require(Pipeline.getRayBouncePreset() == null, "Custom bounce limit must not select a preset");
            bounces.type = "int_range:3-4";
            require(!Pipeline.applyRayBouncePreset(RayBouncePreset.LOW), "Unsupported range accepted");
            require(bounces.value.equals("5"), "Unsupported preset changed the value");
            bounces.type = "float";
            require(!Pipeline.canApplyRayBouncePreset(RayBouncePreset.DEFAULT), "Noninteger setting accepted");
            Pipeline.clear();
            require(!Pipeline.applyRayBouncePreset(RayBouncePreset.LOW), "Missing module accepted");
            require(Pipeline.getRayBouncePreset() == null, "Missing module must have no active preset");
            System.out.println("PASS: 4/3/2 selection, per-pack persistence, other settings preserved, custom and unsupported settings");
        } finally {
            Pipeline.clear();
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
