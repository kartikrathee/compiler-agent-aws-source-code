package com.apac.atlas.services.lambda;

import com.apac.atlas.core.storage.StorageBackend;
import com.apac.atlas.core.storage.StorageFactory;
import com.apac.atlas.services.lambda.model.LambdaAlias;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class LambdaAliasStore {

    private final StorageBackend<String, LambdaAlias> backend;
    private final ConcurrentHashMap<String, LambdaAlias> urlIdIndex = new ConcurrentHashMap<>();

    @Inject
    public LambdaAliasStore(StorageFactory storageFactory) {
        this.backend = storageFactory.create("lambda", "lambda-aliases.json",
                new TypeReference<Map<String, LambdaAlias>>() {});
        loadIndex();
    }

    LambdaAliasStore(StorageBackend<String, LambdaAlias> backend) {
        this.backend = backend;
        loadIndex();
    }

    private void loadIndex() {
        backend.scan(k -> true).forEach(this::indexAlias);
    }

    private void indexAlias(LambdaAlias alias) {
        if (alias.getUrlConfig() != null && alias.getUrlConfig().getFunctionUrl() != null) {
            String urlId = extractUrlId(alias.getUrlConfig().getFunctionUrl());
            if (urlId != null) {
                urlIdIndex.put(urlId, alias);
            }
        }
    }

    private void deindexAlias(LambdaAlias alias) {
        if (alias.getUrlConfig() != null && alias.getUrlConfig().getFunctionUrl() != null) {
            String urlId = extractUrlId(alias.getUrlConfig().getFunctionUrl());
            if (urlId != null) {
                urlIdIndex.remove(urlId);
            }
        }
    }

    private String extractUrlId(String url) {
        int start = url.indexOf("://");
        if (start < 0) return null;
        int end = url.indexOf(".", start + 3);
        if (end < 0) return null;
        return url.substring(start + 3, end);
    }

    public void save(String region, LambdaAlias alias) {
        get(region, alias.getFunctionName(), alias.getName()).ifPresent(this::deindexAlias);
        backend.put(key(region, alias.getFunctionName(), alias.getName()), alias);
        indexAlias(alias);
    }

    public Optional<LambdaAlias> get(String region, String functionName, String aliasName) {
        return backend.get(key(region, functionName, aliasName));
    }

    public Optional<LambdaAlias> getByUrlId(String urlId) {
        return Optional.ofNullable(urlIdIndex.get(urlId));
    }

    public List<LambdaAlias> list(String region, String functionName) {
        String prefix = "alias::" + region + "::" + functionName + "::";
        return backend.scan(k -> k.startsWith(prefix));
    }

    public List<LambdaAlias> listAll() {
        return backend.scan(k -> true);
    }

    public void delete(String region, String functionName, String aliasName) {
        get(region, functionName, aliasName).ifPresent(alias -> {
            deindexAlias(alias);
            backend.delete(key(region, functionName, aliasName));
        });
    }

    private static String key(String region, String functionName, String aliasName) {
        return "alias::" + region + "::" + functionName + "::" + aliasName;
    }
}
