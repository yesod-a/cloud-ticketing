package com.cloudticket.auth;

import java.nio.ByteBuffer;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

public final class UuidConverters {
  private UuidConverters() {}
  static byte[] toBytes(UUID value) { return value == null ? null : ByteBuffer.allocate(16).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()).array(); }
  static UUID toUuid(byte[] value) { if (value == null) return null; if (value.length != 16) throw new IllegalArgumentException("UUID binary value must contain 16 bytes"); var b=ByteBuffer.wrap(value); return new UUID(b.getLong(), b.getLong()); }
  @WritingConverter public enum UuidToBytes implements Converter<UUID,byte[]> { INSTANCE; public byte[] convert(UUID source){return toBytes(source);} }
  @ReadingConverter public enum BytesToUuid implements Converter<byte[],UUID> { INSTANCE; public UUID convert(byte[] source){return toUuid(source);} }
}
