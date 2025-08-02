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
    private static final int BUFFER_SIZE = 64 * 1024;

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
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setSoTimeout(300000);

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true);
                    clientSocket.setSendBufferSize(BUFFER_SIZE * 2);

                    new Thread(new FileSenderHandler(clientSocket, filePath)).start();
                } catch (IOException e) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error starting file server: " + e.getMessage());
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

                String header = String.format("Filename: %s\nFilesize: %d\n\n", filename, fileSize);
                bos.write(header.getBytes());
                bos.flush();

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead = bis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                    if (bytesRead % (BUFFER_SIZE * 10) == 0) {
                        bos.flush();
                    }
                }

                bos.flush();

            } catch (IOException e) {
                System.err.println("Error sending file: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }
}
