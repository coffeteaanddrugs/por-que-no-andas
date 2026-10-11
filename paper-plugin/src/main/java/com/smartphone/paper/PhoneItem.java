package com.smartphone.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * El item "Teléfono". Es un fragmento de eco con un CustomModelData especial: con el mod instalado
 * se ve como un móvil. Hay que tenerlo en el inventario para usar el teléfono.
 */
public final class PhoneItem {
	/** Debe coincidir con el valor del mod (PhoneItem.MODEL_ID en el cliente). */
	public static final int MODEL = 7001;
	public static final Material BASE = Material.ECHO_SHARD;

	private PhoneItem() {
	}

	public static ItemStack create() {
		ItemStack item = new ItemStack(BASE);
		ItemMeta meta = item.getItemMeta();
		meta.displayName(Component.text("Teléfono", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
		meta.lore(List.of(
				Component.text("Llévalo contigo para poder", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
				Component.text("usar el teléfono.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
		meta.setCustomModelData(MODEL);
		item.setItemMeta(meta);
		return item;
	}

	public static boolean isPhone(ItemStack stack) {
		if (stack == null || stack.getType() != BASE || !stack.hasItemMeta()) {
			return false;
		}
		ItemMeta meta = stack.getItemMeta();
		return meta.hasCustomModelData() && meta.getCustomModelData() == MODEL;
	}

	public static boolean has(Player player) {
		for (ItemStack stack : player.getInventory().getContents()) {
			if (isPhone(stack)) {
				return true;
			}
		}
		return false;
	}
}
