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

import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class PropertyParser {
  /**
   * 默认分割填充的 key 前缀
   */
  private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
  /**
   * 配置默认填充值的key
   */
  public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";
  /**
   * 配置默认分隔符的key
   */
  public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";
  /**
   * 是否开启默认填充的开关
   * 默认为 false
   */
  private static final String ENABLE_DEFAULT_VALUE = "false";
  /**
   * 默认分隔符
   */
  private static final String DEFAULT_VALUE_SEPARATOR = ":";

  private PropertyParser() {
    // Prevent Instantiation
  }

  public static String parse(String string, Properties variables) {
    VariableTokenHandler handler = new VariableTokenHandler(variables);
    GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
    return parser.parse(string);
  }

  private static class VariableTokenHandler implements TokenHandler {
    /**
     * 配置信息
     * 比如在properties中的配置信息
     */
    private final Properties variables;
    /**
     * 是否启用默认值填充
     * <code>true</code>启用
     * <code>false</code>关闭
     * 默认值为<code>false</code>
     */
    private final boolean enableDefaultValue;
    /**
     * 默认的分隔符，默认为":"
     */
    private final String defaultValueSeparator;

    private VariableTokenHandler(Properties variables) {
      this.variables = variables;
      // 获取是否启用默认填充配置
      this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
      // 分隔符
      this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
    }

    private String getPropertyValue(String key, String defaultValue) {
      return variables == null ? defaultValue : variables.getProperty(key, defaultValue);
    }


    @Override
    public String handleToken(String content) {
      if (variables != null) {
        String key = content;
        // 判断是否启用默认值填充
        if (enableDefaultValue) {
          // 根据默认分隔符查找出现位置的索引
          final int separatorIndex = content.indexOf(defaultValueSeparator);
          String defaultValue = null;
          // 有出现分隔符的情况才进行查找操作
          if (separatorIndex >= 0) {
            // 根据出现的索引截取。比如输入的内容为 userName:zpl
            // 那么截取的就是 userName
            key = content.substring(0, separatorIndex);
            // 截取默认值
            defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
          }

          // 如果默认值不为空的情况，从variables根据key获取值，如果获取不到返回默认值
          if (defaultValue != null) {
            return variables.getProperty(key, defaultValue);
          }

        }

        // 没有启用默认填充场景。简单判断下。是否有配置该key。有就直接获取返回
        if (variables.containsKey(key)) {
          return variables.getProperty(key);
        }
      }

      // 如果都没有配置variables的情况下。返回默认拼接
      return "${" + content + "}";
    }
  }

}
