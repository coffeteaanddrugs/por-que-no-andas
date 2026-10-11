package com.smartphone.client;

import com.smartphone.net.PhoneC2S;
import com.smartphone.net.PhoneS2C;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class PhoneClient implements ClientModInitializer {
	public static KeyBinding openKey;
	public static KeyBinding answerKey;
	public static KeyBinding hangupKey;
	public static KeyBinding photoKey;

	/** ticks desde que entraste al servidor (-1 = no conectado) */
	private static int joinTicks = -1;

	private static final String CATEGORY = "key.categories.smartphone";

	@Override
	public void onInitializeClient() {
		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.smartphone.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UP, CATEGORY));
		answerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.smartphone.answer", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY));
		hangupKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.smartphone.hangup", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY));
		photoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.smartphone.photo", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_ENTER, CATEGORY));

		PayloadTypeRegistry.playC2S().register(PhoneC2S.ID, PhoneC2S.CODEC);
		PayloadTypeRegistry.playS2C().register(PhoneS2C.ID, PhoneS2C.CODEC);

		ClientPlayNetworking.registerGlobalReceiver(PhoneS2C.ID,
				(payload, context) -> ClientPacketHandler.handle(payload.action(), payload.data()));

		HudRenderCallback.EVENT.register(HudOverlay::render);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ClientPhoneState.reset();
			joinTicks = 0;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientPhoneState.reset();
			CameraMode.exit();
			joinTicks = -1;
		});

		ClientTickEvents.END_CLIENT_TICK.register(PhoneClient::tick);
	}

	private static void tick(MinecraftClient client) {
		if (client.player == null) {
			return;
		}

		helloTick();

		boolean hasPhone = ClientPhoneItem.has(client.player);
		if (!hasPhone && client.currentScreen instanceof PhoneScreen) {
			client.setScreen(null);
		}
		if (!hasPhone && CameraMode.active) {
			CameraMode.exit();
		}

		while (openKey.wasPressed()) {
			if (!hasPhone) {
				continue;
			}
			if (CameraMode.active) {
				CameraMode.exit();
			}
			if (client.currentScreen == null) {
				client.setScreen(new PhoneScreen());
			}
		}
		while (answerKey.wasPressed()) {
			if (hasPhone && ClientPhoneState.callState.equals("incoming")) {
				ClientPhoneState.send("call_answer");
			}
		}
		while (hangupKey.wasPressed()) {
			switch (ClientPhoneState.callState) {
				case "incoming" -> ClientPhoneState.send("call_decline");
				case "outgoing", "active" -> ClientPhoneState.send("call_hangup");
				default -> {
				}
			}
		}
		while (photoKey.wasPressed()) {
			if (hasPhone) {
				CameraMode.shoot();
			}
		}

		CameraMode.tick(client);
		if (hasPhone) {
			ringtone();
		}
	}

	/** Le pide al plugin del servidor los datos del teléfono (y reintenta si no contesta). */
	private static void helloTick() {
		if (joinTicks < 0) {
			return;
		}
		joinTicks++;
		if (!ClientPhoneState.myNumber.isEmpty()) {
			joinTicks = -1;
			return;
		}
		if (joinTicks == 20 || joinTicks == 120 || joinTicks == 400) {
			ClientPhoneState.send("hello");
		}
		if (joinTicks == 600) {
			ClientPhoneState.toast("Teléfono", "El servidor no responde. ¿Falta el plugin Smartphone?");
			joinTicks = -1;
		}
	}

	/** Timbre de llamada entrante y tono de llamada saliente. */
	private static void ringtone() {
		String state = ClientPhoneState.callState;
		if (state.equals("incoming") && !ClientSettings.get().dnd) {
			Sounds.ringtoneStep(ClientPhoneState.callTicks++ % 50);
		} else if (state.equals("outgoing")) {
			if (ClientPhoneState.callTicks++ % 60 == 0) {
				Sounds.ringBack();
			}
		}
	}
}
