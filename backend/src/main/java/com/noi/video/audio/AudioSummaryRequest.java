package com.noi.video.audio;

import com.noi.language.AiPrompt;
import com.noi.requests.NoiRequest;
import com.noi.video.AiVideo;

public class AudioSummaryRequest extends NoiRequest {
    private final AiVideo video;
    private final String text;

    private AudioSummaryRequest(AiVideo video, AiPrompt prompt, String text) {
        super(null, prompt, prompt.getModel().getName());
        this.video = video;
        this.text = text;
    }

    public static AudioSummaryRequest create(AiVideo video, AiPrompt prompt, String text) {
        return new AudioSummaryRequest(video, prompt, text);
    }

    public AiVideo getVideo() {
        return video;
    }

    public String getText() {
        return text;
    }
}