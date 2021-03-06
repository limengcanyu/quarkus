package org.jboss.resteasy.reactive.server.core;

import org.jboss.resteasy.reactive.server.spi.SimplifiedResourceInfo;

public final class QuarkusRestSimplifiedResourceInfo implements SimplifiedResourceInfo {

    private final String methodName;
    private final Class<?> resourceClass;
    private final Class<?>[] parameterTypes;

    public QuarkusRestSimplifiedResourceInfo(String methodName, Class<?> resourceClass, Class<?>[] parameterTypes) {
        this.methodName = methodName;
        this.resourceClass = resourceClass;
        this.parameterTypes = parameterTypes;
    }

    @Override
    public Class<?> getResourceClass() {
        return resourceClass;
    }

    @Override
    public String getMethodName() {
        return methodName;
    }

    @Override
    public Class<?>[] parameterTypes() {
        return parameterTypes;
    }
}
