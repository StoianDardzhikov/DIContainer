package org.example;

import org.example.Annotations.Default;
import org.example.Annotations.Inject;
import org.example.Annotations.Named;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;

public class Container {
    HashMap<String, Object> keyInstances = new HashMap<>();
    HashMap<Class<?>, Object> classInstances = new HashMap<>();
    HashMap<Class<?>, Class<?>> implementations = new HashMap<>();

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
            instance = (T) createInstance(keyClass, true);
        classInstances.put(keyClass, instance);
        return instance;
    }

    public void decorateInstance(Object o) throws Exception {
        addFields(o.getClass(), o, false);
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
            Class<?>[] parameters = constructor.getParameterTypes();
            Object[] objects = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                objects[i] = constructInstance(parameters[i], addToContainer);
            }
            instance = constructor.newInstance(objects);
        }

        if (addToContainer)
            classInstances.put(c, instance);
        return (T) instance;
    }

    private <T> T createInstance(Class<T> c, boolean addToContainer) throws Exception {
        T instance = constructInstance(c, addToContainer);
        addFields(c, instance, addToContainer);
        return instance;
    }

    private <T> void addFields(Class<T> c, Object instance, boolean addToContainer) throws Exception {
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
            fieldClassInstance = createInstance(fieldType, addToContainer);
            field.set(instance, fieldClassInstance);
        }
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