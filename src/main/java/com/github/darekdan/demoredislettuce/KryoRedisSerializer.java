package com.github.darekdan.demoredislettuce;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * A fast and efficient binary RedisSerializer using the Kryo library.
 *
 * <p>Kryo is not thread-safe, so we use a ThreadLocal to manage
 * Kryo instances for each thread, which is a standard high-performance pattern.
 *
 * <p>We use writeClassAndObject/readClassAndObject to allow this
 * serializer to be generic and handle any object type.
 */
public class KryoRedisSerializer<T> implements RedisSerializer<T> {

    /**
     * ThreadLocal Kryo instances.
     */
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        // Register classes here for better performance.
        // If a class is not registered, Kryo will write the
        // full class name, which is less efficient.
        kryo.register(Item.class);
        kryo.register(ItemResponse.class);
        // Add other DTOs/Entities you plan to cache here.
        // kryo.register(AnotherCachedObject.class);
        return kryo;
    });

    @Override
    public byte[] serialize(T t) throws SerializationException {
        if (t == null) {
            return new byte[0];
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos)) {
            Kryo kryo = kryoThreadLocal.get();
            // Write class info and object data
            kryo.writeClassAndObject(output, t);
            output.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new SerializationException("Kryo serialization failed: " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked") // Kryo's readClassAndObject returns Object
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             Input input = new Input(bais)) {
            Kryo kryo = kryoThreadLocal.get();
            // Read class info and object data
            return (T) kryo.readClassAndObject(input);
        } catch (Exception e) {
            throw new SerializationException("Kryo deserialization failed: " + e.getMessage(), e);
        }
    }
}
