package com.cloudticket.activity;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * Registers MyBatis-Plus table metadata for entities used by unit tests.
 *
 * <p>Lambda update wrappers resolve a property to a column through the entity's {@code TableInfo},
 * which is normally built while the mappers start up. A plain unit test never starts a mapper, so it
 * has to publish the metadata itself.
 */
final class MybatisMetadata {

  private MybatisMetadata() {}

  static void register(Class<?>... entities) {
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
    for (Class<?> entity : entities) {
      TableInfoHelper.initTableInfo(assistant, entity);
    }
  }
}
