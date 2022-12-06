package io.boomerang.migration;

public interface SpringContextBridgedServices {
    FileLoadingService getFileLoadingService();
    String getCollectionPrefix();
}