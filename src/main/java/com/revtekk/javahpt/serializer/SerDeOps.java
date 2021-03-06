package com.revtekk.javahpt.serializer;

import java.nio.ByteBuffer;

public interface SerDeOps<T>
{
    void serialize(T obj, ByteBuffer buffer);
    T deserialize(ByteBuffer buffer);
}
