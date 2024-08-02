package com.noi.video.scenes;

import com.noi.language.AiPrompt;
import com.noi.requests.NoiRequest;
import com.noi.video.AiVideo;

import java.util.Objects;

public class SceneChangeRequest extends NoiRequest {
    private final AiVideo video;
    private final AiVideo.SceneChange sceneChange;

    protected SceneChangeRequest(AiVideo video, AiVideo.SceneChange sceneChange, AiPrompt prompt) {
        super(null, prompt, prompt.getModel().getName());
        this.video = video;
        this.sceneChange = sceneChange;
    }

    public static SceneChangeRequest create(AiVideo video, AiVideo.SceneChange sceneChange, AiPrompt prompt) {
        return new SceneChangeRequest(video, sceneChange, prompt);
    }

    public AiVideo getVideo() {
        return video;
    }

    public AiVideo.SceneChange getSceneChange() {
        return sceneChange;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SceneChangeRequest that = (SceneChangeRequest) o;
        return video.equals(that.video) && sceneChange.equals(that.sceneChange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), video, sceneChange);
    }
}
