package com.noi.image;

import com.noi.AiModel;
import com.noi.language.AiPrompt;
import com.noi.requests.NoiRequest;
import com.noi.tools.FileTools;
import com.noi.tools.SystemEnv;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.net.URL;

public abstract class AiImageService {
    protected final String modelName;

    protected AiImageService(String modelName) {
        this.modelName = modelName;
    }

    public static AiImageService getService(NoiRequest request) {
        String modelName = AiModel.getModel(request);
        if (modelName == null || modelName.isEmpty()) {
            throw new IllegalArgumentException();
        }
        if ("gpt-3".equalsIgnoreCase(modelName) || "gpt-4".equalsIgnoreCase(modelName) ||
                modelName.startsWith("dall-e")) {
            return new OpenAiImageService(modelName);
        } else if ("midjourney".equalsIgnoreCase(modelName)) {
            return new MidJourneyImageService(modelName);
        }

        return null;
    }

    public static File getLocalImageFile(AiImage aiImage) throws IOException {
        if (aiImage.isLocal()) {
            return new File(aiImage.getFilePath());
        }

        // for remote images:
        String fileName = getFileName(aiImage.getId(), aiImage.getUrl());
        String noiPath = SystemEnv.get("NOI_PATH", "/Users/martin/work/tmp/ai-data");
        String imgUrl = String.format("%s/images/%s", noiPath, fileName);

        File file = new File(imgUrl);
        if (file.exists()) {
            return file;
        }

        return null;
    }

    // write a local copy of the image to a root folder and a created folder for the request-UUID
    // file name is the image id and the src file extension
    public static void downloadImage(AiImage image) throws IOException {
        String noiPath = SystemEnv.get("NOI_PATH", "/Users/martin/work/tmp/ai-data");
        String path = String.format("%s/images/", noiPath);
        downloadImage(path, image.getId(), image.getUrl());
    }

    public static void downloadImage(String targetFolder, Long imageId, String srcUrl) throws IOException {
        if (imageId == null) {
            throw new IllegalArgumentException();
        }

        File imageDir = new File(targetFolder);
        imageDir.mkdirs();

        String fileName = getFileName(imageId, srcUrl);

        File out = new File(imageDir, fileName);
        BufferedInputStream in = new BufferedInputStream(new URL(srcUrl).openStream());
        BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(out));
        FileTools.copy(in, bout);
    }

    private static String getFileName(Long imageId, String srcUrl) throws IOException {
        String fileExtension = FileTools.parseFileExtensionFromUrl(srcUrl);
        if (fileExtension == null || fileExtension.isEmpty()) {
            Header contentType = headForContentType(srcUrl);
            fileExtension = FileTools.parseContentTypeForFileExtension(contentType);
        }
        return String.format("%d.%s", imageId, fileExtension);
    }

    private static Header headForContentType(String srcUrl) throws IOException {
        // get the content type from the remote url:
        // -> read response header: content-type: image/jpeg
        CloseableHttpClient client = null;
        CloseableHttpResponse response = null;

        try {
            client = HttpClients.createDefault();

            HttpHead httpHead = new HttpHead(srcUrl);
            httpHead.setHeader("User-Agent", "Noi");
            response = client.execute(httpHead);
            return response.getFirstHeader("content-type");

        } finally {
            if (response != null) {
                response.close();
            }
            if (client != null) {
                client.close();
            }
        }
    }

    public abstract AiImageResponse generateImages(AiImageRequest request) throws IOException;

    public String getModelName() {
        return modelName;
    }

    public static void main(String[] args) throws IOException {

//        Long imageId = 1L;
//        String srcUrl = "https://miro.medium.com/v2/resize:fit:1400/format:webp/1*1uzNKl9Hj2UQQ9lZhFNNkQ.jpeg";
//        downloadImage(targetFolder, imageId, srcUrl);
//
//        srcUrl = "https://www.iwcpinc.com/wp-content/uploads/2020/02/appstore892345324.png";
//        downloadImage(targetFolder, imageId, srcUrl);
//
//        srcUrl = "https://www.decoraid.com/wp-content/uploads/2021/04/outdoor-kitchen-garden-design-ideas-scaled-1916x1150.jpg";
//        downloadImage(targetFolder, imageId, srcUrl);

        // image file names

        String uuid = "2d50af10-2ca2-4ee7-8d0c-b12b6d6b7677";
        String modelName = "a model";
        String promptText = "a prompt";
        Long promptId = -1L;
        AiPrompt prompt = AiPrompt.create(promptId, promptText);
        AiImageRequest aiRequest = AiImageRequest.create(uuid, prompt, modelName);

        String url = "https://oaidalleapiprodscus.blob.core.windows.net/private/org-AtfHy1Nregs9iAEfy8PvgJik/user-7uuDfNQymHYEcMP8Istj6W2y/img-RSuE2QC2jGWibtJYs8QtVVbj.png?st=2024-04-09T18%3A39%3A25Z&se=2024-04-09T20%3A39%3A25Z&sp=r&sv=2021-08-06&sr=b&rscd=inline&rsct=image/png&skoid=6aaadede-4fb3-4698-a8f6-684d7786b067&sktid=a48cca56-e6da-484e-a814-9c849652bcb3&skt=2024-04-09T02%3A18%3A02Z&ske=2024-04-10T02%3A18%3A02Z&sks=b&skv=2021-08-06&sig=iq5%2B9OalXYtYKazSsaasI/NNQ0VOsV2MZlsUXQ1D0AU%3D";

        String fileExtension = FileTools.parseFileExtension(url);
        fileExtension = FileTools.parseFileExtensionFromUrl(url);

        AiImage aiImage = AiImage.create(aiRequest, url, promptText);
        aiImage.setId(3L);

        String fileName = getFileName(aiImage.getId(), aiImage.getUrl());
        File localImageFile = getLocalImageFile(aiImage);

    }
}
