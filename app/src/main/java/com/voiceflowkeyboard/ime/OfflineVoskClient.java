package com.voiceflowkeyboard.ime;

import android.content.Context;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class OfflineVoskClient {
    static final int SAMPLE_RATE = 16000;
    private static final String MODEL_ID = "vosk-model-small-en-us-0.15";
    private static final String MODEL_URL = "https://alphacephei.com/vosk/models/" + MODEL_ID + ".zip";

    private OfflineVoskClient() {
    }

    static boolean isModelReady(Context context) {
        File dir = modelDir(context);
        return new File(dir, "conf").isDirectory()
                && new File(dir, "am").isDirectory()
                && new File(dir, "graph").isDirectory();
    }

    static void ensureModel(Context context) throws IOException {
        if (isModelReady(context)) {
            return;
        }
        File root = modelRoot(context);
        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("Could not create offline model directory.");
        }
        File zip = new File(context.getCacheDir(), MODEL_ID + ".zip");
        download(MODEL_URL, zip);
        unzip(zip, root);
        if (!zip.delete()) {
            zip.deleteOnExit();
        }
        if (!isModelReady(context)) {
            throw new IOException("Offline model download did not finish correctly.");
        }
    }

    static String transcribePcm(Context context, File pcmFile) throws Exception {
        ensureModel(context);
        Model model = null;
        Recognizer recognizer = null;
        try {
            model = new Model(modelDir(context).getAbsolutePath());
            recognizer = new Recognizer(model, SAMPLE_RATE);
            try (InputStream in = new BufferedInputStream(new FileInputStream(pcmFile))) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    recognizer.acceptWaveForm(buffer, read);
                }
            }
            JSONObject json = new JSONObject(recognizer.getFinalResult());
            String text = json.optString("text", "").trim();
            if (!text.isEmpty()) {
                return text;
            }
            throw new IOException("Offline transcription did not include text.");
        } finally {
            if (recognizer != null) {
                recognizer.close();
            }
            if (model != null) {
                model.close();
            }
        }
    }

    static List<String> defaultTranscriptionModels() {
        List<String> models = new ArrayList<>();
        models.add(MODEL_ID);
        return models;
    }

    private static File modelRoot(Context context) {
        return new File(context.getFilesDir(), "vosk");
    }

    private static File modelDir(Context context) {
        return new File(modelRoot(context), MODEL_ID);
    }

    private static void download(String url, File destination) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(120000);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("Offline model download failed (" + code + ").");
        }
        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             FileOutputStream fileOut = new FileOutputStream(destination);
             BufferedOutputStream out = new BufferedOutputStream(fileOut)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static void unzip(File zip, File destinationRoot) throws IOException {
        String rootPath = destinationRoot.getCanonicalPath() + File.separator;
        try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = in.getNextEntry()) != null) {
                File outFile = new File(destinationRoot, entry.getName());
                String outPath = outFile.getCanonicalPath();
                if (!outPath.startsWith(rootPath)) {
                    throw new IOException("Blocked unsafe model zip path.");
                }
                if (entry.isDirectory()) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        throw new IOException("Could not create model directory.");
                    }
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Could not create model directory.");
                    }
                    try (FileOutputStream fileOut = new FileOutputStream(outFile);
                         BufferedOutputStream out = new BufferedOutputStream(fileOut)) {
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                }
                in.closeEntry();
            }
        }
    }
}
