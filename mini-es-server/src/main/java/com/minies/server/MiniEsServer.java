package com.minies.server;

import com.minies.cluster.ClusterState;
import com.minies.cluster.CoordinatingNode;
import com.minies.core.document.Document;
import com.minies.core.search.ScoreDoc;
import com.minies.vector.HybridEngine;
import com.minies.vector.HybridSearchResult;
import com.minies.vector.MockEmbeddingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thin JDK HttpServer demo: /health /docs /search /refresh
 */
public final class MiniEsServer {

    private static final Pattern JSON_STRING = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern JSON_INT = Pattern.compile("\"(\\w+)\"\\s*:\\s*(\\d+)");

    private final CoordinatingNode node;
    private final HttpServer server;

    public MiniEsServer(int port, CoordinatingNode node) throws IOException {
        this.node = node;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", this::health);
        server.createContext("/docs", this::docs);
        server.createContext("/search", this::search);
        server.createContext("/refresh", this::refresh);
        server.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        server.start();
        System.out.println("mini-es listening on http://127.0.0.1:" + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
    }

    private void health(HttpExchange ex) throws IOException {
        String body = "{\"status\":\"ok\",\"primaries\":"
                + node.state().router().numberOfPrimaryShards()
                + ",\"model\":\"" + node.state().embedding().modelId() + "\"}";
        write(ex, 200, body);
    }

    private void refresh(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            write(ex, 405, "{\"error\":\"POST only\"}");
            return;
        }
        node.refresh();
        write(ex, 200, "{\"refreshed\":true}");
    }

    private void docs(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            write(ex, 405, "{\"error\":\"POST only\"}");
            return;
        }
        String raw = readBody(ex);
        String id = extractString(raw, "id");
        String title = extractString(raw, "title");
        String content = extractString(raw, "content");
        if (id == null || id.isBlank()) {
            write(ex, 400, "{\"error\":\"id required\"}");
            return;
        }
        Document doc = new Document(id)
                .add("title", title == null ? "" : title)
                .add("content", content == null ? "" : content);
        node.index(doc);
        write(ex, 200, "{\"indexed\":true,\"id\":\"" + escape(id) + "\",\"shard\":"
                + node.state().router().shardId(id) + "}");
    }

    private void search(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            write(ex, 405, "{\"error\":\"POST only\"}");
            return;
        }
        String raw = readBody(ex);
        String query = extractString(raw, "query");
        int topN = extractInt(raw, "top_n", 10);
        String fusion = extractString(raw, "fusion");
        if (query == null || query.isBlank()) {
            write(ex, 400, "{\"error\":\"query required\"}");
            return;
        }
        HybridEngine.FusionMode mode = HybridEngine.FusionMode.RRF;
        if (fusion != null) {
            mode = HybridEngine.FusionMode.valueOf(fusion.trim().toUpperCase());
        }
        HybridSearchResult result = node.searchHybrid(query, topN, mode);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"fusion\":\"").append(result.fusion()).append("\",");
        sb.append("\"degraded\":").append(result.degraded()).append(",");
        sb.append("\"hits\":[");
        List<ScoreDoc> hits = result.hits();
        for (int i = 0; i < hits.size(); i++) {
            ScoreDoc h = hits.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"").append(escape(h.docId()))
                    .append("\",\"score\":").append(h.score()).append('}');
        }
        sb.append("]}");
        write(ex, 200, sb.toString());
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void write(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String extractString(String json, String key) {
        Matcher m = JSON_STRING.matcher(json == null ? "" : json);
        while (m.find()) {
            if (key.equals(m.group(1))) {
                return m.group(2).replace("\\\"", "\"").replace("\\\\", "\\");
            }
        }
        return null;
    }

    private static int extractInt(String json, String key, int defaultValue) {
        Matcher m = JSON_INT.matcher(json == null ? "" : json);
        while (m.find()) {
            if (key.equals(m.group(1))) {
                return Integer.parseInt(m.group(2));
            }
        }
        return defaultValue;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9201;
        int shards = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        ClusterState state = new ClusterState(shards, 1, new MockEmbeddingService(32));
        CoordinatingNode node = new CoordinatingNode(state);
        MiniEsServer server = new MiniEsServer(port, node);
        server.start();
    }
}
