package com.cloudticket.common.mybatis.handler;

import java.nio.ByteBuffer;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * Maps {@link UUID} values onto {@code BINARY(16)} columns.
 *
 * <p>The auth schema stores identifiers as {@code BINARY(16)} and converts them in SQL with
 * {@code UUID_TO_BIN}/{@code BIN_TO_UUID}. Keeping the same byte layout in a type handler lets the
 * mappers use plain {@code WHERE id = #{id}} predicates instead of hand-written conversion calls.
 */
@MappedTypes(UUID.class)
public class ByteArrayUuidTypeHandler extends BaseTypeHandler<UUID> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType)
      throws SQLException {
    ps.setBytes(i, toBytes(parameter));
  }

  @Override
  public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toUuid(rs.getBytes(columnName));
  }

  @Override
  public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toUuid(rs.getBytes(columnIndex));
  }

  @Override
  public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toUuid(cs.getBytes(columnIndex));
  }

  public static byte[] toBytes(UUID value) {
    if (value == null) return null;
    return ByteBuffer.allocate(16)
        .putLong(value.getMostSignificantBits())
        .putLong(value.getLeastSignificantBits())
        .array();
  }

  public static UUID toUuid(byte[] value) {
    if (value == null) return null;
    if (value.length != 16) throw new IllegalArgumentException("UUID binary value must contain 16 bytes");
    ByteBuffer buffer = ByteBuffer.wrap(value);
    return new UUID(buffer.getLong(), buffer.getLong());
  }
}
