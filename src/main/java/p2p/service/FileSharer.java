package p2p.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import p2p.utils.UploadUtils;

public class FileSharer {

    private Map<Integer, String> availableFiles;
    private Map<Integer, String> originalFilenames;
    private Map<Integer, ServerSocket> activeServers;
    private ExecutorService executorService;

    public FileSharer() {
        availableFiles = new HashMap<>();
        originalFilenames = new HashMap<>();
        activeServers = new HashMap<>();
        executorService = Executors.newCachedThreadPool();
    }

    public int offerFile(String filePath) {
        return offerFile(filePath, new File(filePath).getName());
    }

    public int offerFile(String filePath, String originalFilename) {
        int port;
        while (true) {
            port = UploadUtils.generateCode();
            if (!availableFiles.containsKey(port)) {
                availableFiles.put(port, filePath);
                originalFilenames.put(port, originalFilename);
                return port;
            }
        }
    }

    public void startFileServer(int port) {
        String filePath = availableFiles.get(port);
        String originalFilename = originalFilenames.get(port);

        if (filePath == null) {
            System.err.println("No file associated with port: " + port);
            return;
        }

        executorService.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                activeServers.put(port, serverSocket);
                System.out.println("Serving file '" + originalFilename + "' on port " + port);

                // Accept multiple connections for the same file
                while (!serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("Client connected: " + clientSocket.getInetAddress());

                        // Handle each client in a separate thread
                        executorService.submit(new FileSenderHandler(clientSocket, filePath, originalFilename));

                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            System.err.println("Error accepting client connection: " + e.getMessage());
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("Error starting file server on port " + port + ": " + e.getMessage());
            } finally {
                // Clean up
                activeServers.remove(port);
                availableFiles.remove(port);
                originalFilenames.remove(port);
            }
        });
    }

    public void stopFileServer(int port) {
        ServerSocket serverSocket = activeServers.get(port);
        if (serverSocket != null) {
            try {
                serverSocket.close();
                System.out.println("Stopped file server on port " + port);
            } catch (IOException e) {
                System.err.println("Error stopping file server on port " + port + ": " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        // Close all active servers
        for (ServerSocket serverSocket : activeServers.values()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing server socket: " + e.getMessage());
            }
        }
        activeServers.clear();
        availableFiles.clear();
        originalFilenames.clear();
        executorService.shutdown();
    }

    private static class FileSenderHandler implements Runnable {

        private final Socket clientSocket;
        private final String filePath;
        private final String originalFilename;

        public FileSenderHandler(Socket clientSocket, String filePath, String originalFilename) {
            this.clientSocket = clientSocket;
            this.filePath = filePath;
            this.originalFilename = originalFilename;
        }

        @Override
        public void run() {
            try {
                // Set socket options for large file transfers
                clientSocket.setSoTimeout(600000); // 10 minutes timeout
                clientSocket.setSendBufferSize(1024 * 1024); // 1MB send buffer
                clientSocket.setTcpNoDelay(false); // Enable Nagle's algorithm for large transfers

                FileInputStream fis = new FileInputStream(filePath);
                BufferedInputStream bis = new BufferedInputStream(fis, 1024 * 1024); // 1MB buffer
                OutputStream oss = clientSocket.getOutputStream();
                BufferedOutputStream bos = new BufferedOutputStream(oss, 1024 * 1024); // 1MB buffer

                // Send the original filename as a header
                String header = "Filename: " + originalFilename + "\n";
                bos.write(header.getBytes("UTF-8"));
                bos.flush();

                // Send the file content with larger buffer for large files
                byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
                int bytesRead;
                long totalBytesSent = 0;

                while ((bytesRead = bis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                    totalBytesSent += bytesRead;

                    // Flush every 10MB to prevent memory buildup
                    if (totalBytesSent % (10 * 1024 * 1024) == 0) {
                        bos.flush();
                    }

                    // Optional: Log progress for very large files
                    if (totalBytesSent % (50 * 1024 * 1024) == 0) { // Every 50MB
                        System.out.println("Sent " + (totalBytesSent / (1024 * 1024)) + "MB of '" + originalFilename + "'...");
                    }
                }
                bos.flush();
                System.out.println("File '" + originalFilename + "' (" + (totalBytesSent / (1024 * 1024)) + "MB) sent to " + clientSocket.getInetAddress());

                bis.close();
                fis.close();
                bos.close();
                oss.close();

            } catch (IOException e) {
                System.err.println("Error sending file to client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }
    }
}
