/*
 *    Copyright 2009-2023 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.autoconstructor;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutoConstructorTest {
  private static SqlSessionFactory sqlSessionFactory;

  @BeforeAll
  static void setUp() throws Exception {
    // 基于配置文件创建sqlSessionFactory
    try (Reader reader = Resources.getResourceAsReader("org/apache/ibatis/autoconstructor/mybatis-config.xml")) {
      sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
    }

    // 初始化表接口和数据
    BaseDataTest.runScript(sqlSessionFactory.getConfiguration()
                                            .getEnvironment()
                                            .getDataSource(), "org/apache/ibatis/autoconstructor/CreateDB.sql");
  }

  @Test
  void fullyPopulatedSubject() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      // 因为该id对应的数据字段数据都不为空。所以不报错。如果查询数据。某个字段为空null也会导致报错。也是因为使用基本数据类型的原因
      final Object subject = mapper.getSubject(1);
      assertNotNull(subject);
    }
  }

  @Test
  void primitiveSubjects() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      // 该查询会报错。因为数据库中有些值为null而类中数据类型都是用基本数据类型而不是包装类型。所有会导致报错
      assertThrows(PersistenceException.class, mapper::getSubjects);
    }
  }

  @Test
  void annotatedSubject() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      // 基于注解  @AutomapConstructor构造器。因为该注解标注的构造器。使用了包装类型。不会报错
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      verifySubjects(mapper.getAnnotatedSubjects());
    }
  }

  @Test
  void badMultipleAnnotatedSubject() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      // @AutomapConstructor只能应用于一个一个构造函数上。不能使用多个
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      final PersistenceException ex = assertThrows(PersistenceException.class, mapper::getBadAnnotatedSubjects);
      final ExecutorException cause = (ExecutorException) ex.getCause();
      assertEquals("@AutomapConstructor should be used in only one constructor.", cause.getMessage());
    }
  }

  @Test
  void badSubject() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      // 由于构造器对应的数据类型不一样。导致解析映射报错
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      assertThrows(PersistenceException.class, mapper::getBadSubjects);
    }
  }

  @Test
  void extensiveSubject() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      verifySubjects(mapper.getExtensiveSubjects());
    }
  }

  private void verifySubjects(final List<?> subjects) {
    assertNotNull(subjects);
    Assertions.assertThat(subjects.size())
              .isEqualTo(3);
  }
}
