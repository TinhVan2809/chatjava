package models;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public final class ClientSession {
    private final Socket socket;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private final String host;
    private final int port;
    private final String username;
    private final String fullName;

    public ClientSession(Socket socket, BufferedReader reader, PrintWriter writer, String host, int port,
            String username, String fullName) {
        this.socket = socket;
        this.reader = reader;
        this.writer = writer;
        this.host = host;
        this.port = port;
        this.username = username;
        this.fullName = fullName;
    }

    public Socket getSocket() {
        return socket;
    }

    public BufferedReader getReader() {
        return reader;
    }

    public PrintWriter getWriter() {
        return writer;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDisplayName() {
        return fullName + " (" + username + ")";
    }

    public void closeQuietly() {
        try {
            writer.close();
        } catch (Exception ignored) {
        }

        try {
            reader.close();
        } catch (Exception ignored) {
        }

        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
