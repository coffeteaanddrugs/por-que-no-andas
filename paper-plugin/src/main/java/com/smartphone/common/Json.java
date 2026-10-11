package com.smartphone.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class Json {
	public static final Gson GSON = new GsonBuilder().create();

	private Json() {
	}
}
