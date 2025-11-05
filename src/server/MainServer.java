package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Main TCP Server
 * Member 1: TCP Server Socket Programming
 *
 * Responsibilities:
 * - Create ServerSocket on port 8088
 * - Use ExecutorService thread pool (16 threads)
 * - Accept multiple client connections
 * - Spawn ClientHandler for each connection
 * - Implement instructor CLI commands:
 *   * newpoll <question> | <opt1;opt2;opt3;opt4> | <correct>
 *   * startpoll
 *   * endpoll
 *   * reveal
 *   * status
 *   * exit
 */
public class MainServer {
    private static final int TCP_PORT = 8088;

    public static void main(String[] args) {
        // TODO: Member 1 - Implement TCP server
    }
}
