package com.noi.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import org.apache.http.Header;
import org.apache.http.entity.ContentType;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 *
 */
public class FileTools {
    public static StringBuilder buildCSVLine(String[] tokens) throws IOException {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            csv.append(tokens[i]);
            if (i < tokens.length - 1) {
                csv.append(",");
            }
        }
        return csv;
    }

    public static StringBuilder buildQuotedCSVLine(String[] tokens) throws IOException {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            csv.append("\"").append(tokens[i]).append("\"");
            if (i < tokens.length - 1) {
                csv.append(",");
            }
        }
        return csv;
    }

    public static int writeCSV(Map<Integer, String> lines, String fileUrl) throws IOException {
        int writeCount = 0;
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileUrl)));
        for (Map.Entry<Integer, String> entry : lines.entrySet()) {
            writer.write(entry.getKey());
            writer.write(",");
            writer.write(entry.getValue());
            writer.newLine();
            writeCount++;
        }
        writer.close();
        return writeCount;
    }

    public static void writeToFile(JsonObject json, File file) throws IOException {
        Gson gson = new Gson();
        FileWriter out = new FileWriter(file);
        out.write(gson.toJson(json));
        out.flush();
        out.close();
    }

    public static long writeToFile(InputStream content, String fileUrl) throws IOException {
        return writeToFile(content, fileUrl, null);
    }

    public static long writeToFile(InputStream content, String fileUrl, String encoding) throws IOException {
        BufferedReader reader = null;
        BufferedWriter writer = null;
        long rowCount = 0L;
        try {
            if ("gzip".equals(encoding)) {
                GZIPInputStream gzis = new GZIPInputStream(content);
                reader = new BufferedReader(new InputStreamReader(gzis));
            } else {
                reader = new BufferedReader(new InputStreamReader(content, "utf-8"));
            }

            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileUrl), "utf-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
                rowCount++;
            }
            writer.flush();
        } finally {
            FileTools.close(writer);
            FileTools.close(reader);
        }

        return rowCount;
    }

    public static BufferedReader getFileReader(String fileURL) throws FileNotFoundException, UnsupportedEncodingException {
        return getFileReader(fileURL, "utf-8");
    }

    public static BufferedReader getFileReader(File file) throws FileNotFoundException, UnsupportedEncodingException {
        return getFileReader(file, "utf-8");
    }

    public static BufferedReader getFileReader(File file, String encoding) throws FileNotFoundException, UnsupportedEncodingException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding));
    }

    public static BufferedReader getFileReader(String fileURL, String encoding) throws FileNotFoundException, UnsupportedEncodingException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(fileURL), encoding));
    }

    public static String[] splitLine(String line, char separator, boolean ignoreQuotes) {
        StringBuilder token = new StringBuilder();
        List<String> tokens = new LinkedList<String>();
        boolean hasQuoteOpen = false;

        for (char c : line.toCharArray()) {
            if (c == separator && !hasQuoteOpen) {
                tokens.add(token.toString());
                token = new StringBuilder();
            } else if (hasQuoteOpen && c == '\"') {
                hasQuoteOpen = false;
            } else if (!ignoreQuotes && c == '\"') {
                hasQuoteOpen = true;
            } else if (c == separator && hasQuoteOpen) {
                token.append(c);
            } else {
                token.append(c);
            }
        }
        if (token.length() > 0) {
            tokens.add(token.toString().trim());
        }

        String[] result = new String[tokens.size()];
        tokens.toArray(result);
        return result;
    }

    public static InputStream getInputStream(String fileUrl) throws IOException {
        return new FileInputStream(fileUrl);
    }

    public static InputStream getInputStream(File f) throws IOException {
        return new FileInputStream(f);
    }

    public static BufferedWriter getFileWriter(String fileURL) throws IOException {
        return getFileWriter(fileURL, false, "utf-8");
    }

    public static BufferedWriter getFileWriter(String fileURL, String encoding) throws IOException {
        return getFileWriter(fileURL, false, encoding);
    }

    public static BufferedWriter getFileWriter(String fileURL, boolean append) throws IOException {
        return getFileWriter(fileURL, append, "utf-8");
    }

    public static BufferedWriter getFileWriter(String fileURL, boolean append, String encoding) throws IOException {
        File f = new File(fileURL);
        if (!f.exists()) {
            f.createNewFile();
        }
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileURL, append), encoding));
    }

    public static void writeLine(BufferedWriter writer, String[] tokens) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {

            if (tokens[i] == null) {
                System.out.println("Warning: no content for token; line so far: " + line);
                continue;
            }
            if (tokens[i].contains(",")) {
                line.append("\"");
            }
            line.append(tokens[i]);
            if (tokens[i].contains(",")) {
                line.append("\"");
            }

            if (i < tokens.length - 1) {
                line.append(",");
            }
        }

        writer.write(line.toString());
        writer.newLine();
        writer.flush();
    }

    public static void writeLine(BufferedWriter writer, LinkedList<String> tokens) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            line.append(tokens.get(i));
            if (i < tokens.size() - 1) {
                line.append(",");
            }
        }

        writer.write(line.toString());
        writer.newLine();
    }

    public static void writeLine(BufferedWriter writer, String tokens) throws IOException {
        writer.write(tokens);
        writer.newLine();
    }

    public static List<Integer> buildIdList(String fileUri) throws SQLException, IOException {
        int lineCount = 0;
        List<Integer> ids = new ArrayList<Integer>();

        BufferedReader reader = getFileReader(fileUri);
        String line;

        while ((line = reader.readLine()) != null) {
            lineCount++;
            String[] tokens = line.trim().split(",");

            String arrivalSource;
            if (tokens.length > 0) {
                if (tokens.length == 3) {
                    arrivalSource = tokens[1].trim();
                } else {
                    arrivalSource = tokens[0].trim();
                }
            } else {
                arrivalSource = line.trim();
            }

            try {
                ids.add(Integer.valueOf(arrivalSource));
            } catch (RuntimeException e) {
                System.out.println("ERROR: unable to parse for [" + line + "]");
            }
        }

        System.out.println("read " + lineCount + " original lines");
        return ids;
    }

    public static void writeIds(List<Integer> ids, String fileUri) throws IOException {
        FileWriter writer = new FileWriter(fileUri);
        for (Integer id : ids) {
            writer.write(id + "\r\n");
        }

        writer.flush();
        writer.close();
    }

    public static void close(Writer writer) {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void close(OutputStream out) {
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void close(Reader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void close(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String bumpyCase(String phrase) {
        boolean liftChar = true;
        StringBuilder result = new StringBuilder();
        for (char c : phrase.toCharArray()) {
            if (c == ' ') {
                liftChar = true;
            } else if (liftChar) {
                liftChar = false;
                result.append(Character.toUpperCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public static void close(JsonReader in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void copy(String from, String to) throws IOException {
        File destination = new File(to);

        if (destination.createNewFile()) {
            OutputStream writer = new FileOutputStream(destination);
            InputStream reader = new FileInputStream(from);

            copy(reader, writer);

            writer.close();
            reader.close();
        } else {
            throw new IOException("unable to create new file");
        }
    }

    public static void copy(InputStream reader, OutputStream writer) throws IOException {
        byte[] b = new byte[1024];
        int count;
        while ((count = reader.read(b)) > 0) {
            writer.write(b, 0, count);
            writer.flush();
        }
    }

    public static ByteArrayOutputStream readIntoBuffer(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[1024];
        int count;
        while ((count = in.read()) > 0) {
            out.write(b, 0, count);
            out.flush();
        }

        in.close();

        return out;
    }

    public static String parseHostFromUrl(String url, boolean preserveProtocol) {
        if (url == null) {
            return null;
        }

        int pos = url.indexOf("://");
        if (pos > 0) {
            int pos2 = url.indexOf("/", pos + 3);
            if (pos2 > pos) {
                if (preserveProtocol) {
                    return url.substring(0, pos2);
                } else {
                    return url.substring(pos + 3, pos2);
                }
            } else if (url.length() > pos + 3) { // no slash at the end
                if (preserveProtocol) {
                    return url;
                } else {
                    return url.substring(pos + 3);
                }
            }
        }

        return null;
    }

    public static boolean moveFile(File file, File destinationFolder) throws IOException {
        Path source = Paths.get(file.getPath());
        String destinationUri = String.format("%s%s%s", destinationFolder, File.separator, file.getName());
        Path target = Paths.get(destinationUri);
        Files.move(source, target);
        return true;
    }

    public static void main(String[] args) throws IOException {
        String targetDir = "/Users/martin/work/tmp";
        String s = "test.txt";
        File test = new File(targetDir, s);
        System.out.println(getFileExtension(test));
        System.out.println(getFileName(test.getAbsolutePath(), false));
        System.out.println(getFileName(test.getAbsolutePath(), true));

        test = new File(targetDir);
        System.out.println(getFileExtension(test));

        System.out.println("exists?" + test.exists());

//        String source = "/Users/martin";
//        File f = new File(source, s);
//
//        moveFile(f, new File(targetDir));
//
//        test = new File(targetDir, s);
//        System.out.println("exists?" + test.exists());
    }

    public static String readToString(InputStream content, String charset) throws IOException {
        StringBuilder b = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(content, charset));
        String line;
        while ((line = reader.readLine()) != null) {
            b.append(line);
        }
        return b.toString();
    }

    public static String readToString(String encoding, InputStream content, String charset) throws IOException {
        BufferedReader reader = null;
        if ("gzip".equals(encoding)) {
            GZIPInputStream gzis = new GZIPInputStream(content);
            reader = new BufferedReader(new InputStreamReader(gzis));
        } else {
            reader = new BufferedReader(new InputStreamReader(content, charset == null ? "utf-8" : charset));
        }
        StringBuilder b = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            b.append(line);
        }
        return b.toString();
    }

    public static String readToString(InputStream content) throws IOException {
        StringBuilder b = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(content, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            b.append(line);
        }
        return b.toString();
    }

    public static String parseFileExtension(File file) {
        if (file == null) {
            throw new IllegalArgumentException();
        }
        int index = file.getName().lastIndexOf(".");
        if (index <= 0 || index == file.getName().length() + 1) {
            return "";
        }
        return file.getName().substring(index + 1).trim();
    }

    public static String parseFileExtension(String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException();
        }
        int index = fileName.lastIndexOf(".");
        if (index <= 0 || index == fileName.length() + 1) {
            return "";
        }
        return fileName.substring(index + 1).trim();
    }

    public static String parseFileExtensionFromUrl(String url) {
        if (url == null) {
            throw new IllegalArgumentException();
        }

        // any Query?
        String[] tokens = url.split("\\?");
        return parseFileExtension(tokens[0]);
    }

    public static String parseContentTypeForFileExtension(Header contentType) {
        if (contentType == null) {
            return null;
        }
        return parseContentTypeForFileExtension(contentType.getValue());
    }

    public static String parseContentTypeForFileExtension(String contentType) {
        if (contentType == null) {
            return null;
        }
        ContentType type = ContentType.parse(contentType);
        return type.getMimeType();
    }

    public static String readInputStream(InputStream inputStream) throws IOException {
        if (inputStream != null) {
            System.out.println("reading input stream ...");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

            char[] charBuffer = new char[512];
            int bytesRead = -1;

            StringBuilder stringBuilder = new StringBuilder();
            while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                stringBuilder.append(charBuffer, 0, bytesRead);
            }
            bufferedReader.close();
            return stringBuilder.toString();
        }
        return null;
    }

    public static String getFileExtension(File localImageFile) {
        int separator = localImageFile.getAbsolutePath().lastIndexOf(".");
        if (separator > 0 && localImageFile.getAbsolutePath().length() > separator) {
            return localImageFile.getAbsolutePath().substring(separator + 1);
        }
        return "";
    }

    /**
     * parse the last segment of the given url / path to get a file name
     *
     * @param url
     * @param includeExtension
     * @return
     */
    public static String getFileName(String url, boolean includeExtension) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String[] tokens = url.split("/");
        String name = tokens[tokens.length - 1];
        if (!includeExtension && name.contains(".")) {
            int pos = name.lastIndexOf(".");
            return name.substring(0, pos);
        }
        return name;
    }

    private void categorizeCsv(String fromFile, String toFile) throws IOException {
        BufferedWriter writer = getFileWriter(toFile);
        BufferedReader reader = getFileReader(fromFile);

        String line;
        String cat = null;
        StringBuilder words = new StringBuilder();

        while ((line = reader.readLine()) != null) {
            String[] tokens = line.trim().split(",");

            if (cat == null || !tokens[0].equals(cat)) {
                if (cat != null) {
                    writeCategory(cat, words, writer);
                }
                cat = tokens[0];
                words = new StringBuilder();
            }

            if (words.length() > 0) {
                words.append(",");
            }
            words.append("'").append(tokens[1]).append("'");
        }

        writer.flush();
        writer.close();
        reader.close();
    }

    private void writeCategory(String cat, StringBuilder words, BufferedWriter writer) throws IOException {
        //"query1" => ['one','two','three']
        writer.write("\"" + cat + "\" => [" + words + "]");
        writer.newLine();
    }
}
