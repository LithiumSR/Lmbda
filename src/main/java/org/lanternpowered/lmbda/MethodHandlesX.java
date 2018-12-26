/*
 * This file is part of Lmbda, licensed under the MIT License (MIT).
 *
 * Copyright (c) LanternPowered <https://www.lanternpowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.lanternpowered.lmbda;

import static java.util.Objects.requireNonNull;

import org.objectweb.asm.ClassReader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ReflectPermission;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.Arrays;

/**
 * A utility class full of magic related to {@link MethodHandles}.
 */
@SuppressWarnings("ThrowableNotThrown")
public final class MethodHandlesX {

    private static final PrivateLookupProvider privateLookupProvider = loadPrivateLookupProvider();
    private static final DefineClass defineClassFunction = loadDefineClass();

    /**
     * Gets a lookup object with full capabilities to emulate all supported bytecode
     * behaviors, including private access, on a target class.
     *
     * <p>If there is a security manager, its checkPermission method is called
     * to check ReflectPermission("suppressAccessChecks").</p>
     *
     * <p>When using java 9+, see
     * https://docs.oracle.com/javase/9/docs/api/java/lang/invoke/MethodHandles.html#privateLookupIn-java.lang.Class-java.lang.invoke.MethodHandles.Lookup-</p>
     *
     * @param targetClass The target class for which private access should be acquired
     * @param lookup The caller lookup object
     * @return A lookup object for the target class, with private access
     * @throws IllegalAccessException If the lookup doesn't have private access to the target class
     */
    public static MethodHandles.Lookup privateLookupIn(Class<?> targetClass, MethodHandles.Lookup lookup) throws IllegalAccessException {
        requireNonNull(targetClass, "targetClass");
        requireNonNull(lookup, "lookup");
        return privateLookupProvider.privateLookupIn(targetClass, lookup);
    }

    /**
     * Defines a {@link Class} to the same {@link ClassLoader} and in the same runtime package
     * and {@link ProtectionDomain} as the {@link java.lang.invoke.MethodHandles.Lookup}'s lookup class.
     *
     * @param lookup The lookup of which the target class will be used to define the class in
     * @param byteCode The byte code of the class to define
     * @return The defined class
     * @throws IllegalAccessException If the lookup doesn't have package private access to the target package
     */
    public static Class<?> defineClass(MethodHandles.Lookup lookup, byte[] byteCode) throws IllegalAccessException {
        requireNonNull(lookup, "lookup");
        requireNonNull(byteCode, "byteCode");
        return defineClassFunction.defineClass(lookup, byteCode);
    }

    /**
     * Similar to {@link java.lang.invoke.MethodHandles.Lookup#findStaticSetter(Class, String, Class)}
     * but allows modifications to final fields.
     *
     * @param lookup The caller lookup object that has access to the target field
     * @param target The target class where the field is located
     * @param fieldName The field name
     * @param fieldType The field type
     * @return The static setter method handle
     * @throws IllegalAccessException If the given lookup doesn't have access to the target field
     * @throws NoSuchFieldException If the field couldn't be found
     * @deprecated See {@link FieldAccess#makeAccessible(Field)}.
     */
    @Deprecated
    public static MethodHandle findFinalStaticSetter(MethodHandles.Lookup lookup, Class<?> target, String fieldName, Class<?> fieldType)
            throws IllegalAccessException, NoSuchFieldException {
        requireNonNull(lookup, "lookup");
        requireNonNull(target, "target");
        requireNonNull(fieldName, "fieldName");
        requireNonNull(fieldType, "fieldType");
        final Field field = AccessController.doPrivileged((PrivilegedAction<Field>) () -> Arrays.stream(target.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getName().equals(fieldName) && f.getType() == fieldType)
                .findFirst().orElse(null));
        if (field == null) {
            throw new NoSuchFieldException("no such field: " + target.getName() + "." + fieldName + "/" + fieldType.getName() + "/putStatic");
        }
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            FieldAccess.makeAccessible(field);
            return null;
        });
        return lookup.unreflectSetter(field);
    }

    /**
     * Similar to {@link java.lang.invoke.MethodHandles.Lookup#findSetter(Class, String, Class)}
     * but allows modifications to final fields.
     *
     * @param lookup The caller lookup object that has access to the target field
     * @param target The target class where the field is located
     * @param fieldName The field name
     * @param fieldType The field type
     * @return The setter method handle
     * @throws IllegalAccessException If the given lookup doesn't have access to the target field
     * @throws NoSuchFieldException If the field couldn't be found
     * @deprecated See {@link FieldAccess#makeAccessible(Field)}.
     */
    @Deprecated
    public static MethodHandle findFinalSetter(MethodHandles.Lookup lookup, Class<?> target, String fieldName, Class<?> fieldType)
            throws IllegalAccessException, NoSuchFieldException {
        requireNonNull(lookup, "lookup");
        requireNonNull(target, "target");
        requireNonNull(fieldName, "fieldName");
        requireNonNull(fieldType, "fieldType");
        final Field field = AccessController.doPrivileged((PrivilegedAction<Field>) () -> Arrays.stream(target.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && f.getName().equals(fieldName) && f.getType() == fieldType)
                .findFirst().orElse(null));
        if (field == null) {
            throw new NoSuchFieldException("no such field: " + target.getName() + "." + fieldName + "/" + fieldType.getName() + "/putField");
        }
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            FieldAccess.makeAccessible(field);
            return null;
        });
        return lookup.unreflectSetter(field);
    }

    /**
     * Similar to {@link java.lang.invoke.MethodHandles.Lookup#unreflectSetter(Field)}
     * but allows modifications to final fields.
     *
     * @param lookup The caller lookup object that has access to the given field
     * @param field The field to unreflect
     * @return The (static) setter method handle
     * @throws IllegalAccessException If the given lookup doesn't have access to the target field
     * @deprecated See {@link FieldAccess#makeAccessible(Field)}.
     */
    @Deprecated
    public static MethodHandle unreflectFinalSetter(MethodHandles.Lookup lookup, Field field) throws IllegalAccessException {
        requireNonNull(lookup, "lookup");
        requireNonNull(field, "field");
        if (!field.isAccessible()) {
            throw new IllegalAccessException("no access to field: " + field.getDeclaringClass().getName()
                    + "." + field.getName() + "/" + field.getType().getName());
        }
        final Field newField = AccessController.doPrivileged((PrivilegedAction<Field>) () ->
                Arrays.stream(field.getDeclaringClass().getDeclaredFields())
                        .filter(f -> f.getName().equals(field.getName()) && f.getType() == field.getType())
                        .findFirst().orElse(null));
        if (newField == null) {
            throw new IllegalStateException(field.toString());
        }
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            FieldAccess.makeAccessible(newField);
            return null;
        });
        return lookup.unreflectSetter(field);
    }

    private interface PrivateLookupProvider {

        MethodHandles.Lookup privateLookupIn(Class<?> targetClass, MethodHandles.Lookup lookup) throws IllegalAccessException;
    }

    /**
     * Loads the {@link PrivateLookupProvider}.
     *
     * @return The private lookup provider
     */
    private static PrivateLookupProvider loadPrivateLookupProvider() {
        return AccessController.doPrivileged((PrivilegedAction<PrivateLookupProvider>) () -> {
            if (DirectPrivateLookupProvider.findMethodHandle() != null) {
                return new DirectPrivateLookupProvider();
            }
            return new TrustedPrivateLookupProvider();
        });
    }

    static final class TrustedPrivateLookupProvider implements PrivateLookupProvider {

        final MethodHandles.Lookup trustedLookup = loadTrustedLookup();

        private static MethodHandles.Lookup loadTrustedLookup() {
            try {
                // See if we can find the trusted lookup field directly
                final Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
                field.setAccessible(true);
                return (MethodHandles.Lookup) field.get(null);
            } catch (NoSuchFieldException e) {
                // Not so much luck, let's try to hack something together another way
                // Get a public lookup and create a new instance
                final MethodHandles.Lookup lookup = MethodHandles.publicLookup().in(Object.class);

                try {
                    final Field field = MethodHandles.Lookup.class.getDeclaredField("allowedModes");
                    field.setAccessible(true);

                    final Field mField = Field.class.getDeclaredField("modifiers");
                    mField.setAccessible(true);
                    mField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

                    // The field that holds the trusted access mode
                    final Field trustedAccessModeField = MethodHandles.Lookup.class.getDeclaredField("TRUSTED");
                    trustedAccessModeField.setAccessible(true);

                    // Apply the modifier to the lookup instance
                    field.set(lookup, trustedAccessModeField.get(null));
                } catch (Exception e1) {
                    throw new IllegalStateException("Unable to create a trusted method handles lookup", e1);
                }

                return lookup;
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Unable to create a trusted method handles lookup", e);
            }
        }

        @Override
        public MethodHandles.Lookup privateLookupIn(Class<?> targetClass, MethodHandles.Lookup lookup) {
            final SecurityManager securityManager = System.getSecurityManager();
            if (securityManager != null) {
                securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
            }
            return this.trustedLookup.in(targetClass);
        }
    }

    private static final class DirectPrivateLookupProvider implements PrivateLookupProvider {

        private static final MethodHandle methodHandle = findMethodHandle();

        static MethodHandle findMethodHandle() {
            try {
                return MethodHandles.publicLookup().findVirtual(MethodHandles.class, "privateLookupIn",
                        MethodType.methodType(MethodHandles.Lookup.class, Class.class, MethodHandles.Lookup.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                return null;
            }
        }

        @Override
        public MethodHandles.Lookup privateLookupIn(Class<?> targetClass, MethodHandles.Lookup lookup) {
            return doUnchecked(() -> (MethodHandles.Lookup) requireNonNull(methodHandle).invoke(targetClass, lookup));
        }
    }

    private interface DefineClass {

        Class<?> defineClass(MethodHandles.Lookup lookup, byte[] byteCode) throws IllegalAccessException;
    }

    private static DefineClass loadDefineClass() {
        return AccessController.doPrivileged((PrivilegedAction<DefineClass>) () -> {
            final MethodHandle methodHandle = Java9DefineClass.findMethodHandle();
            if (methodHandle != null) {
                return new Java9DefineClass();
            }
            return new ReflectClassLoaderDefineClass();
        });
    }

    /**
     * This {@link DefineClass} will be used in Java 9+
     */
    private static final class Java9DefineClass implements DefineClass {

        private static final MethodHandle methodHandle = findMethodHandle();

        static MethodHandle findMethodHandle() {
            try {
                return MethodHandles.publicLookup().findVirtual(MethodHandles.Lookup.class, "defineClass",
                        MethodType.methodType(Class.class, byte[].class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                return null;
            }
        }

        @Override
        public Class<?> defineClass(MethodHandles.Lookup lookup, byte[] byteCode) {
            return doUnchecked(() -> (Class<?>) requireNonNull(methodHandle).invoke(lookup, byteCode));
        }
    }

    private static final class ReflectClassLoaderDefineClass implements DefineClass {

        private static final MethodHandle methodHandle = doUnchecked(() ->
                ((TrustedPrivateLookupProvider) privateLookupProvider).trustedLookup.findVirtual(ClassLoader.class, "defineClass",
                        MethodType.methodType(Class.class, String.class, byte[].class, int.class, int.class, ProtectionDomain.class)));

        @Override
        public Class<?> defineClass(MethodHandles.Lookup lookup, byte[] byteCode) {
            final SecurityManager securityManager = System.getSecurityManager();
            if (securityManager != null) {
                securityManager.checkPermission(new ReflectPermission("defineClass"));
            }
            if ((lookup.lookupModes() & MethodHandles.Lookup.PACKAGE) == 0) {
                throwUnchecked(new IllegalAccessException("Lookup does not have PACKAGE access"));
            }
            final String className;
            try {
                final ClassReader classReader = new ClassReader(byteCode);
                className = classReader.getClassName().replace('/', '.');
            } catch (RuntimeException e) {
                final ClassFormatError classFormatError = new ClassFormatError();
                classFormatError.initCause(e);
                throw classFormatError;
            }
            final Class<?> lookupClass = lookup.lookupClass();
            final String packageName = getPackageName(className);
            final String lookupPackageName = getPackageName(lookupClass);
            if (!packageName.equals(lookupPackageName)) {
                throw new IllegalArgumentException("Class not in same package as lookup class");
            }
            return AccessController.doPrivileged((PrivilegedAction<Class<?>>) () -> {
                final ClassLoader classLoader = lookupClass.getClassLoader();
                final ProtectionDomain protectionDomain = lookupClass.getProtectionDomain();
                return doUnchecked(() -> (Class<?>) methodHandle.invoke(classLoader, className,
                        byteCode, 0, byteCode.length, protectionDomain));
            });
        }
    }

    private static String getPackageName(Class<?> theClass) {
        while (theClass.isArray()) {
            theClass = theClass.getComponentType();
        }
        if (theClass.isPrimitive()) {
            return "java.lang";
        }
        return getPackageName(theClass.getName());
    }

    private static String getPackageName(String className) {
        final int index = className.lastIndexOf('.');
        return index == -1 ? "" : className.substring(0, index);
    }

    static <T> T doUnchecked(ThrowableSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            throw throwUnchecked(t);
        }
    }

    interface ThrowableSupplier<T> {

        T get() throws Throwable;
    }

    /**
     * Throws the {@link Throwable} as an unchecked exception.
     *
     * @param t The throwable to throw
     * @return A runtime exception
     */
    static RuntimeException throwUnchecked(Throwable t) {
        throwUnchecked0(t);
        throw new AssertionError("Unreachable.");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked0(Throwable t) throws T {
        throw (T) t;
    }

    private MethodHandlesX() {
    }
}
