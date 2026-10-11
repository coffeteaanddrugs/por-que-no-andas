package com.smartphone.client;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** El teléfono es un ítem (fragmento de eco con CustomModelData 7001) que hay que llevar encima. */
public final class ClientPhoneItem {
	public static final int MODEL = 7001;

	private ClientPhoneItem() {
	}

	public static boolean isPhone(ItemStack stack) {
		if (stack == null || stack.isEmpty() || stack.getItem() != Items.ECHO_SHARD) {
			return false;
		}
		CustomModelDataComponent c = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		return c != null && c.value() == MODEL;
	}

	/** ¿Lleva el jugador un teléfono en el inventario? */
	public static boolean has(PlayerEntity player) {
		if (player == null) {
			return false;
		}
		var inv = player.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			if (isPhone(inv.getStack(i))) {
				return true;
			}
		}
		return false;
	}
}
