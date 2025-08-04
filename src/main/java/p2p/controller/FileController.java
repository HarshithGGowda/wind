package p2p.controller;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.IOUtils;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import p2p.service.FileSharer;

public class FileController {

    private final FileSharer fileSharer;
    private final HttpServer server;
    private final String uploadDir;
    private final ExecutorService executorService;

    public FileController(int port) throws IOException {
        this.fileSharer = new FileSharer();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.uploadDir = System.getProperty("java.io.tmpdir") + File.separator + "peerlink-uploads";
        this.executorService = Executors.newFixedThreadPool(10);

        File uploadDirFile = new File(uploadDir);
        if (!uploadDirFile.exists()) {
            uploadDirFile.mkdirs();
        }

        server.createContext("/upload", new UploadHandler());
        server.createContext("/download", new DownloadHandler());
        server.createContext("/", new CORSHandler());

        server.setExecutor(executorService);
    }

    public void start() {
        server.start();
        System.out.println("API server started on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        executorService.shutdown();
        System.out.println("API server stopped");
    }

    private class CORSHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            headers.add("Access-Control-Allow-Headers", "Content-Type,Authorization");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String response = "Not Found";
            exchange.sendResponseHeaders(404, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    private static class MultipartParser {

        private final byte[] data;
        private final String boundary;

        public MultipartParser(byte[] data, String boundary) {
            this.data = data;
            this.boundary = boundary;
        }

        public ParseResult parse() {
            try {
                String dataAsString = new String(data);

                String filenameMarker = "filename=\"";
                int filenameStart = dataAsString.indexOf(filenameMarker);
                if (filenameStart == -1) {
                    return null;
                }

                filenameStart += filenameMarker.length();
                int filenameEnd = dataAsString.indexOf("\"", filenameStart);
                String filename = dataAsString.substring(filenameStart, filenameEnd);

                String contentTypeMarker = "Content-Type: ";
                int contentTypeStart = dataAsString.indexOf(contentTypeMarker, filenameEnd);
                String contentType = "application/octet-stream"; // Default

                if (contentTypeStart != -1) {
                    contentTypeStart += contentTypeMarker.length();
                    int contentTypeEnd = dataAsString.indexOf("\r\n", contentTypeStart);
                    contentType = dataAsString.substring(contentTypeStart, contentTypeEnd);
                }

                String headerEndMarker = "\r\n\r\n";
                int headerEnd = dataAsString.indexOf(headerEndMarker);
                if (headerEnd == -1) {
                    return null;
                }

                int contentStart = headerEnd + headerEndMarker.length();

                byte[] boundaryBytes = ("\r\n--" + boundary + "--").getBytes();
                int contentEnd = findSequence(data, boundaryBytes, contentStart);

                if (contentEnd == -1) {
                    boundaryBytes = ("\r\n--" + boundary).getBytes();
                    contentEnd = findSequence(data, boundaryBytes, contentStart);
                }

                if (contentEnd == -1 || contentEnd <= contentStart) {
                    return null;
                }

                byte[] fileContent = new byte[contentEnd - contentStart];
                System.arraycopy(data, contentStart, fileContent, 0, fileContent.length);

                return new ParseResult(filename, contentType, fileContent);
            } catch (Exception e) {
                System.err.println("Error parsing multipart data: " + e.getMessage());
                return null;
            }
        }

        private int findSequence(byte[] data, byte[] sequence, int startPos) {
            outer:
            for (int i = startPos; i <= data.length - sequence.length; i++) {
                for (int j = 0; j < sequence.length; j++) {
                    if (data[i + j] != sequence[j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }

        public static class ParseResult {

            public final String filename;
            public final String contentType;
            public final byte[] fileContent;

            public ParseResult(String filename, String contentType, byte[] fileContent) {
                this.filename = filename;
                this.contentType = contentType;
                this.fileContent = fileContent;
            }
        }
    }

    private class UploadHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                String response = "Method Not Allowed";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            Headers requestHeaders = exchange.getRequestHeaders();
            String contentType = requestHeaders.getFirst("Content-Type");

            if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                String response = "Bad Request: Content-Type must be multipart/form-data";
                exchange.sendResponseHeaders(400, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            try {
                String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                IOUtils.copy(exchange.getRequestBody(), baos);
                byte[] requestData = baos.toByteArray();

                MultipartParser parser = new MultipartParser(requestData, boundary);
                MultipartParser.ParseResult result = parser.parse();

                if (result == null) {
                    String response = "Bad Request: Could not parse file content";
                    exchange.sendResponseHeaders(400, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }

                String originalFilename = result.filename;
                if (originalFilename == null || originalFilename.trim().isEmpty()) {
                    originalFilename = "unnamed-file";
                }

                // Create unique filename while preserving original name and extension
                String fileExtension = "";
                String baseName = originalFilename;
                int lastDotIndex = originalFilename.lastIndexOf('.');
                if (lastDotIndex > 0 && lastDotIndex < originalFilename.length() - 1) {
                    fileExtension = originalFilename.substring(lastDotIndex);
                    baseName = originalFilename.substring(0, lastDotIndex);
                }

                String uniqueFilename = UUID.randomUUID().toString() + "_" + baseName + fileExtension;
                String filePath = uploadDir + File.separator + uniqueFilename;

                try (FileOutputStream fos = new FileOutputStream(filePath)) {
                    fos.write(result.fileContent);
                }

                int port = fileSharer.offerFile(filePath, originalFilename); // Pass original filename

                new Thread(() -> fileSharer.startFileServer(port)).start();

                String jsonResponse = "{\"port\": " + port + "}";
                headers.add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonResponse.getBytes());
                }

            } catch (Exception e) {
                System.err.println("Error processing file upload: " + e.getMessage());
                String response = "Server error: " + e.getMessage();
                exchange.sendResponseHeaders(500, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

    private class DownloadHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");

            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                String response = "Method Not Allowed";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String portStr = path.substring(path.lastIndexOf('/') + 1);

            try {
                int port = Integer.parseInt(portStr);

                try (Socket socket = new Socket("localhost", port); InputStream socketInput = socket.getInputStream()) {

                    File tempFile = File.createTempFile("download-", ".tmp");
                    String filename = "downloaded-file"; // Default filename

                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        // Read the filename header
                        StringBuilder headerBuilder = new StringBuilder();
                        int b;
                        while ((b = socketInput.read()) != -1) {
                            if (b == '\n') {
                                break;
                            }
                            if (b != '\r') { // Skip carriage return
                                headerBuilder.append((char) b);
                            }
                        }

                        String header = headerBuilder.toString().trim();
                        if (header.startsWith("Filename: ")) {
                            filename = header.substring("Filename: ".length());
                        }

                        // Read file content
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = socketInput.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }

                    // Set proper content disposition with original filename (RFC 6266 compliant)
                    String encodedFilename = java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
                    headers.add("Content-Disposition", "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encodedFilename);

                    // Set content type based on file extension
                    String contentType = getContentTypeFromFilename(filename);
                    headers.add("Content-Type", contentType);

                    exchange.sendResponseHeaders(200, tempFile.length());
                    try (OutputStream os = exchange.getResponseBody(); FileInputStream fis = new FileInputStream(tempFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }

                    tempFile.delete();

                } catch (IOException e) {
                    System.err.println("Error downloading file from peer: " + e.getMessage());
                    String response = "Error downloading file: " + e.getMessage();
                    headers.add("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(500, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                }

            } catch (NumberFormatException e) {
                String response = "Bad Request: Invalid port number";
                exchange.sendResponseHeaders(400, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }

        private String getContentTypeFromFilename(String filename) {
            if (filename == null) {
                return "application/octet-stream";
            }

            String extension = "";
            int lastDotIndex = filename.lastIndexOf('.');
            if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
                extension = filename.substring(lastDotIndex + 1).toLowerCase();
            }

            switch (extension) {
                case "pdf":
                    return "application/pdf";
                case "txt":
                    return "text/plain";
                case "doc":
                    return "application/msword";
                case "docx":
                    return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "xls":
                    return "application/vnd.ms-excel";
                case "xlsx":
                    return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "ppt":
                    return "application/vnd.ms-powerpoint";
                case "pptx":
                    return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                case "jpg":
                case "jpeg":
                    return "image/jpeg";
                case "png":
                    return "image/png";
                case "gif":
                    return "image/gif";
                case "zip":
                    return "application/zip";
                case "rar":
                    return "application/x-rar-compressed";
                case "mp3":
                    return "audio/mpeg";
                case "mp4":
                    return "video/mp4";
                case "avi":
                    return "video/x-msvideo";
                case "json":
                    return "application/json";
                case "xml":
                    return "application/xml";
                case "html":
                    return "text/html";
                case "css":
                    return "text/css";
                case "js":
                    return "application/javascript";
                default:
                    return "application/octet-stream";
            }
        }
    }
}
