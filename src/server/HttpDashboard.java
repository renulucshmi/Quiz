package server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;

/**
 * HTTP Server with REST APIs
 * Member 3: HTTP Protocol and Web Interface
 *
 * Responsibilities:
 * - Create HttpServer on port 8090
 * - Serve static files from web/ directory
 * - Implement REST endpoints:
 *   * GET /student/poll
 *   * POST /student/answer
 *   * POST /instructor/create
 *   * POST /instructor/start
 *   * POST /instructor/end
 *   * POST /instructor/reveal
 *   * GET /instructor/stats
 * - Set CORS headers
 */
public class HttpDashboard {
    private static final int HTTP_PORT = 8090;

    public HttpDashboard(PollManager pollManager) {
        // TODO: Member 3 - Initialize HTTP server
    }

    public void start() throws IOException {
        // TODO: Member 3 - Start HTTP server and create endpoints
    }
}
