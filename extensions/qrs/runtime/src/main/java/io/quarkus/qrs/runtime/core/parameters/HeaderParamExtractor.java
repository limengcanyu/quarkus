package io.quarkus.qrs.runtime.core.parameters;

import io.quarkus.qrs.runtime.core.QrsRequestContext;

public class HeaderParamExtractor implements ParameterExtractor {

    private final String name;
    private final boolean single;

    public HeaderParamExtractor(String name, boolean single) {
        this.name = name;
        this.single = single;
    }

    @Override
    public Object extractParameter(QrsRequestContext context) {
        if (single) {
            return context.getContext().request().getHeader(name);
        } else {
            return context.getContext().request().headers().getAll(name);
        }
    }
}