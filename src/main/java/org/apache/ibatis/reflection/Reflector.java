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
package org.apache.ibatis.reflection;

import org.apache.ibatis.reflection.invoker.*;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.util.MapUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map.Entry;

/**
 * 反射器。每个对象对应一个{@link Reflector}
 *
 * @author Clinton Begin
 */
public class Reflector {

  /**
   * 记录方法的处理器。这个更高的jdk版本才有。由于我用的是jdk8.所有这个我们忽略不用理会
   */
  private static final MethodHandle isRecordMethodHandle = getIsRecordMethodHandle();
  /**
   * 封装的Class对象
   * 可以理解成。每个类对应一个 {@link Reflector}
   * 每个{@link Reflector}持有该类的引用
   */
  private final Class<?> type;
  /**
   * 可读属性集合， 也就是存在get方法的属性
   */
  private final String[] readablePropertyNames;
  /**
   * 可写的属性集合，也就是存在set方法的属性
   */
  private final String[] writablePropertyNames;
  /**
   * 记录属性对应的set方法的集合。
   * key是属性名称
   * value是{@link Invoker}
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();
  /**
   * 记录属性对应的get方法的集合。
   * key是属性名称
   * value是{@link Invoker}。
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();
  /**
   * 记录set方法的参数类型,key为属性名称
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  /**
   * 记录get方法的参数类型,key为属性名称,value为方法的返回值
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  /**
   * 默认构造函数
   */
  private Constructor<?> defaultConstructor;
  /**
   * 所有属性名称集合 key和value都是属性名称
   */
  private final Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    // 设置要解析的类
    type = clazz;
    // 1:获取默认构造函数(默认获取无参构造函数)
    addDefaultConstructor(clazz);
    // 2:获取所有的方法
    Method[] classMethods = getClassMethods(clazz);
    // 3:判断是否记录方法(这一步可以先忽略。因为我们暂时用不到jdk高版本)
    if (isRecord(type)) {
      addRecordGetMethods(classMethods);
    } else {
      // 4:初始化 getMethods 和getTypes
      addGetMethods(classMethods);
      // 5:初始化 setMethods 和 getTypes
      addSetMethods(classMethods);
      // 6:添加字段
      addFields(clazz);
    }
    // 7:初始化可读属性
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    // 8:初始化可写属性
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);
    // 9:初始所有属性字段。包括readablePropertyNames和writablePropertyNames
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  private void addRecordGetMethods(Method[] methods) {
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0)
          .forEach(m -> addGetMethod(m.getName(), m, false));
  }

  /**
   * 获取无参构造方法
   *
   * @author zpl
   * @date 2023/11/14 22:42
   */
  private void addDefaultConstructor(Class<?> clazz) {
    // 获取类中所有的构造方法
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    Arrays.stream(constructors)
          // 过滤形参为0的构造方法
          .filter(constructor -> constructor.getParameterTypes().length == 0).findAny()
          // 不为空时，才进行defaultConstructor的赋值操作
          .ifPresent(constructor -> this.defaultConstructor = constructor);
  }

  private void addGetMethods(Method[] methods) {
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    Arrays.stream(methods)
          // 判断方法形参是否为0以及方法名是否以get或is开头
          .filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
          .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 解决方法形参冲突的问题
    resolveGetterConflicts(conflictingGetters);
  }

  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      // 最佳的getter方法
      Method winner = null;
      // 属性名
      String propName = entry.getKey();
      boolean isAmbiguous = false;
      for (Method candidate : entry.getValue()) {
        // 如果当前winner为空，说明当前candidate方法是最佳方法
        if (winner == null) {
          winner = candidate;
          continue;
        }

        // 获取方法的返回值
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();

        // 判断两个类型是否一样
        if (candidateType.equals(winnerType)) {
          // 非布尔类型的情况。这一步主要是为了解决歧义问题。比如说：当一个属性有多个getter方法返回一样的返回值时，这说明是有歧义的。
          // 按照 Java Bean的规范，一个非布尔类型的属性只会对应一个getter方法
          if (!boolean.class.equals(candidateType)) {
            isAmbiguous = true;
            break;
          }

          // 执行到这里,说明是布尔类型的返回值。则允许存在 isProperty和getProperty两种风格的方法
          // 如果是候选方的方法名是以is开头的则作为最佳方法。也就是说。如果同时存在isProperty和getProperty则isProperty优先
          if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // 如果winnerType是candidateType的子类或者同类。则什么都不处理。因为已经获取最原始的类了。这一步winnerType已经是最佳方法了。
        } else if (winnerType.isAssignableFrom(candidateType)) {
          // 判断candidateType是否是winnerType的子类或者同类，如果是，则赋值给winner,表示candidate为最佳方法
          winner = candidate;
        } else {
          isAmbiguous = true;
          break;
        }
      }
      addGetMethod(propName, winner, isAmbiguous);
    }
  }

  private void addGetMethod(String name, Method method, boolean isAmbiguous) {
    MethodInvoker invoker = isAmbiguous ? new AmbiguousMethodInvoker(method,
                                                                     MessageFormat.format(
                                                                       "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
                                                                       name,
                                                                       method.getDeclaringClass()
                                                                             .getName())) : new MethodInvoker(method);
    getMethods.put(name, invoker);
    // 解析方法的返回类型
    Type returnType = TypeParameterResolver.resolveReturnType(method, type);
    // 查找原始的方法类型
    getTypes.put(name, typeToClass(returnType));
  }

  private void addSetMethods(Method[] methods) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    Arrays.stream(methods)
          // 要求方法形参参数只有一个，并且以set开头的方法名
          .filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
          .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 解决setter方法冲突问题
    resolveSetterConflicts(conflictingSetters);
  }

  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    // 属性名合法的情况下才进行保存
    if (isValidPropertyName(name)) {
      List<Method> list = MapUtil.computeIfAbsent(conflictingMethods, name, k -> new ArrayList<>());
      list.add(method);
    }
  }

  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
      // 属性名
      String propName = entry.getKey();
      // setter方法集合
      List<Method> setters = entry.getValue();
      // getter方法的返回值类型
      Class<?> getterType = getTypes.get(propName);
      // getter方法是否存在歧义的方法重载
      boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
      boolean isSetterAmbiguous = false;
      Method match = null;
      for (Method setter : setters) {
        // getter方法的没有歧义并且getter的返回值类型和setter方法的形参类型一致
        if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        // 执行到这里。说明setter也有歧义。需要找出最合适的setter方法
        if (!isSetterAmbiguous) {
          match = pickBetterSetter(match, setter, propName);
          isSetterAmbiguous = match == null;
        }
      }
      if (match != null) {
        addSetMethod(propName, match);
      }
    }
  }

  /**
   * 查找最合适的setter方法
   *
   * @param setter1  最佳匹配的方法
   * @param setter2  当前需要匹配的方法
   * @param property 属性名
   * @author zpl
   * @date 2023/11/16 22:03
   */
  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }

    // 获取形参的参数类型
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    }

    if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }

    MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
                                                       MessageFormat.format(
                                                         "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.",
                                                         property,
                                                         setter2.getDeclaringClass().getName(),
                                                         paramType1.getName(),
                                                         paramType2.getName()));
    setMethods.put(property, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
    setTypes.put(property, typeToClass(paramTypes[0]));
    return null;
  }

  private void addSetMethod(String name, Method method) {
    MethodInvoker invoker = new MethodInvoker(method);
    setMethods.put(name, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
    setTypes.put(name, typeToClass(paramTypes[0]));
  }

  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        int modifiers = field.getModifiers();
        if ((!Modifier.isFinal(modifiers) || !Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    // 存在父级。继续递归
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private boolean isValidPropertyName(String name) {
    return (!name.startsWith("$") && !"serialVersionUID".equals(name) && !"class".equals(name));
  }

  /**
   * 获取类型的所有方法。包括私有和公有方法，包含父类的。排除{@link Object}的方法
   *
   * @param clazz The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> clazz) {
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;
    while (currentClass != null && currentClass != Object.class) {
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      // 获取父级Class。继续递归
      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[0]);
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      // 不是桥接方法的场景下。才进行处理
      if (!currentMethod.isBridge()) {
        // 获取方法签名
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  /**
   * 获取签名。用于唯一性校验
   * <p>
   * 格式为：返回值类型名称#方法名称:参数1,参数2,参数3
   *
   * @author zpl
   * @date 2023/11/12 11:16
   */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    sb.append(returnType.getName()).append('#');
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    }
    throw new ReflectionException("There is no default constructor for " + type);
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.containsKey(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.containsKey(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }

  /**
   * Class.isRecord() alternative for Java 15 and older.
   */
  private static boolean isRecord(Class<?> clazz) {
    try {
      return isRecordMethodHandle != null && (boolean) isRecordMethodHandle.invokeExact(clazz);
    } catch (Throwable e) {
      throw new ReflectionException("Failed to invoke 'Class.isRecord()'.", e);
    }
  }

  private static MethodHandle getIsRecordMethodHandle() {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodType mt = MethodType.methodType(boolean.class);
    try {
      return lookup.findVirtual(Class.class, "isRecord", mt);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      return null;
    }
  }
}
