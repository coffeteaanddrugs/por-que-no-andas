package com.smartphone.common;

/** Una línea del historial de llamadas. dir: "in"/"out". result: answered, missed, declined, unavailable, busy. */
public class CallLogEntry {
	public String number;
	public String dir;
	public long time;
	public int duration;
	public String result;

	public CallLogEntry() {
	}

	public CallLogEntry(String number, String dir, long time, int duration, String result) {
		this.number = number;
		this.dir = dir;
		this.time = time;
		this.duration = duration;
		this.result = result;
	}
}
