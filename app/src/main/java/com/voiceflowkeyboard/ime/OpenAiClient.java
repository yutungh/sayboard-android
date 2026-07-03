package com.voiceflowkeyboard.ime;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OpenAiClient {
    private static final String MODELS_URL = "https://api.openai.com/v1/models";
    private static final String TRANSCRIPTIONS_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String RESPONSES_URL = "https://api.openai.com/v1/responses";
    private static final Pattern WHOLE_MARKDOWN_FENCE = Pattern.compile(
            "\\A```(?:[A-Za-z0-9_-]+)?[ \\t]*(?:\\r?\\n)?([\\s\\S]*?)(?:\\r?\\n)?```\\s*\\z"
    );
    private static final String OUTPUT_CONTRACT = "\n\nOutput contract:\n"
            + "- Return plain text only.\n"
            + "- Do not wrap the answer in Markdown code fences, quotes, or labels.\n"
            + "- Do not add any text before or after the transformed text.";

    private OpenAiClient() {
    }

    static String transcribe(Context context, File audioFile) throws Exception {
        String apiKey = requiredApiKey(context);
        String model = nonEmpty(Prefs.transcriptionModel(context), "gpt-4o-transcribe");
        String boundary = "VoiceFlowKeyboardBoundary" + System.currentTimeMillis();

        HttpURLConnection connection = (HttpURLConnection) new URL(TRANSCRIPTIONS_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(120000);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream out = connection.getOutputStream()) {
            writePart(out, boundary, "model", model);
            writeFilePart(out, boundary, "file", audioFile, "voiceflow-keyboard.m4a", "audio/mp4");
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }

        String response = readResponse(connection);
        JSONObject json = new JSONObject(response);
        if (!json.optString("text").isEmpty()) {
            return json.getString("text").trim();
        }
        throw new IOException("Transcription response did not include text.");
    }

    static String transform(Context context, String transcript, String preset) throws Exception {
        String apiKey = requiredApiKey(context);
        String model = nonEmpty(Prefs.transformModel(context), "gpt-5.5-mini");
        String prompt = nonEmpty(Prefs.promptForPreset(context, preset), Prefs.defaultPromptForPreset(preset));
        JSONObject baseBody = new JSONObject()
                .put("model", model)
                .put("input", prompt + OUTPUT_CONTRACT + "\n\nTranscript:\n" + transcript);

        JSONObject body = new JSONObject(baseBody.toString());
        boolean hasLatencyOptions = addLatencyOptions(body, model, preset, prompt);
        String response;
        try {
            response = sendResponsesRequest(apiKey, body);
        } catch (IOException e) {
            if (!hasLatencyOptions || !looksLikeLatencyOptionRejection(e)) {
                throw e;
            }
            response = sendResponsesRequest(apiKey, baseBody);
        }

        JSONObject json = new JSONObject(response);
        String parsed = findOutputText(json);
        if (!parsed.isEmpty()) {
            return stripWholeOutputWrappers(parsed).trim();
        }
        throw new IOException("Transform response did not include output text.");
    }

    static List<String> listModels(String apiKey) throws Exception {
        String trimmedKey = apiKey == null ? "" : apiKey.trim();
        if (trimmedKey.isEmpty()) {
            throw new IllegalStateException("Add your OpenAI API key first.");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(MODELS_URL).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Authorization", "Bearer " + trimmedKey);

        String response = readResponse(connection);
        JSONObject json = new JSONObject(response);
        JSONArray data = json.optJSONArray("data");
        List<String> models = new ArrayList<>();
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                String id = data.optJSONObject(i) == null ? "" : data.optJSONObject(i).optString("id", "");
                if (!id.trim().isEmpty()) {
                    models.add(id.trim());
                }
            }
        }
        Collections.sort(models, modelComparator());
        return models;
    }

    static List<String> transcriptionModelsFrom(List<String> models) {
        List<String> filtered = new ArrayList<>();
        for (String model : models) {
            String lower = model.toLowerCase(Locale.US);
            if (lower.contains("transcribe") || "whisper-1".equals(lower)) {
                filtered.add(model);
            }
        }
        return withFallbacks(filtered, defaultTranscriptionModels());
    }

    static List<String> transformModelsFrom(List<String> models) {
        List<String> filtered = new ArrayList<>();
        for (String model : models) {
            String lower = model.toLowerCase(Locale.US);
            if (lower.startsWith("gpt-")
                    && !lower.contains("transcribe")
                    && !lower.contains("tts")
                    && !lower.contains("realtime")
                    && !lower.contains("audio")
                    && !lower.contains("image")
                    && !lower.contains("embedding")
                    && !lower.contains("moderation")) {
                filtered.add(model);
            }
        }
        return withFallbacks(filtered, defaultTransformModels());
    }

    static List<String> defaultTranscriptionModels() {
        List<String> models = new ArrayList<>();
        models.add("gpt-4o-transcribe");
        models.add("gpt-4o-mini-transcribe");
        models.add("gpt-4o-transcribe-diarize");
        models.add("whisper-1");
        return models;
    }

    static List<String> defaultTransformModels() {
        List<String> models = new ArrayList<>();
        models.add("gpt-5.5-mini");
        models.add("gpt-5.5");
        models.add("gpt-5.4-mini");
        models.add("gpt-5.4");
        models.add("gpt-5-mini");
        models.add("gpt-5");
        models.add("gpt-4.1");
        models.add("gpt-4.1-mini");
        return models;
    }

    private static List<String> withFallbacks(List<String> models, List<String> fallbacks) {
        List<String> merged = new ArrayList<>();
        for (String fallback : fallbacks) {
            if (!containsIgnoreCase(merged, fallback)) {
                merged.add(fallback);
            }
        }
        for (String model : models) {
            if (!containsIgnoreCase(merged, model)) {
                merged.add(model);
            }
        }
        return merged;
    }

    private static boolean containsIgnoreCase(List<String> values, String value) {
        for (String existing : values) {
            if (existing.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static Comparator<String> modelComparator() {
        return (left, right) -> {
            int priority = Integer.compare(modelPriority(left), modelPriority(right));
            if (priority != 0) {
                return priority;
            }
            return left.compareToIgnoreCase(right);
        };
    }

    private static int modelPriority(String model) {
        String lower = model.toLowerCase(Locale.US);
        if (lower.startsWith("gpt-5.5-mini")) {
            return 0;
        }
        if (lower.startsWith("gpt-5.5")) {
            return 1;
        }
        if (lower.startsWith("gpt-5") && lower.contains("mini")) {
            return 2;
        }
        if (lower.startsWith("gpt-5")) {
            return 3;
        }
        if (lower.startsWith("gpt-4")) {
            return 4;
        }
        if (lower.contains("transcribe")) {
            return 5;
        }
        if (lower.startsWith("whisper")) {
            return 6;
        }
        return 7;
    }

    private static String sendResponsesRequest(String apiKey, JSONObject body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(RESPONSES_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(120000);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json");

        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        return readResponse(connection);
    }

    private static boolean addLatencyOptions(JSONObject body, String model, String preset, String prompt) throws Exception {
        String normalized = model.toLowerCase(Locale.US);
        boolean gpt5Model = normalized.startsWith("gpt-5");
        boolean reasoningModel = gpt5Model || normalized.startsWith("o");

        body.put("prompt_cache_key", promptCacheKey(model, preset, prompt));
        if (gpt5Model) {
            body.put("prompt_cache_retention", "24h");
            body.put("text", new JSONObject().put("verbosity", "low"));
        }
        if (reasoningModel) {
            body.put("reasoning", new JSONObject().put("effort", gpt5Model ? "none" : "low"));
        }
        return true;
    }

    private static String promptCacheKey(String model, String preset, String prompt) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest((model + "\n" + preset + "\n" + prompt).getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder("vk-");
        for (int i = 0; i < 12 && i < hash.length; i++) {
            builder.append(String.format(Locale.US, "%02x", hash[i]));
        }
        return builder.toString();
    }

    private static boolean looksLikeLatencyOptionRejection(IOException e) {
        String message = String.valueOf(e.getMessage()).toLowerCase(Locale.US);
        return message.contains("unsupported")
                || message.contains("unknown parameter")
                || message.contains("unrecognized")
                || message.contains("invalid parameter")
                || message.contains("prompt_cache")
                || message.contains("reasoning")
                || message.contains("verbosity");
    }

    private static String stripWholeOutputWrappers(String text) {
        String result = text == null ? "" : text.trim();
        Matcher fence = WHOLE_MARKDOWN_FENCE.matcher(result);
        if (fence.matches()) {
            result = fence.group(1).trim();
        }
        return result;
    }

    private static String requiredApiKey(Context context) {
        String apiKey = Prefs.openAiApiKey(context);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Add your OpenAI API key in VoiceFlow Keyboard settings.");
        }
        return apiKey.trim();
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static void writePart(OutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(
            OutputStream out,
            String boundary,
            String name,
            File file,
            String filename,
            String contentType
    ) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String readResponse(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = readAll(stream);
        if (code < 200 || code >= 300) {
            throw new IOException("OpenAI request failed (" + code + "): " + body);
        }
        return body;
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private static String findOutputText(Object value) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String type = object.optString("type");
            if ("output_text".equals(type) && object.has("text")) {
                return object.optString("text", "");
            }
            JSONArray names = object.names();
            if (names == null) {
                return "";
            }
            for (int i = 0; i < names.length(); i++) {
                String found = findOutputText(object.opt(names.optString(i)));
                if (!found.isEmpty()) {
                    return found;
                }
            }
            return "";
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            ByteArrayOutputStream joined = new ByteArrayOutputStream();
            for (int i = 0; i < array.length(); i++) {
                String found = findOutputText(array.opt(i));
                if (!found.isEmpty()) {
                    if (joined.size() > 0) {
                        joined.write('\n');
                    }
                    byte[] bytes = found.getBytes(StandardCharsets.UTF_8);
                    joined.write(bytes, 0, bytes.length);
                }
            }
            return joined.toString();
        }
        return "";
    }
}
