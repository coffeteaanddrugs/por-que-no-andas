package com.smartphone.common;

/** Un mensaje de chat. "from" y "to" son números de teléfono. */
public class Message {
	public static final int SENT = 0;
	public static final int DELIVERED = 1;
	public static final int READ = 2;

	public long id;
	public String from;
	public String to;
	public String text;
	public long time;
	public int status;
}
