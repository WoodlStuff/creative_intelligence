package com.noi.image;

import com.google.common.collect.ImmutableRangeSet;
import com.noi.requests.NoiResponse;

import java.util.ArrayList;
import java.util.List;

public class AiImageResponse extends NoiResponse {
    private final List<AiImage> images = new ArrayList<>();

    private AiImageResponse(AiImageRequest request, List<AiImage> images) {
        super(request);
        if (images != null) {
            this.images.addAll(images);
        }
    }

    public static AiImageResponse create(AiImageRequest request, List<AiImage> images) {
        return new AiImageResponse(request, images);
    }

    public List<AiImage> getImages() {
        return images;
    }
}
