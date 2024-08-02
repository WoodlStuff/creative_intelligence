package com.noi.llm.google;

import com.google.gson.JsonObject;
import com.noi.AiModel;
import com.noi.llm.LLMService;
import com.noi.video.AiVideo;
import com.noi.video.audio.AudioExtractionRequest;
import com.noi.video.audio.AudioSummaryRequest;
import com.noi.video.scenes.VideoSceneSummaryRequest;

import java.io.IOException;

public class GoogleService extends LLMService {
    public static final String NAME = "Google";

    public GoogleService(AiModel model) {
        super(NAME, model);
    }

    @Override
    public JsonObject labelForSameVideoScene(AiVideo.SceneChange sceneChange) throws IOException {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public JsonObject transcribeVideo(AudioExtractionRequest video) {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public JsonObject summarizeVideoScenes(VideoSceneSummaryRequest request) throws IOException {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public JsonObject summarizeVideoSound(AudioSummaryRequest summaryRequest) throws IOException {
        throw new IllegalStateException("implement me");
    }
}