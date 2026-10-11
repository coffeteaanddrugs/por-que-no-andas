package com.smartphone.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Servidor -> Cliente. */
public record PhoneS2C(String action, String data) implements CustomPayload {
	public static final CustomPayload.Id<PhoneS2C> ID = new CustomPayload.Id<>(Identifier.of("smartphone", "s2c"));
	public static final PacketCodec<RegistryByteBuf, PhoneS2C> CODEC = PacketCodec.tuple(
			PacketCodecs.string(32), PhoneS2C::action,
			PacketCodecs.string(300000), PhoneS2C::data,
			PhoneS2C::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
