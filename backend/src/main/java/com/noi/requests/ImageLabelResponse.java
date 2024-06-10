package com.noi.requests;

import com.noi.image.label.AiImageLabel;
import com.noi.language.AiImageLabelRequest;

import java.util.ArrayList;
import java.util.List;

public class ImageLabelResponse extends NoiResponse {
    private final List<AiImageLabel> imageLabels = new ArrayList<>();

    protected ImageLabelResponse(AiImageLabelRequest request) {
        super(request);
    }

    private ImageLabelResponse(AiImageLabelRequest request, List<AiImageLabel> imageLabels) {
        super(request);
        this.imageLabels.addAll(imageLabels);

        if (imageLabels.size() > 0 && imageLabels.get(0).isErrorResponse()) {
            this.errorCode = imageLabels.get(0).getStatusCode();
            this.errorMessage = imageLabels.get(0).getReason();
        }
    }

    public static ImageLabelResponse create(AiImageLabelRequest request, List<AiImageLabel> imageLabels) {
        return new ImageLabelResponse(request, imageLabels);
    }

    public static ImageLabelResponse create(AiImageLabelRequest request) {
        List<AiImageLabel> imageLabels = new ArrayList<>();
        return new ImageLabelResponse(request, imageLabels);
    }

    public List<AiImageLabel> getImageLabels() {
        return imageLabels;
    }
}
