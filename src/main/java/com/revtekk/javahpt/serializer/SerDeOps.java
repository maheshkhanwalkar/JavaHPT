package com.revtekk.javahpt.serializer;

import java.nio.ByteBuffer;

/**
 * Serialize/Deserialize operations for a class
 *
 * This interface defines the serialize and deserialize operations
 * for the specified class type parameter.
 *
 * @param <T> class type
 */
public interface SerDeOps<T>
{
    /**
     * Serialize the object
     * @param obj object to serialize
     * @param buffer buffer to write into
     */
    void serialize(T obj, ByteBuffer buffer);

    /**
     * Deserialize the object
     * @param buffer buffer to read from
     * @return the deserialized object
     */
    T deserialize(ByteBuffer buffer);
}
