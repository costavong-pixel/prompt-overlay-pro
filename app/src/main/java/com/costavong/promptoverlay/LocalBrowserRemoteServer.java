package com.costavong.promptoverlay;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * A small, local-only companion remote. It is deliberately not a cloud service:
 * the server is available only while a prompt is open and only to devices on the
 * same Wi-Fi network (for example, an iPad Personal Hotspot).
 */
final class LocalBrowserRemoteServer {
    interface CommandHandler {
        void onCommand(String action);
    }

    static final class ConnectionDetails {
        final String address;
        final String accessCode;

        ConnectionDetails(String address, String accessCode) {
            this.address = address;
            this.accessCode = accessCode;
        }
    }

    private static final int MAX_LINE_LENGTH = 4096;

    private final CommandHandler commandHandler;
    private final String accessCode;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean running;
    private String localUrl;

    LocalBrowserRemoteServer(CommandHandler commandHandler) {
        this.commandHandler = commandHandler;
        this.accessCode = String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }

    synchronized ConnectionDetails start() throws IOException {
        if (running) {
            return new ConnectionDetails(localUrl, accessCode);
        }

        String localAddress = findWifiAddress();
        if (localAddress == null) {
            throw new IOException("No local Wi-Fi address is available.");
        }

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getByName(localAddress), 0));
        localUrl = "http://" + localAddress + ":" + serverSocket.getLocalPort();
        running = true;
        executor = Executors.newFixedThreadPool(2, new DaemonThreadFactory());
        executor.execute(this::acceptConnections);
        return new ConnectionDetails(localUrl, accessCode);
    }

    synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // Closing an already closed local socket is harmless.
            }
            serverSocket = null;
        }
        localUrl = null;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void acceptConnections() {
        while (running) {
            try {
                ServerSocket activeServer = serverSocket;
                if (activeServer == null) {
                    return;
                }
                Socket client = activeServer.accept();
                ExecutorService activeExecutor = executor;
                if (activeExecutor != null) {
                    activeExecutor.execute(() -> handleClient(client));
                } else {
                    closeQuietly(client);
                }
            } catch (SocketException ignored) {
                // stop() closes the server socket to leave the accept call.
                return;
            } catch (IOException ignored) {
                if (!running) {
                    return;
                }
            }
        }
    }

    private void handleClient(Socket client) {
        try (Socket connection = client;
                InputStream input = new BufferedInputStream(connection.getInputStream());
                OutputStream output = connection.getOutputStream()) {
            connection.setSoTimeout(3500);
            String requestLine = readLine(input);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            String[] parts = requestLine.split(" ", 3);
            if (parts.length < 2 || !("GET".equals(parts[0]) || "POST".equals(parts[0]))) {
                writeResponse(output, 405, "Method not allowed", "Only GET and POST are supported.");
                return;
            }

            // Consume a small, bounded header section before responding.
            for (int headerCount = 0; headerCount < 32; headerCount++) {
                String header = readLine(input);
                if (header == null || header.isEmpty()) {
                    break;
                }
            }

            String target = parts[1];
            int queryStart = target.indexOf('?');
            String path = queryStart >= 0 ? target.substring(0, queryStart) : target;
            Map<String, String> query = parseQuery(queryStart >= 0 ? target.substring(queryStart + 1) : "");

            if ("/command".equals(path)) {
                String action = query.get("action");
                if (!accessCode.equals(query.get("code")) || !isAllowedAction(action)) {
                    writeResponse(output, 403, "Remote locked", "Use the address and access code shown on the prompt.");
                    return;
                }
                commandHandler.onCommand(action);
                writeResponse(output, 200, "OK", "OK");
                return;
            }

            if (!"/".equals(path)) {
                writeResponse(output, 404, "Not found", "This local remote has one control page.");
                return;
            }

            if (accessCode.equals(query.get("code"))) {
                writeHtml(output, 200, browserRemotePage());
            } else {
                writeHtml(output, 200, pairingPage());
            }
        } catch (IOException ignored) {
            // A browser may cancel a request when the control page is refreshed.
        }
    }

    private static boolean isAllowedAction(String action) {
        return RemoteControlProfile.ACTION_TOGGLE.equals(action)
                || RemoteControlProfile.ACTION_FASTER.equals(action)
                || RemoteControlProfile.ACTION_SLOWER.equals(action)
                || RemoteControlProfile.ACTION_FORWARD.equals(action)
                || RemoteControlProfile.ACTION_BACK.equals(action)
                || RemoteControlProfile.ACTION_REPLAY.equals(action)
                || RemoteControlProfile.ACTION_CLOSE.equals(action);
    }

    private String pairingPage() {
        return pageShell("Pair browser remote", "<main><p class=\"quiet\">Step 3 of 3</p><h1>Enter the code</h1>"
                + "<p>Type the six-digit code shown on the phone.</p>"
                + "<form method=\"get\"><input name=\"code\" inputmode=\"numeric\" "
                + "autocomplete=\"one-time-code\" pattern=\"[0-9]{6}\" maxlength=\"6\" "
                + "placeholder=\"Access code\" required autofocus>"
                + "<button type=\"submit\">Connect</button></form>"
                + "<p class=\"quiet\">This page only controls the prompt on this local Wi-Fi network.</p>"
                + "</main>");
    }

    private String browserRemotePage() {
        String encodedCode = accessCode;
        return pageShell("Prompt remote", "<main><h1>Prompt remote</h1>"
                + "<p id=\"status\" class=\"quiet\">Connected to this phone</p>"
                + "<button class=\"primary\" onclick=\"send('toggle')\">Start / Pause</button>"
                + "<section><button onclick=\"send('slower')\">Slower</button>"
                + "<button onclick=\"send('faster')\">Faster</button></section>"
                + "<section><button onclick=\"send('back')\">Back</button>"
                + "<button onclick=\"send('forward')\">Forward</button></section>"
                + "<button onclick=\"send('replay')\">Replay from top</button>"
                + "<button class=\"danger\" onclick=\"send('close')\">Close prompt</button>"
                + "</main><script>const code='" + encodedCode + "';function send(action){"
                + "document.getElementById('status').textContent='Sending…';fetch('/command?code='+code+'&action='+action,"
                + "{cache:'no-store'}).then(r=>{document.getElementById('status').textContent=r.ok?'Sent':'Connection lost';})"
                + ".catch(()=>document.getElementById('status').textContent='Connection lost');}</script>");
    }

    private static String pageShell(String title, String content) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" "
                + "content=\"width=device-width,initial-scale=1,viewport-fit=cover\"><title>" + title
                + "</title><style>body{margin:0;background:#141821;color:#fff;font-family:-apple-system,BlinkMacSystemFont,"
                + "sans-serif}main{max-width:34rem;margin:auto;padding:24px 18px 40px}h1{font-size:26px;margin:4px 0 8px}"
                + "p{line-height:1.45}.quiet{color:#b8c4db;font-size:15px}button,input{box-sizing:border-box;width:100%;"
                + "min-height:58px;border-radius:12px;font-size:19px;font-weight:600}button{border:0;background:#35415b;"
                + "color:#fff;margin:7px 0;padding:12px}button.primary{background:#407bff;font-size:23px;min-height:72px}"
                + "button.danger{background:#7b3741;margin-top:20px}input{border:1px solid #71809e;background:#202838;"
                + "color:#fff;padding:12px;text-align:center;letter-spacing:.16em}section{display:flex;gap:12px}section button"
                + "{width:50%}</style></head><body>" + content + "</body></html>";
    }

    private static void writeHtml(OutputStream output, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        writeHeaders(output, status, status == 200 ? "OK" : "Error", "text/html; charset=utf-8", data.length);
        output.write(data);
    }

    private static void writeResponse(OutputStream output, int status, String title, String message)
            throws IOException {
        writeHtml(output, status, pageShell(title, "<main><h1>" + title + "</h1><p>" + message + "</p></main>"));
    }

    private static void writeHeaders(OutputStream output, int status, String message, String contentType,
            int contentLength) throws IOException {
        String headers = "HTTP/1.1 " + status + " " + message + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + contentLength + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
    }

    private static String readLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int count = 0; count < MAX_LINE_LENGTH; count++) {
            int next = input.read();
            if (next == -1) {
                return line.length() == 0 ? null : line.toString();
            }
            if (next == '\n') {
                return line.toString();
            }
            if (next != '\r') {
                line.append((char) next);
            }
        }
        throw new IOException("Request line is too long.");
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> values = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return values;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int equals = pair.indexOf('=');
            String key = decode(equals >= 0 ? pair.substring(0, equals) : pair);
            String value = decode(equals >= 0 ? pair.substring(equals + 1) : "");
            if (key != null && value != null) {
                values.put(key, value);
            }
        }
        return values;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String findWifiAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return null;
            }
            String privateFallback = null;
            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                String name = networkInterface.getName().toLowerCase();
                boolean likelyWifi = name.contains("wlan") || name.contains("wifi") || name.startsWith("ap");
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()
                            || address.isLinkLocalAddress()) {
                        continue;
                    }
                    String host = address.getHostAddress();
                    if (likelyWifi && isPrivateIpv4(host)) {
                        return host;
                    }
                    if (privateFallback == null && isPrivateIpv4(host)) {
                        privateFallback = host;
                    }
                }
            }
            return privateFallback;
        } catch (SocketException ignored) {
            return null;
        }
    }

    private static boolean isPrivateIpv4(String address) {
        String[] parts = address.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            return first == 10 || first == 192 && second == 168 || first == 172 && second >= 16 && second <= 31;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Nothing to recover from for a local client socket.
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "PromptBrowserRemote");
            thread.setDaemon(true);
            return thread;
        }
    }
}
