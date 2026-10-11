package com.smartphone.common;

/** Un movimiento de dinero de Cashapp. dir: "in" (recibido) / "out" (enviado). */
public class TransferEntry {
	public String number;
	public String dir;
	public long amount;
	public long time;

	public TransferEntry() {
	}

	public TransferEntry(String number, String dir, long amount, long time) {
		this.number = number;
		this.dir = dir;
		this.amount = amount;
		this.time = time;
	}
}
