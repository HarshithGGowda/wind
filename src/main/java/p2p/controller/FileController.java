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
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024;

    public FileController(int port) throws IOException {
        this.fileSharer = new FileSharer();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.uploadDir = System.getProperty("java.io.tmpdir") + File.separator + "wind-uploads";
        this.executorService = Executors.newFixedThreadPool(20);

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
    }

    private class UploadHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "POST, OPTIONS");
            headers.add("Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                    sendError(exchange, 400, "Content-Type must be multipart/form-data");
                    return;
                }

                String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
                String uniqueFilename = UUID.randomUUID().toString() + ".tmp";
                String tempFilePath = uploadDir + File.separator + uniqueFilename;

                MultipartStreamParser parser = new MultipartStreamParser(boundary);
                MultipartStreamParser.ParseResult result = parser.parseStream(exchange.getRequestBody(), tempFilePath);

                if (result == null) {
                    sendError(exchange, 400, "Could not parse file content");
                    return;
                }

                File tempFile = new File(tempFilePath);
                if (tempFile.length() > MAX_FILE_SIZE) {
                    tempFile.delete();
                    sendError(exchange, 413, "File too large. Maximum size is " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
                    return;
                }

                String filename = result.filename != null ? result.filename : "unnamed-file";
                String finalFilename = UUID.randomUUID().toString() + "_" + new File(filename).getName();
                String finalFilePath = uploadDir + File.separator + finalFilename;

                tempFile.renameTo(new File(finalFilePath));

                int port = fileSharer.offerFile(finalFilePath);
                new Thread(() -> fileSharer.startFileServer(port)).start();

                String jsonResponse = String.format(
                        "{\"port\": %d, \"filename\": \"%s\", \"size\": %d}",
                        port, filename, new File(finalFilePath).length()
                );

                headers.add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonResponse.getBytes());
                }

            } catch (Exception e) {
                System.err.println("Upload error: " + e.getMessage());
                sendError(exchange, 500, "Server error: " + e.getMessage());
            }
        }
    }

    private class DownloadHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");

            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String portStr = path.substring(path.lastIndexOf('/') + 1);

            try {
                int port = Integer.parseInt(portStr);

                try (Socket socket = new Socket("localhost", port)) {
                    socket.setSoTimeout(300000);
                    socket.setReceiveBufferSize(BUFFER_SIZE * 2);

                    try (InputStream socketInput = socket.getInputStream(); BufferedInputStream bis = new BufferedInputStream(socketInput, BUFFER_SIZE)) {

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

                            while ((bytesRead = bis.read(buffer)) != -1) {
                                bos.write(buffer, 0, bytesRead);
                                if (bytesRead % (BUFFER_SIZE * 10) == 0) {
                                    bos.flush();
                                }
                            }
                            bos.flush();
                        }
                    }
                } catch (IOException e) {
                    sendError(exchange, 500, "Error downloading file: " + e.getMessage());
                }
            } catch (NumberFormatException e) {
                sendError(exchange, 400, "Invalid port number");
            }
        }
    }

    private class CORSHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            headers.add("Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            sendError(exchange, 404, "Not Found");
        }
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.sendResponseHeaders(code, message.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(message.getBytes());
        }
    }

    private static class MultipartStreamParser {

        private final String boundary;

        public MultipartStreamParser(String boundary) {
            this.boundary = boundary;
        }

        public ParseResult parseStream(InputStream inputStream, String outputFilePath) throws IOException {
            try (BufferedInputStream bis = new BufferedInputStream(inputStream, BUFFER_SIZE); FileOutputStream fos = new FileOutputStream(outputFilePath); BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE)) {

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

                if (!foundHeader) {
                    return null;
                }

                byte[] buffer = new byte[BUFFER_SIZE];
                byte[] boundaryBytes = ("\r\n--" + boundary).getBytes();
                CircularBuffer circularBuffer = new CircularBuffer(boundaryBytes.length);

                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    for (int i = 0; i < bytesRead; i++) {
                        byte currentByte = buffer[i];

                        if (circularBuffer.matches(boundaryBytes)) {
                            break;
                        }

                        bos.write(currentByte);
                        circularBuffer.add(currentByte);
                    }
                }

                bos.flush();
                return new ParseResult(filename, "application/octet-stream");

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
}
