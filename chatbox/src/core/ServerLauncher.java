package core;

import java.io.IOException;

public final class ServerLauncher {
    private ServerLauncher() {
    }

    public static void startServer(int port) {
        System.out.println("Realtime Messenger Server");
        System.out.println("Listening on port " + port);
        System.out.println("Accounts file: " + StoragePaths.usersFile().toAbsolutePath());

        try {
            new ChatServer(port).start();
        } catch (IOException ex) {
            System.err.println("Unable to start server: " + ex.getMessage());
        }
    }
}
