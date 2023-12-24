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
package org.apache.ibatis.parsing;

/**
 * @author Clinton Begin
 */
public interface TokenHandler {
  /**
   * 处理 token(可以理解为解析xml配置的占位符)
   *
   * @param content 要处理的内容
   * @return 返回解析后的内容
   * @author zpl
   * @date 2023/12/23 22:23
   */
  String handleToken(String content);
}
