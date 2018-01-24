package com.jiminger.image;

public interface NoThrowAutoClosable extends AutoCloseable {

    @Override
    public void close();
}
