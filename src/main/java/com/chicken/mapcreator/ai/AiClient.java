package com.chicken.mapcreator.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/** Zapytania HTTP do Claude (Anthropic), ChatGPT (OpenAI) i Gemini (Google). Asynchroniczne, z limitem czasu. */
public final class AiClient {
	public record Message(String role, String text) {}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
	private static final Pattern SAFE_MODEL = Pattern.compile("[A-Za-z0-9._:\\-]{1,80}");

	private AiClient() {}

	public static CompletableFuture<String> ask(String provider, String key, String model, String system, List<Message> history) {
		HttpRequest request;
		try {
			request = build(provider, key, model, system, history);
		} catch (RuntimeException e) {
			return CompletableFuture.failedFuture(e);
		}
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(response -> {
			if (response.statusCode() / 100 != 2) {
				String body = response.body();
				throw new IllegalStateException("HTTP " + response.statusCode() + ": " + body.substring(0, Math.min(300, body.length())));
			}
			return extract(provider, response.body());
		});
	}

	/** Lista modeli dostepnych dla danego klucza (do wyboru w menu). */
	public static CompletableFuture<List<String>> listModels(String provider, String key) {
		HttpRequest.Builder req;
		switch (provider) {
			case "openai" -> req = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/models")).header("Authorization", "Bearer " + key);
			case "gemini" -> req = HttpRequest.newBuilder(URI.create("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200"))
					.header("x-goog-api-key", key);
			default -> req = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/models?limit=100"))
					.header("x-api-key", key).header("anthropic-version", "2023-06-01");
		}
		HttpRequest request = req.timeout(Duration.ofSeconds(30)).GET().build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(response -> {
			if (response.statusCode() / 100 != 2) {
				String body = response.body();
				throw new IllegalStateException("HTTP " + response.statusCode() + ": " + body.substring(0, Math.min(200, body.length())));
			}
			JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
			java.util.List<String> ids = new java.util.ArrayList<>();
			if (provider.equals("gemini")) {
				for (JsonElement el : root.getAsJsonArray("models")) {
					JsonObject m = el.getAsJsonObject();
					boolean chat = m.has("supportedGenerationMethods")
							&& m.getAsJsonArray("supportedGenerationMethods").toString().contains("generateContent");
					if (chat) {
						ids.add(m.get("name").getAsString().replace("models/", ""));
					}
				}
			} else {
				for (JsonElement el : root.getAsJsonArray("data")) {
					String id = el.getAsJsonObject().get("id").getAsString();
					if (!provider.equals("openai") || id.startsWith("gpt") || id.startsWith("o") || id.startsWith("chatgpt")) {
						ids.add(id);
					}
				}
			}
			// najpierw szybkie i tanie modele ("flash-lite", "flash"), na koncu duze ("pro"), bo te czesto nie maja darmowego limitu
			ids.sort(java.util.Comparator.comparingInt((String id) -> id.contains("flash-lite") || id.contains("haiku") || id.contains("mini") ? 0
					: id.contains("flash") || id.contains("sonnet") ? 1 : 2).thenComparing(java.util.Comparator.naturalOrder()));
			return ids;
		});
	}

	private static HttpRequest build(String provider, String key, String model, String system, List<Message> history) {
		if (!SAFE_MODEL.matcher(model).matches()) {
			throw new IllegalArgumentException("Bad model name");
		}
		JsonObject body = new JsonObject();
		HttpRequest.Builder req;
		switch (provider) {
			case "openai" -> {
				req = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
						.header("Authorization", "Bearer " + key);
				body.addProperty("model", model);
				JsonArray messages = new JsonArray();
				messages.add(msg("system", system));
				for (Message m : history) {
					messages.add(msg(m.role(), m.text()));
				}
				body.add("messages", messages);
				JsonObject format = new JsonObject();
				format.addProperty("type", "json_object");
				body.add("response_format", format);
			}
			case "gemini" -> {
				req = HttpRequest.newBuilder(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
						+ URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent"))
						.header("x-goog-api-key", key);
				JsonObject sys = new JsonObject();
				JsonArray sysParts = new JsonArray();
				JsonObject sysText = new JsonObject();
				sysText.addProperty("text", system);
				sysParts.add(sysText);
				sys.add("parts", sysParts);
				body.add("systemInstruction", sys);
				JsonArray contents = new JsonArray();
				for (Message m : history) {
					JsonObject c = new JsonObject();
					c.addProperty("role", m.role().equals("assistant") ? "model" : "user");
					JsonArray parts = new JsonArray();
					JsonObject part = new JsonObject();
					part.addProperty("text", m.text());
					parts.add(part);
					c.add("parts", parts);
					contents.add(c);
				}
				body.add("contents", contents);
				JsonObject gen = new JsonObject();
				gen.addProperty("responseMimeType", "application/json");
				body.add("generationConfig", gen);
			}
			default -> {
				req = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
						.header("x-api-key", key).header("anthropic-version", "2023-06-01");
				body.addProperty("model", model);
				body.addProperty("max_tokens", 1500);
				body.addProperty("system", system);
				JsonArray messages = new JsonArray();
				for (Message m : history) {
					messages.add(msg(m.role(), m.text()));
				}
				body.add("messages", messages);
			}
		}
		return req.timeout(Duration.ofSeconds(120)).header("content-type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
	}

	private static JsonObject msg(String role, String text) {
		JsonObject o = new JsonObject();
		o.addProperty("role", role);
		o.addProperty("content", text);
		return o;
	}

	private static String extract(String provider, String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		StringBuilder out = new StringBuilder();
		switch (provider) {
			case "openai" -> out.append(root.getAsJsonArray("choices").get(0).getAsJsonObject()
					.getAsJsonObject("message").get("content").getAsString());
			case "gemini" -> {
				JsonArray parts = root.getAsJsonArray("candidates").get(0).getAsJsonObject()
						.getAsJsonObject("content").getAsJsonArray("parts");
				for (JsonElement p : parts) {
					if (p.getAsJsonObject().has("text")) {
						out.append(p.getAsJsonObject().get("text").getAsString());
					}
				}
			}
			default -> {
				for (JsonElement block : root.getAsJsonArray("content")) {
					JsonObject b = block.getAsJsonObject();
					if (b.has("text")) {
						out.append(b.get("text").getAsString());
					}
				}
			}
		}
		return out.toString();
	}
}
