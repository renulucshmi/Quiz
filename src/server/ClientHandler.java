package server;

import java.io.*;
import java.net.*;

/**
 * Client Connection Handler
 * Member 2: Socket I/O and Protocol
 *
 * Responsibilities:
 * - Handle individual client socket connections
 * - Use BufferedReader and PrintWriter for I/O
 * - Parse JSON messages: JOIN, ANSWER, PING
 * - Send JSON messages: POLL, RESULT, ACK, PONG
 * - Register clients with server
 * - Submit answers to PollManager
 */
public class ClientHandler implements Runnable {

    public ClientHandler(Socket socket, MainServer server, PollManager pollManager) {
        // TODO: Member 2 - Initialize handler
    }

    @Override
    public void run() {
        // TODO: Member 2 - Handle client communication
    }
}
