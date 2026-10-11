package com.smartphone.common;

public final class Numbers {
	private Numbers() {
	}

	/** Devuelve el número en formato 555-1234, o null si no es válido (7 dígitos). */
	public static String normalize(String s) {
		if (s == null) {
			return null;
		}
		String digits = s.replaceAll("\\D", "");
		if (digits.length() != 7) {
			return null;
		}
		return digits.substring(0, 3) + "-" + digits.substring(3);
	}
}
