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
package org.apache.ibatis.reflection.factory;

import java.util.List;
import java.util.Properties;

/**
 * MyBatis uses an ObjectFactory to create all needed new Objects.
 *
 * @author Clinton Begin
 */
public interface ObjectFactory {

  /**
   * 设置配置属性
   *
   * @param properties 配置属性
   * @author zpl
   * @date 2023/12/9 22:29
   */
  default void setProperties(Properties properties) {
    // NOP
  }

  /**
   * 根据指定的class创建对象
   *
   * @param type 需要创建对象的 class
   * @author zpl
   * @date 2023/12/9 22:29
   */
  <T> T create(Class<T> type);

  /**
   * 根据指定class、指定构造器类型、指定构造器参数创建对象
   *
   * @param <T>                 the generic type
   * @param type                对象类型
   * @param constructorArgTypes 构造器参数类型
   * @param constructorArgs     构造器参数值
   * @return the t
   */
  <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs);

  /**
   * 判断该对象类型是否为集合
   *
   * @param <T>  the generic type
   * @param type Object type
   * @return whether it is a collection or not
   * @since 3.1.0
   */
  <T> boolean isCollection(Class<T> type);

}
