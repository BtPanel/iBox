package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;

import com.github.catvod.net.GitHubUrl;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class RequestInterceptor implements Interceptor {

    private final ConcurrentHashMap<String, String> authMap;

    public RequestInterceptor() {
        authMap = new ConcurrentHashMap<>();
    }

    public void clear() {
        authMap.clear();
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = checkAuth(request.url());
        if (!request.method().equals("GET") && !request.method().equals("HEAD")) {
            return chain.proceed(request.newBuilder().url(GitHubUrl.accelerateIfNeeded(url.toString())).build());
        }
        List<String> candidates = GitHubUrl.candidates(url.toString());
        IOException failure = null;
        for (int i = 0; i < candidates.size(); i++) {
            if (chain.call().isCanceled()) throw new IOException("Canceled");
            try {
                Response response = chain.proceed(request.newBuilder().url(candidates.get(i)).build());
                if (response.isSuccessful() || i == candidates.size() - 1) return response;
                response.close();
            } catch (IOException e) {
                if (failure != null) e.addSuppressed(failure);
                failure = e;
            }
        }
        throw failure == null ? new IOException("No GitHub download candidate") : failure;
    }

    private HttpUrl checkAuth(HttpUrl url) {
        String host = url.host();
        String auth = url.queryParameter("auth");
        if (auth != null) authMap.put(host, auth);
        else if (authMap.containsKey(host)) return url.newBuilder().addQueryParameter("auth", authMap.get(host)).build();
        return url;
    }
}
