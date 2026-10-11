package com.smartphone.paper;

import java.util.logging.Level;

/** Pequeño ayudante de logs (acepta mensajes con {}). */
public final class Log {
	private Log() {
	}

	private static String fmt(String msg, Object... args) {
		StringBuilder sb = new StringBuilder();
		int arg = 0;
		int i = 0;
		while (i < msg.length()) {
			if (msg.startsWith("{}", i) && arg < args.length && !(args[arg] instanceof Throwable)) {
				sb.append(args[arg++]);
				i += 2;
			} else {
				sb.append(msg.charAt(i++));
			}
		}
		return sb.toString();
	}

	private static Throwable lastThrowable(Object[] args) {
		return args.length > 0 && args[args.length - 1] instanceof Throwable t ? t : null;
	}

	public static void info(String msg, Object... args) {
		SmartphonePlugin.get().getLogger().info(fmt(msg, args));
	}

	public static void error(String msg, Object... args) {
		Throwable t = lastThrowable(args);
		if (t != null) {
			SmartphonePlugin.get().getLogger().log(Level.SEVERE, fmt(msg, args), t);
		} else {
			SmartphonePlugin.get().getLogger().severe(fmt(msg, args));
		}
	}
}
