package p2p.controller;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB buffer
    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024; // 500MB max file size

    public FileController(int port) throws IOException {
        this.fileSharer = new FileSharer();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.uploadDir = System.getProperty("java.io.tmpdir") + File.separator + "wind-uploads";
        this.executorService = Executors.newFixedThreadPool(20); // Increased thread pool

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
        System.out.println("Wind API server started on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        executorService.shutdown();
        System.out.println("Wind API server stopped");
    }

    // New streaming multipart parser
    private static class MultipartStreamParser {

        private final String boundary;

        public MultipartStreamParser(String boundary) {
            this.boundary = boundary;
        }

        public ParseResult parseStream(InputStream inputStream, String outputFilePath) throws IOException {
            try (BufferedInputStream bis = new BufferedInputStream(inputStream, BUFFER_SIZE); FileOutputStream fos = new FileOutputStream(outputFilePath); BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE)) {

                // Read until we find the filename
                ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
                boolean foundHeader = false;
                String filename = null;

                int b;
                while ((b = bis.read()) != -1 && !foundHeader) {
                    headerBuffer.write(b);
                    String headerStr = headerBuffer.toString();

                    if (headerStr.contains("filename=\"")) {
                        int start = headerStr.indexOf("filename=\"") + 10;
                        int end = headerStr.indexOf("\"", start);
                        if (end > start) {
                            filename = headerStr.substring(start, end);
                        }
                    }

                    if (headerStr.contains("\r\n\r\n")) {
                        foundHeader = true;
                    }
                }

                if (!foundHeader || filename == null) {
                    return null;
                }

                // Now stream the file content
                byte[] buffer = new byte[BUFFER_SIZE];
                byte[] boundaryBytes = ("\r\n--" + boundary).getBytes();
                CircularBuffer circularBuffer = new CircularBuffer(boundaryBytes.length);

                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    for (int i = 0; i < bytesRead; i++) {
                        byte currentByte = buffer[i];

                        if (circularBuffer.matches(boundaryBytes)) {
                            // Found boundary, stop writing
                            break;
                        }

                        bos.write(currentByte);
                        circularBuffer.add(currentByte);
                    }
                }

                bos.flush();
                return new ParseResult(filename, "application/octet-stream");

            } catch (Exception e) {
                throw new IOException("Error parsing multipart stream", e);
            }
        }

        public static class ParseResult {

            public final String filename;
            public final String contentType;

            public ParseResult(String filename, String contentType) {
                this.filename = filename;
                this.contentType = contentType;
            }
        }

        // Helper class for boundary detection
        private static class CircularBuffer {

            private final byte[] buffer;
            private int position = 0;
            private boolean full = false;

            public CircularBuffer(int size) {
                this.buffer = new byte[size];
            }

            public void add(byte b) {
                buffer[position] = b;
                position = (position + 1) % buffer.length;
                if (position == 0) {
                    full = true;
                }
            }

            public boolean matches(byte[] pattern) {
                if (!full && position < pattern.length) {
                    return false;
                }

                for (int i = 0; i < pattern.length; i++) {
                    int bufferIndex = (position - pattern.length + i + buffer.length) % buffer.length;
                    if (buffer[bufferIndex] != pattern[i]) {
                        return false;
                    }
                }
                return true;
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
            String contentLengthStr = requestHeaders.getFirst("Content-Length");

            if (contentLengthStr != null) {
                long contentLength = Long.parseLong(contentLengthStr);
                if (contentLength > MAX_FILE_SIZE) {
                    String response = "File too large. Maximum size is " + (MAX_FILE_SIZE / 1024 / 1024) + "MB";
                    exchange.sendResponseHeaders(413, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }
            }

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

                // Stream the file directly to disk instead of loading into memory
                String uniqueFilename = UUID.randomUUID().toString() + ".tmp";
                String tempFilePath = uploadDir + File.separator + uniqueFilename;

                MultipartStreamParser parser = new MultipartStreamParser(boundary);
                MultipartStreamParser.ParseResult result = parser.parseStream(exchange.getRequestBody(), tempFilePath);

                if (result == null) {
                    String response = "Bad Request: Could not parse file content";
                    exchange.sendResponseHeaders(400, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }

                String filename = result.filename;
                if (filename == null || filename.trim().isEmpty()) {
                    filename = "unnamed-file";
                }

                String finalFilename = UUID.randomUUID().toString() + "_" + new File(filename).getName();
                String finalFilePath = uploadDir + File.separator + finalFilename;

                // Rename temp file to final name
                new File(tempFilePath).renameTo(new File(finalFilePath));

                int port = fileSharer.offerFile(finalFilePath);

                new Thread(() -> fileSharer.startFileServer(port)).start();

                String jsonResponse = String.format("{\"port\": %d, \"filename\": \"%s\", \"size\": %d}",
                        port, filename, new File(finalFilePath).length());
                headers.add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonResponse.getBytes());
                }

            } catch (Exception e) {
                System.err.println("Error processing file upload: " + e.getMessage());
                e.printStackTrace();
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

                try (Socket socket = new Socket("localhost", port)) {
                    socket.setSoTimeout(300000); // 5 minute timeout
                    socket.setReceiveBufferSize(BUFFER_SIZE * 2);

                    try (InputStream socketInput = socket.getInputStream(); BufferedInputStream bis = new BufferedInputStream(socketInput, BUFFER_SIZE)) {

                        // Read header
                        StringBuilder headerBuilder = new StringBuilder();
                        int b;
                        boolean headerComplete = false;

                        while ((b = bis.read()) != -1 && !headerComplete) {
                            headerBuilder.append((char) b);
                            if (headerBuilder.toString().endsWith("\n\n")) {
                                headerComplete = true;
                            }
                        }

                        String headerStr = headerBuilder.toString().trim();
                        String filename = "downloaded-file";
                        long fileSize = -1;

                        // Parse header
                        String[] headerLines = headerStr.split("\n");
                        for (String line : headerLines) {
                            if (line.startsWith("Filename: ")) {
                                filename = line.substring("Filename: ".length()).trim();
                            } else if (line.startsWith("Filesize: ")) {
                                fileSize = Long.parseLong(line.substring("Filesize: ".length()).trim());
                            }
                        }

                        headers.add("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                        headers.add("Content-Type", "application/octet-stream");
                        if (fileSize > 0) {
                            headers.add("Content-Length", String.valueOf(fileSize));
                        }

                        exchange.sendResponseHeaders(200, fileSize > 0 ? fileSize : 0);

                        try (OutputStream os = exchange.getResponseBody(); BufferedOutputStream bos = new BufferedOutputStream(os, BUFFER_SIZE)) {

                            byte[] buffer = new byte[BUFFER_SIZE];
                            int bytesRead;
                            long totalReceived = 0;

                            while ((bytesRead = bis.read(buffer)) != -1) {
                                bos.write(buffer, 0, bytesRead);
                                totalReceived += bytesRead;

                                // Flush periodically
                                if (totalReceived % (BUFFER_SIZE * 10) == 0) {
                                    bos.flush();
                                    if (fileSize > 0) {
                                        System.out.printf("Downloaded: %.2f%% (%d/%d bytes)\n",
                                                (totalReceived * 100.0 / fileSize), totalReceived, fileSize);
                                    }
                                }
                            }

                            bos.flush();
                            System.out.println("File download completed: " + filename);
                        }
                    }

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

    // Keep existing MultipartParser for backward compatibility
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
}
