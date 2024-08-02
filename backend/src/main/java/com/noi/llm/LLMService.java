package com.noi.llm;

import com.google.gson.JsonObject;
import com.noi.AiModel;
import com.noi.image.AiImage;
import com.noi.llm.openai.OpenAIService;
import com.noi.requests.NoiRequest;
import com.noi.video.AiVideo;
import com.noi.video.audio.AudioExtractionRequest;
import com.noi.video.audio.AudioSummaryRequest;
import com.noi.video.scenes.VideoSceneSummaryRequest;
import org.apache.commons.io.FileUtils;

import javax.naming.NamingException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class LLMService {
//    private static final Map<String, LLMService> SERVICES = new HashMap<>();

//    public static final LLMService OPEN_AI = new OpenAIService();
//    public static final LLMService GOOGLE = new GoogleService();
//    public static final LLMService MID_JOURNEY = new MidJourneyService();

    private static final Map<AiModel, String> MODEL_URLS = new HashMap<>();

    static {
        MODEL_URLS.put(AiModel.GPT_3, "https://api.openai.com/v1/chat/completions");
        MODEL_URLS.put(AiModel.GPT_4, "https://api.openai.com/v1/chat/completions");
        MODEL_URLS.put(AiModel.DALL_E_3, "https://api.openai.com/v1/images/generations");
        MODEL_URLS.put(AiModel.DALL_E_2, "https://api.openai.com/v1/images/generations");
    }

    protected final String name;
    protected final AiModel model;

    protected LLMService(String name, AiModel model) {
        this.name = name;
        this.model = model;
    }

    public static String getModelUrl(AiModel aiModel) {
        return MODEL_URLS.get(aiModel);
    }

    public static LLMService getService(NoiRequest request) {
        AiModel model = request.getModel();
        if (model != null) {
            if (OpenAIService.NAME.equalsIgnoreCase(model.getServiceName())) {
                return new OpenAIService(model);
            }
        }
        return null;
    }

    protected static String imageToBase64String(AiImage image) throws IOException {
        // todo: make the 'jpg' not hard coded: replace with file ending?
        byte[] fileContent = FileUtils.readFileToByteArray(new File(image.getFilePath()));
        return String.format("data:image/jpg;base64,%s", Base64.getEncoder().encodeToString(fileContent));
    }

    protected byte[] base64EncodeImage(String imagePath) {
        // todo;
        return new byte[0];
    }

    protected byte[] base64EncodeImage(AiImage image) {
        // todo;
        return new byte[0];
    }

    public String getName() {
        return name;
    }

    public abstract JsonObject labelForSameVideoScene(AiVideo.SceneChange sceneChange) throws IOException, SQLException, NamingException;

    /**
     * transcribe the sound in the video, and summarize it.
     *
     * @param video
     * @return
     */
    public abstract JsonObject transcribeVideo(AudioExtractionRequest video) throws IOException;

    public abstract JsonObject summarizeVideoScenes(VideoSceneSummaryRequest request) throws IOException, SQLException, NamingException;

    public abstract JsonObject summarizeVideoSound(AudioSummaryRequest summaryRequest) throws IOException;
}
