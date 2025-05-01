package org.ServerSide;

import java.io.IOException;

@FunctionalInterface
public interface ThrowingConsumer<T> {
    void accept(T t) throws IOException;
}