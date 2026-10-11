package com.smartphone.paper;

import com.google.gson.JsonObject;
import com.smartphone.common.Contact;
import com.smartphone.common.Json;
import com.smartphone.common.Numbers;
import com.smartphone.common.TransferEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cashapp: lee el saldo de BankPlus (mediante PlaceholderAPI) y transfiere dinero
 * ejecutando el comando de pago de BankPlus como si lo escribiera el jugador.
 * Todo se puede ajustar en plugins/Smartphone/config.yml.
 */
public final class Cash {
	private static String symbol = "$";
	private static String balancePlaceholder = "%bankplus_balance_long%";
	private static String payCommand = "bank pay {player} {amount}";
	private static final Set<UUID> pending = new HashSet<>();
	private static final long MAX_AMOUNT = 1_000_000_000_000L;

	private Cash() {
	}

	public static void init(SmartphonePlugin plugin) {
		plugin.saveDefaultConfig();
		FileConfiguration c = plugin.getConfig();
		symbol = c.getString("currency-symbol", "$");
		balancePlaceholder = c.getString("balance-placeholder", "%bankplus_balance_long%");
		payCommand = c.getString("pay-command", "bank pay {player} {amount}");
	}

	private static boolean placeholderApi() {
		return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
	}

	/** Saldo del jugador, o null si no se puede leer. */
	private static Long balanceOf(Player player) {
		if (!placeholderApi()) {
			return null;
		}
		try {
			Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
			Method m = papi.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
			String result = (String) m.invoke(null, player, balancePlaceholder);
			if (result == null || result.equals(balancePlaceholder)) {
				return null;
			}
			String digits = result.replaceAll("[^0-9-]", "");
			if (digits.isEmpty() || digits.equals("-")) {
				return null;
			}
			return Long.parseLong(digits);
		} catch (Exception e) {
			return null;
		}
	}

	private static String nameOf(Player owner, String number) {
		for (Contact c : Handler.data.contactsOf(owner.getUniqueId().toString())) {
			if (c.number.equals(number)) {
				return c.name;
			}
		}
		return number;
	}

	/** Manda a la app el saldo y los últimos movimientos. */
	public static void sendCash(Player player) {
		Long balance = balanceOf(player);
		JsonObject o = new JsonObject();
		o.addProperty("available", balance != null);
		o.addProperty("balance", balance == null ? 0L : balance);
		o.addProperty("symbol", symbol);
		o.add("history", Json.GSON.toJsonTree(Handler.data.transfersOf(player.getUniqueId().toString())));
		Handler.send(player, "cash", o);
	}

	public static void send(Player player, JsonObject in) {
		UUID id = player.getUniqueId();
		if (pending.contains(id)) {
			return;
		}
		String me = Handler.data.numberFor(id);
		String to = Numbers.normalize(Handler.str(in, "to"));
		long amount;
		try {
			amount = Long.parseLong(Handler.str(in, "amount"));
		} catch (NumberFormatException e) {
			Handler.toast(player, "Monto inválido");
			return;
		}
		if (amount <= 0 || amount > MAX_AMOUNT) {
			Handler.toast(player, "Monto inválido");
			return;
		}
		if (to == null || Handler.data.uuidOf(to) == null) {
			Handler.toast(player, "Ese número no existe");
			return;
		}
		if (to.equals(me)) {
			Handler.toast(player, "No puedes enviarte dinero a ti mismo");
			return;
		}
		Player target = Handler.online(Handler.data.uuidOf(to));
		if (target == null) {
			Handler.toast(player, "La otra persona debe estar conectada");
			return;
		}
		Long before = balanceOf(player);
		if (before == null) {
			Handler.toast(player, "El banco no está disponible");
			return;
		}
		if (before < amount) {
			Handler.toast(player, "Saldo insuficiente");
			return;
		}

		pending.add(id);
		String command = payCommand.replace("{player}", target.getName()).replace("{amount}", String.valueOf(amount));
		player.performCommand(command);

		final long sent = amount;
		final String targetNumber = to;
		Bukkit.getScheduler().runTaskLater(SmartphonePlugin.get(), () -> {
			pending.remove(id);
			Player sender = Bukkit.getPlayer(id);
			if (sender == null) {
				return;
			}
			Long after = balanceOf(sender);
			if (after == null || after >= before) {
				Handler.toast(sender, "La transferencia no se completó");
				sendCash(sender);
				return;
			}
			long now = System.currentTimeMillis();
			Handler.data.addTransfer(id.toString(), new TransferEntry(targetNumber, "out", sent, now));
			Handler.data.addTransfer(target.getUniqueId().toString(), new TransferEntry(me, "in", sent, now));
			Handler.toast(sender, "Enviaste " + symbol + sent + " a " + nameOf(sender, targetNumber));
			sendCash(sender);
			Player receiver = Bukkit.getPlayer(target.getUniqueId());
			if (receiver != null) {
				Handler.toast(receiver, "Recibiste " + symbol + sent + " de " + nameOf(receiver, me));
				sendCash(receiver);
			}
		}, 10L);
	}
}
