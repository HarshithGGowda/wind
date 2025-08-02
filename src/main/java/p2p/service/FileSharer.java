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

import p2p.utils.UploadUtils;

public class FileSharer {

    private HashMap<Integer, String> availableFiles;
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB buffer for streaming

    public FileSharer() {
        availableFiles = new HashMap<>();
    }

    public int offerFile(String filePath) {
        int port;
        while (true) {
            port = UploadUtils.generateCode();
            if (!availableFiles.containsKey(port)) {
                availableFiles.put(port, filePath);
                return port;
            }
        }
    }

    public void startFileServer(int port) {
        String filePath = availableFiles.get(port);
        if (filePath == null) {
            System.err.println("No file associated with port: " + port);
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setSoTimeout(300000); // 5 minute timeout
            System.out.println("Serving file '" + new File(filePath).getName() + "' on port " + port);

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true);
                    clientSocket.setSendBufferSize(BUFFER_SIZE * 2);
                    System.out.println("Client connected: " + clientSocket.getInetAddress());

                    new Thread(new FileSenderHandler(clientSocket, filePath)).start();
                } catch (IOException e) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("Error starting file server on port " + port + ": " + e.getMessage());
        }
    }

    private static class FileSenderHandler implements Runnable {

        private final Socket clientSocket;
        private final String filePath;

        public FileSenderHandler(Socket clientSocket, String filePath) {
            this.clientSocket = clientSocket;
            this.filePath = filePath;
        }

        @Override
        public void run() {
            File file = new File(filePath);
            long fileSize = file.length();
            String filename = file.getName();

            try (FileInputStream fis = new FileInputStream(file); BufferedInputStream bis = new BufferedInputStream(fis, BUFFER_SIZE); OutputStream oss = clientSocket.getOutputStream(); BufferedOutputStream bos = new BufferedOutputStream(oss, BUFFER_SIZE)) {

                // Send file metadata as header
                String header = String.format("Filename: %s\nFilesize: %d\n\n", filename, fileSize);
                bos.write(header.getBytes());
                bos.flush();

                // Stream the file content in chunks
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long totalSent = 0;

                while ((bytesRead = bis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                    totalSent += bytesRead;

                    // Flush periodically for better streaming
                    if (totalSent % (BUFFER_SIZE * 10) == 0) {
                        bos.flush();
                        System.out.printf("Sent: %.2f%% (%d/%d bytes)\n",
                                (totalSent * 100.0 / fileSize), totalSent, fileSize);
                    }
                }

                bos.flush();
                System.out.println("File '" + filename + "' sent successfully to " + clientSocket.getInetAddress());

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
