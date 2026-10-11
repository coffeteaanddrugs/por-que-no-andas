package com.smartphone.paper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/** Plugin de Paper: es el "servidor" del teléfono. Los jugadores usan el mod Smartphone en su cliente. */
public final class SmartphonePlugin extends JavaPlugin implements PluginMessageListener, Listener {
	private static SmartphonePlugin instance;
	private int saveCounter;

	public static SmartphonePlugin get() {
		return instance;
	}

	@Override
	public void onEnable() {
		instance = this;
		Handler.data = PhoneData.load(getDataFolder().toPath().resolve("smartphone.json"));
		Log.info("Datos cargados ({} números)", Handler.data.numbers.size());

		getServer().getMessenger().registerIncomingPluginChannel(this, Net.C2S, this);
		getServer().getMessenger().registerOutgoingPluginChannel(this, Net.S2C);
		getServer().getPluginManager().registerEvents(this, this);

		// una vez por segundo: timbres sin respuesta y guardado periódico
		getServer().getScheduler().runTaskTimer(this, () -> {
			Handler.calls.tick();
			if (++saveCounter >= 30) {
				saveCounter = 0;
				if (Handler.data.isDirty()) {
					Handler.data.save();
				}
			}
		}, 20L, 20L);

		Cash.init(this);
		VoiceBridge.init(this);
	}

	@Override
	public void onDisable() {
		Handler.calls.endAll();
		if (Handler.data != null) {
			Handler.data.save();
		}
		getServer().getMessenger().unregisterIncomingPluginChannel(this);
		getServer().getMessenger().unregisterOutgoingPluginChannel(this);
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!Net.C2S.equals(channel)) {
			return;
		}
		String[] parts = Net.read(message);
		if (parts == null) {
			return;
		}
		Handler.handle(player, parts[0], parts[1]);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Handler.calls.onDisconnect(event.getPlayer());
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!command.getName().equalsIgnoreCase("phone")) {
			return false;
		}
		if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
			if (!sender.hasPermission("smartphone.give")) {
				sender.sendMessage("No tienes permiso.");
				return true;
			}
			Player target = args.length >= 2 ? getServer().getPlayerExact(args[1]) : (sender instanceof Player p ? p : null);
			if (target == null) {
				sender.sendMessage("Jugador no encontrado. Uso: /phone give <jugador>");
				return true;
			}
			var leftovers = target.getInventory().addItem(PhoneItem.create());
			leftovers.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
			sender.sendMessage("Teléfono entregado a " + target.getName());
			return true;
		}
		if (!(sender instanceof Player player)) {
			sender.sendMessage("Solo los jugadores tienen teléfono.");
			return true;
		}
		if (args.length == 1 && args[0].equalsIgnoreCase("number")) {
			player.sendMessage("Tu número de teléfono es " + Handler.data.numberFor(player.getUniqueId()));
			return true;
		}
		player.sendMessage("Uso: /phone number | /phone give [jugador]");
		return true;
	}
}
