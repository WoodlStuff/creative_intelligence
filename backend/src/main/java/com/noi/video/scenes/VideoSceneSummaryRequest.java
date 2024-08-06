package com.noi.video.scenes;

import com.noi.language.AiPrompt;
import com.noi.llm.LLMService;
import com.noi.requests.NoiRequest;
import com.noi.video.AiVideo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class VideoSceneSummaryRequest extends NoiRequest {
    private final AiVideo video;
    private final List<AiVideo.SceneChange> sceneChanges;
    private final List<String> sceneUrls;
    private final List<String> base64Frames;

    private VideoSceneSummaryRequest(AiVideo video, AiPrompt prompt, List<AiVideo.SceneChange> sceneChanges, List<String> sceneUrls, List<String> base64Frames) {
        super(null, prompt, prompt.getModel().getName());
        this.video = video;
        this.sceneChanges = sceneChanges;
        this.sceneUrls = sceneUrls;
        this.base64Frames = base64Frames;
    }

    public static VideoSceneSummaryRequest create(AiVideo video, AiPrompt prompt, List<AiVideo.SceneChange> sceneChanges) throws IOException {
        List<String> base64Frames = new ArrayList<>();
        List<String> sceneUrls = new ArrayList<>();

        if (sceneChanges.size() > 0) {
            // add the starting image
            base64Frames.add(LLMService.imageToBase64String(sceneChanges.get(0).getLastImage()));
            sceneUrls.add(sceneChanges.get(0).getLastImage().getUrl());

            // loop urls and convert the content to base64
            for (AiVideo.SceneChange sceneChange : sceneChanges) {
                base64Frames.add(LLMService.imageToBase64String(sceneChange.getFirstImage()));
                sceneUrls.add(sceneChange.getFirstImage().getUrl());
            }
        }
        return new VideoSceneSummaryRequest(video, prompt, sceneChanges, sceneUrls, base64Frames);
    }

    public AiVideo getVideo() {
        return video;
    }

    public List<AiVideo.SceneChange> getSceneChanges() {
        return sceneChanges;
    }

    public List<String> getSceneUrls() {
        return sceneUrls;
    }

    public List<String> getBase64Frames() {
        return base64Frames;
    }
}
