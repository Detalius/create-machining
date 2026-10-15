package com.jessehandke.createmachining.content.lathe;

import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Everything a turning recipe JSON can contain: Create's standard fields (ingredients, results,
 * processing_time) plus our ideal spindle speed band, "min_rpm" and "max_rpm".
 *
 * Same pattern Create uses for the Deployer's extra "keep_held_item" field.
 */
public class TurningRecipeParams extends ProcessingRecipeParams {
    public static final int DEFAULT_MIN_RPM = 32;
    public static final int DEFAULT_MAX_RPM = 128;

    /**
     * A Codec is a two-way translator between JSON and a Java object. This one says: read all of
     * Create's standard fields, then also read min_rpm and max_rpm (with defaults if missing).
     */
    public static final MapCodec<TurningRecipeParams> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            codec(TurningRecipeParams::new).forGetter(Function.identity()),
            Codec.INT.optionalFieldOf("min_rpm", DEFAULT_MIN_RPM).forGetter(p -> p.minRpm),
            Codec.INT.optionalFieldOf("max_rpm", DEFAULT_MAX_RPM).forGetter(p -> p.maxRpm)
    ).apply(instance, (params, minRpm, maxRpm) -> {
        params.minRpm = minRpm;
        params.maxRpm = maxRpm;
        return params;
    }));

    /** The network version: how the server sends this recipe to clients. */
    public static final StreamCodec<RegistryFriendlyByteBuf, TurningRecipeParams> STREAM_CODEC =
            streamCodec(TurningRecipeParams::new);

    protected int minRpm = DEFAULT_MIN_RPM;
    protected int maxRpm = DEFAULT_MAX_RPM;

    @Override
    protected void encode(RegistryFriendlyByteBuf buffer) {
        super.encode(buffer);
        ByteBufCodecs.VAR_INT.encode(buffer, minRpm);
        ByteBufCodecs.VAR_INT.encode(buffer, maxRpm);
    }

    @Override
    protected void decode(RegistryFriendlyByteBuf buffer) {
        super.decode(buffer);
        minRpm = ByteBufCodecs.VAR_INT.decode(buffer);
        maxRpm = ByteBufCodecs.VAR_INT.decode(buffer);
    }
}
