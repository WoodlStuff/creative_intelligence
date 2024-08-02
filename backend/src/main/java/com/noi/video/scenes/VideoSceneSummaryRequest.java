package com.noi.video.scenes;

import com.noi.language.AiPrompt;
import com.noi.requests.NoiRequest;
import com.noi.video.AiVideo;

import java.util.List;

public class VideoSceneSummaryRequest extends NoiRequest {
    private final AiVideo video;
    private final List<AiVideo.SceneChange> sceneChanges;

    private VideoSceneSummaryRequest(AiVideo video, AiPrompt prompt, List<AiVideo.SceneChange> sceneChanges) {
        super(null, prompt, prompt.getModel().getName());
        this.video = video;
        this.sceneChanges = sceneChanges;
    }

    public static VideoSceneSummaryRequest create(AiVideo video, AiPrompt prompt, List<AiVideo.SceneChange> sceneChanges) {
        return new VideoSceneSummaryRequest(video, prompt, sceneChanges);
    }

    public AiVideo getVideo() {
        return video;
    }

    public List<AiVideo.SceneChange> getSceneChanges() {
        return sceneChanges;
    }
}
