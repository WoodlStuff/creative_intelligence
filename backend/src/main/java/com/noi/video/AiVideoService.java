package com.noi.video;

import com.noi.AiModel;
import com.noi.image.*;
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

public class AiVideoService {
    protected final String modelName;

    protected AiVideoService(String modelName) {
        this.modelName = modelName;
    }

    public static File getLocalVideoFile(AiVideo aiVideo) throws IOException {
        if (aiVideo.isLocal()) {
            return new File(aiVideo.getFilePath());
        }

        // for remote videos:
        String fileName = getFileName(aiVideo.getId(), aiVideo.getUrl());
        String noiPath = SystemEnv.get("NOI_PATH", "/Users/martin/work/tmp/ai-data");
        String imgUrl = String.format("%s/videos/%s", noiPath, fileName);

        File file = new File(imgUrl);
        if (file.exists()) {
            return file;
        }

        return null;
    }

    // write a local copy of the image to a root folder and a created folder for the request-UUID
    // file name is the image id and the src file extension
    public static void downloadVideo(AiVideo video) throws IOException {
        String noiPath = SystemEnv.get("NOI_PATH", "/Users/martin/work/tmp/ai-data");
        String path = String.format("%s/videos/", noiPath);
        downloadVideo(path, video.getId(), video.getUrl());
    }

    public static void downloadVideo(String targetFolder, Long videoId, String srcUrl) throws IOException {
        if (videoId == null) {
            throw new IllegalArgumentException();
        }

        File imageDir = new File(targetFolder);
        imageDir.mkdirs();

        String fileName = getFileName(videoId, srcUrl);

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

    public String getModelName() {
        return modelName;
    }

    public static void main(String[] args) throws IOException {

    }
}
