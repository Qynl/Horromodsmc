package com.horromods.hollow.network;

import com.horromods.hollow.Hollow;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server → client sync of the player's current Dread (0-100), once a second. */
public record DreadPayload(float dread) implements CustomPayload {
    public static final CustomPayload.Id<DreadPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Hollow.MOD_ID, "dread"));

    public static final PacketCodec<RegistryByteBuf, DreadPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeFloat(payload.dread()),
            buf -> new DreadPayload(buf.readFloat()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
