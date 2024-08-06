package com.noi.video.audio;

import com.noi.AiModel;
import com.noi.language.AiPrompt;
import com.noi.requests.NoiRequest;
import com.noi.video.AiVideo;

import java.io.File;

public class AudioExtractionRequest extends NoiRequest {
    private final AiVideo video;
    private final File audioPath;

    private AudioExtractionRequest(AiVideo video, AiPrompt prompt, AiModel model, File audioPath) {
        super(null, prompt, model.getName());
        this.video = video;
        this.audioPath = audioPath;
    }

    public static AudioExtractionRequest create(AiVideo video, AiPrompt prompt, File audioPath) {
        return new AudioExtractionRequest(video, prompt, prompt.getModel(), audioPath);
    }

    public AiVideo getVideo() {
        return video;
    }

    public File getAudioPath() {
        return audioPath;
    }
}