package com.kernel.hr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kernel")
public class AppProperties {

    private Anthropic anthropic = new Anthropic();
    private Embedding embedding = new Embedding();
    private Srb srb = new Srb();
    private Alb alb = new Alb();
    private Graph graph = new Graph();
    private Sharepoint sharepoint = new Sharepoint();
    private Auth auth = new Auth();
    private Index index = new Index();

    public Anthropic getAnthropic() { return anthropic; }
    public void setAnthropic(Anthropic anthropic) { this.anthropic = anthropic; }
    public Embedding getEmbedding() { return embedding; }
    public void setEmbedding(Embedding embedding) { this.embedding = embedding; }
    public Srb getSrb() { return srb; }
    public void setSrb(Srb srb) { this.srb = srb; }
    public Alb getAlb() { return alb; }
    public void setAlb(Alb alb) { this.alb = alb; }
    public Graph getGraph() { return graph; }
    public void setGraph(Graph graph) { this.graph = graph; }
    public Sharepoint getSharepoint() { return sharepoint; }
    public void setSharepoint(Sharepoint sharepoint) { this.sharepoint = sharepoint; }
    public Auth getAuth() { return auth; }
    public void setAuth(Auth auth) { this.auth = auth; }
    public Index getIndex() { return index; }
    public void setIndex(Index index) { this.index = index; }

    public static class Anthropic {
        private String apiKey = "";
        private String model = "claude-opus-4-8";
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class Embedding {
        private String provider = "voyage";
        private String voyageApiKey = "";
        private String model = "voyage-3";
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getVoyageApiKey() { return voyageApiKey; }
        public void setVoyageApiKey(String voyageApiKey) { this.voyageApiKey = voyageApiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class Srb {
        private String source = "zip";
        private String zipPath = "./data/srb_docs.zip";
        private String extractDir = "./data/_srb_extracted";
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getZipPath() { return zipPath; }
        public void setZipPath(String zipPath) { this.zipPath = zipPath; }
        public String getExtractDir() { return extractDir; }
        public void setExtractDir(String extractDir) { this.extractDir = extractDir; }
    }

    public static class Alb {
        private String source = "sharepoint";
        private String zipPath = "";
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getZipPath() { return zipPath; }
        public void setZipPath(String zipPath) { this.zipPath = zipPath; }
    }

    public static class Graph {
        private String tenantId = "";
        private String clientId = "";
        private String clientSecret = "";
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    }

    public static class Sharepoint {
        private String siteId = "";
        private String driveId = "";
        public String getSiteId() { return siteId; }
        public void setSiteId(String siteId) { this.siteId = siteId; }
        public String getDriveId() { return driveId; }
        public void setDriveId(String driveId) { this.driveId = driveId; }
    }

    public static class Auth {
        private String mode = "mock";
        private String mockProfilePath = "./data/mock_profiles/";
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getMockProfilePath() { return mockProfilePath; }
        public void setMockProfilePath(String mockProfilePath) { this.mockProfilePath = mockProfilePath; }
    }

    public static class Index {
        private String path = "./data/index.json";
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }
}
