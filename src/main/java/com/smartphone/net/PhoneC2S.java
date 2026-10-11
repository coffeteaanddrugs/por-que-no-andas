package com.smartphone.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Cliente -> Servidor. "action" dice qué quiere hacer, "data" es un JSON. */
public record PhoneC2S(String action, String data) implements CustomPayload {
	public static final CustomPayload.Id<PhoneC2S> ID = new CustomPayload.Id<>(Identifier.of("smartphone", "c2s"));
	public static final PacketCodec<RegistryByteBuf, PhoneC2S> CODEC = PacketCodec.tuple(
			PacketCodecs.string(32), PhoneC2S::action,
			PacketCodecs.string(4096), PhoneC2S::data,
			PhoneC2S::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
