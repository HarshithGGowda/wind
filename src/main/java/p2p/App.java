package p2p;

import java.io.IOException;

import p2p.controller.FileController;

/**
 * PeerLink - P2P File Sharing Application
 */
public class App {

    private static FileController fileController;

    public static void main(String[] args) {
        try {
            // Start the API server on port 8080
            fileController = new FileController(8080);
            fileController.start();

            System.out.println("PeerLink server started on port 8080");
            System.out.println("UI available at http://localhost:3000");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down server...");
                if (fileController != null) {
                    fileController.stop();
                }
            }));

            System.out.println("Press Enter to stop the server");
            System.in.read();

        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (fileController != null) {
                fileController.stop();
            }
        }
    }
}
