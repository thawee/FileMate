package com.example.webfs.http;

import java.util.HashMap;
import java.util.Map;

public class RouterHandler implements NioHttpServer.Handler {
    private final Map<String, NioHttpServer.Handler> exactRoutes = new HashMap<>();
    private final Map<String, NioHttpServer.Handler> prefixRoutes = new HashMap<>();
    private final NioHttpServer.Handler notFoundHandler;

    public RouterHandler() {
        this.notFoundHandler = request -> new NioHttpServer.HttpResponse()
                .setStatus(404, "Not Found")
                .setBody("Route not found".getBytes());
    }

    public void get(String path, NioHttpServer.Handler handler) {
        if (path.endsWith("/*")) {
            prefixRoutes.put(path.substring(0, path.length() - 2), handler);
        } else {
            exactRoutes.put(path, handler);
        }
    }

    @Override
    public NioHttpServer.HttpResponse handle(NioHttpServer.HttpRequest request) {
        String path = request.getPath();

        // 1. Check exact matches first
        NioHttpServer.Handler handler = exactRoutes.get(path);
        if (handler != null) return handler.handle(request);

        // 2. Check prefix matches (longest prefix first)
        String bestMatch = null;
        int bestLen = 0;
        for (String prefix : prefixRoutes.keySet()) {
            if (path.startsWith(prefix) && prefix.length() > bestLen) {
                bestMatch = prefix;
                bestLen = prefix.length();
            }
        }
        if (bestMatch != null) {
            return prefixRoutes.get(bestMatch).handle(request);
        }

        // 3. Fallback to 404
        return notFoundHandler.handle(request);
    }
}