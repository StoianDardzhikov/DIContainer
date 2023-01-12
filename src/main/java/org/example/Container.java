package org.example;

import org.example.Annotations.*;
import org.example.Classes.Loaded;

import java.lang.reflect.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;

public class Container {
    HashMap<String, Object> keyInstances = new HashMap<>();
    HashMap<Class<?>, Object> classInstances = new HashMap<>();
    HashMap<Class<?>, Class<?>> implementations = new HashMap<>();

    public Container() {}

    public Container(Properties properties) {
        properties.putAll(keyInstances);
    }

    public Object getInstance(String key) {
        return keyInstances.get(key);
    }

    public <T> T getInstance(Class<T> c) throws Exception {
        Class<?> keyClass = c;
        if (c.isInterface()) {
            keyClass = getInterfaceImplementationClass(c);
        }
        T instance = (T) classInstances.get(keyClass);
        if (instance == null)
            instance = (T) createInstance(keyClass, true, new HashSet<>());
        classInstances.put(keyClass, instance);
        return instance;
    }

    public void decorateInstance(Object o) throws Exception {
        addFields(o.getClass(), o, false, new HashSet<>());
    }

    public void registerInstance(String key, Object instance) throws ContainerException {
        if (keyInstances.containsKey(key))
            throw new ContainerException("This key is already used for another instance");
        keyInstances.put(key, instance);
    }

    public <T> void registerInstance(Class<T> c, Object instance) throws ContainerException {
        if (classInstances.containsKey(c))
            throw new ContainerException("This class is already used for another instance");
        classInstances.put(c, instance);
    }

    public <T, U> void registerImplementation(Class<T> ifs, Class<U> impl) throws ContainerException {
        if (implementations.containsKey(ifs))
            throw new ContainerException("This interface already has implementing class registered");
        implementations.put(ifs, impl);
    }
    public void registerInstance(Object instance) throws ContainerException {
        registerInstance(instance.getClass(), instance);
    }

    private <T> T constructInstance(Class<T> c, boolean addToContainer) throws Exception {
        Constructor<?>[] constructors = c.getDeclaredConstructors();
        Constructor<?> constructor = null;
        Object instance = null;
        for (Constructor constr : constructors) {
            Inject inject = (Inject) constr.getAnnotation(Inject.class);
            if (inject != null) {
                constructor = constr;
                break;
            }
        }

        if (constructor == null)
            instance = c.getDeclaredConstructor().newInstance();
        else {
            constructor.getParameters()[0].getName();
            Parameter[] parameters = constructor.getParameters();
            Object[] objects = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                NamedParameter namedParameter = parameters[i].getAnnotation(NamedParameter.class);
                if (namedParameter != null) {
                    String name = namedParameter.value();
                    Object parameter = keyInstances.get(name);
                    if (parameter != null) {
                        objects[i] = parameter;
                        continue;
                    }
                }
                objects[i] = createInstance(parameters[i].getType(), addToContainer, new HashSet<>());
            }
            instance = constructor.newInstance(objects);
        }

        if (addToContainer)
            classInstances.put(c, instance);

        return (T) instance;
    }

    private <T> T createInstance(Class<T> c, boolean addToContainer, HashSet<Class<?>> passedClasses) throws Exception {
        T instance = constructInstance(c, addToContainer);
        passedClasses.add(c);
        addFields(c, instance, addToContainer, passedClasses);
        if (Initializer.class.isAssignableFrom(c)) {
            invokeInit(instance);
        }
        return instance;
    }

    private <T> void addFields(Class<T> c, Object instance, boolean addToContainer, HashSet<Class<?>> passedClasses) throws Exception {
        Field[] declaredFields = c.getDeclaredFields();
        for (Field field : declaredFields) {
            Inject inject = field.getAnnotation(Inject.class);
            if (inject == null)
                continue;
            Named named = field.getAnnotation(Named.class);
            if (named != null) {
                String fieldName = field.getName();
                Object fieldKeyInstance = keyInstances.get(fieldName);
                if (fieldKeyInstance != null) {
                    field.set(instance, fieldKeyInstance);
                    continue;
                }
            }
            Class<?> fieldType = field.getType();
            if (passedClasses.contains(fieldType))
                throw new ContainerException("Circular dependency found!");
            if (fieldType.isInterface()) {
                Object implementationInstance = getInterfaceImplementation(fieldType);
                field.set(instance, implementationInstance);
                continue;
            }
            Object fieldClassInstance = classInstances.get(fieldType);
            if (fieldClassInstance != null) {
                field.set(instance, fieldClassInstance);
                continue;
            }
            fieldClassInstance = createInstance(fieldType, addToContainer, passedClasses);
            Lazy lazy = field.getAnnotation(Lazy.class);
            if (lazy != null) {
                fieldClassInstance = createProxy(fieldType);
                System.out.println(fieldClassInstance.getClass() + " <- class");
            }
            field.set(instance, fieldClassInstance);
        }
    }

    private <T> T createProxy(Class<T> proxyClass) {
        InvocationHandler invocationHandler = (Object proxy, Method method, Object[] args) -> {
            Object loadedInstance = classInstances.get(proxyClass);
            if (loadedInstance == null) {
                loadedInstance = createInstance(proxyClass, true, new HashSet<>());
                classInstances.put(proxyClass, loadedInstance);
            }
            return method.invoke(loadedInstance);
        };

        T proxy = (T) Proxy.newProxyInstance(Loaded.class.getClassLoader(), new Class[]{}, invocationHandler);
        return proxy;
    }

    private void invokeInit(Object instance) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> c = instance.getClass();
        Method method = c.getMethod("init");
        method.invoke(instance);
    }

    private Object getInterfaceImplementation(Class<?> interfaceClass) throws Exception {
        Class<?> interfaceImplementationClass = getInterfaceImplementationClass(interfaceClass);
        return constructInstance(interfaceImplementationClass, false);
    }

    private Class<?> getInterfaceImplementationClass(Class<?> interfaceClass) throws Exception {
        Class<?> implementationInstanceClass = implementations.get(interfaceClass);
        if (implementationInstanceClass != null)
            return implementationInstanceClass;

        Default _default = interfaceClass.getAnnotation(Default.class);
        if (_default == null)
            throw new ContainerException("No default implementation for interface: " + interfaceClass.getName());

        return _default.value();
    }
}