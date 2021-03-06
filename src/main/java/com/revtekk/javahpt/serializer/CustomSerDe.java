package com.revtekk.javahpt.serializer;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-supplied SerDe wrapper
 *
 * This SerDe allows the client to specify a custom SerDe for the class
 * they want to serialize/deserialize - which is done by registration.
 *
 * The main functionality of this class is performing SerDe routing -- determining
 * which custom SerDe to invoke on serialize and deserialize, which is done without
 * reflection, since an explicit registration of SerDes are maintained.
 */
public class CustomSerDe
{
    private Map<Class<?>, Byte> registry = new HashMap<>();
    private Map<Byte, SerDeOps> custom = new HashMap<>();

    /**
     * Register a custom SerDe
     *
     * This method registers a class and its custom SerDe with a specific,
     * unique number which is critical for routing purposes.
     *
     * The *same* number *must* be used on both the serialization and deserialization
     * CustomSerDe objects, otherwise, the SerDe operations will fail unexpectedly!
     *
     * @param num registration number (must be unique)
     * @param clazz class type
     * @param <T> class type parameter
     */
    public <T> void register(byte num, Class<T> clazz, SerDeOps<T> serDe)
    {
        registry.put(clazz, num);
        custom.put(num, serDe);
    }

    /**
     * Serialize the object
     *
     * The class type of the object is used to select the custom SerDe, which
     * performs the actual serialization operation.
     *
     * However, in order for the deserializer to determine what object was there
     * originally, the registration number (see #register(...)) is appended before the
     * rest of the object is written out. Hence, all the deserializer has to do is
     * read the registration number to determine which custom SerDe to invoke.
     *
     * @param obj object to serialize
     * @param buffer byte buffer to write to
     * @param <T> class type
     */
    public <T> void serialize(T obj, ByteBuffer buffer)
    {
        byte type = registry.get(obj.getClass());
        buffer.put(type);
        custom.get(type).serialize(obj, buffer);
    }

    /**
     * Deserialize the object
     *
     * The registration number is the first byte of the serialized output, so all
     * that needs to be done is lookup the appropriate SerDe which is associated
     * with the provided registration number.
     *
     * Once that is determined, simply call the SerDe will the rest of the buffer
     * output, so it can reconstruct the object.
     *
     * @param buffer buffer to read from
     * @param <T> class type
     * @return the deserialized object
     */
    public <T> T deserialize(ByteBuffer buffer)
    {
        byte type = buffer.get();
        return (T)custom.get(type).deserialize(buffer);
    }
}
