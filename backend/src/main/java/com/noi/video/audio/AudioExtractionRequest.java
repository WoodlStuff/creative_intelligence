package com.noi.video.audio;

import com.noi.AiModel;
import com.noi.language.AiPrompt;
import com.noi.requests.NoiRequest;
import com.noi.video.AiVideo;

public class AudioExtractionRequest extends NoiRequest {
    private final AiVideo video;

    private AudioExtractionRequest(AiVideo video, AiPrompt prompt, AiModel model) {
        super(null, prompt, model.getName());
        this.video = video;
    }

    public static AudioExtractionRequest create(AiVideo video, AiPrompt prompt) {
        return new AudioExtractionRequest(video, prompt, prompt.getModel());
    }

    public AiVideo getVideo() {
        return video;
    }
}