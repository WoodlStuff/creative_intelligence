package com.noi.requests;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.noi.language.AiImageLabelRequest;
import com.noi.tools.FileTools;
import com.noi.tools.TimeTools;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;

public class AiRequestLogger {
    public static final String LABEL = "imageLabels";
    public static final String FILE_ROOT_FOLDER = "/Users/martin/work/tmp/ai-data/";

    public static void logLabelRequest(String category, NoiRequest request, JsonObject payload) throws IOException {
        ////        System.out.println("OpenAI Label Service: payload:\r\n" + gson.toJson(payloadJson));
        String today = TimeTools.convertToDateStringUTC(Instant.now());

        // <root>/yyyyMMdd/<category>/<model>/<request-uuid>-req.json
        String targetFolder = String.format("%s/%s/%s/%s/", FILE_ROOT_FOLDER, today, category, request.getModel().getName());

        File imageDir = new File(targetFolder);
        imageDir.mkdirs();

        String fileName = String.format("%s-req.json", request.getUUID());

        Gson gson = new Gson();
        File out = new File(imageDir, fileName);
        BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(gson.toJson(payload).getBytes(StandardCharsets.UTF_8)));
        BufferedOutputStream bout = new BufferedOutputStream(Files.newOutputStream(out.toPath()));
        FileTools.copy(in, bout);
    }

    public static void logLabelResponse(String category, NoiRequest request, String responseJson) throws IOException {
        // write to logs:
        // <root>/yyyyMMdd/<category>/<model>/<request-uuid>-res.json
        String today = TimeTools.convertToDateStringUTC(Instant.now());
        String targetFolder = String.format("%s/%s/%s/%s/", FILE_ROOT_FOLDER, today, category, request.getModel().getName());

        File imageDir = new File(targetFolder);
        imageDir.mkdirs();

        String fileName = String.format("%s-res.json", request.getUUID());

        File out = new File(imageDir, fileName);
        BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(responseJson.getBytes(StandardCharsets.UTF_8)));
        BufferedOutputStream bout = new BufferedOutputStream(Files.newOutputStream(out.toPath()));
        FileTools.copy(in, bout);
    }

    public static void logLabelResponseFragment(String category, NoiRequest request, String jsonFragment) throws IOException {
        // write to logs:
        // <root>/yyyyMMdd/<category>/<model>/<request-uuid>-res.json

        // no need to log empty docs
        if (jsonFragment == null || "".equals(jsonFragment)) {
            return;
        }

        String today = TimeTools.convertToDateStringUTC(Instant.now());
        String targetFolder = String.format("%s/%s/%s/%s/", FILE_ROOT_FOLDER, today, category, request.getModel().getName());

        File imageDir = new File(targetFolder);
        imageDir.mkdirs();

        String fileName;

        if (request.getPrompt() != null) {
            fileName = String.format("%s-%d-res.json", request.getUUID(), request.getPrompt().getPromptType());
        } else {
            fileName = String.format("%s-res.json", request.getUUID());
        }

        File out = new File(imageDir, fileName);
        BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(jsonFragment.getBytes(StandardCharsets.UTF_8)));
        BufferedOutputStream bout = new BufferedOutputStream(Files.newOutputStream(out.toPath()));
        FileTools.copy(in, bout);
    }

}
