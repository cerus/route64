/* $Id: 56d59c11a6a03c8abed20eae6d5238b12fab4ad5 $
 *
 * Utilities
 *
 * Released under Gnu Public License
 * Copyright © 2017 Michael G. Binz
 */
package org.jdesktop.util;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.smack.util.Pair;
import org.smack.util.StringUtil;
import org.smack.util.collections.WeakMapWithProducer;

/**
 * A ResourceManager.
 *
 * @author Michael Binz
 * @version $Rev$
 */
@Deprecated
public class ResourceManager {

    private final static Logger LOG =
            Logger.getLogger(ResourceManager.class.getName());
    private final ResourceConverterRegistry _converters =
            ServiceManager.getApplicationService(ResourceConverterRegistry.class);
    private final WeakMapWithProducer<Class<?>, ResourceMap> _resourceMapCache =
            new WeakMapWithProducer<>(ResourceMap::new);
    private final WeakHashMap<Class<?>, ResourceMap> staticInjectionDone =
            new WeakHashMap<>();

    /**
     * Create an instance.  Commonly done via the ServiceManager.
     */
    public ResourceManager() {
        for (final ResourceConverterExtension c : ServiceLoader.load(ResourceConverterExtension.class)) {
            c.extendTypeMap(this._converters);
        }

        for (final ResourceConverter c : ServiceLoader.load(ResourceConverter.class)) {
            this.addConverter(c);
        }
    }

    /**
     * @param converter A converter to add to the list of known converters.
     */
    public void addConverter(final ResourceConverter converter) {
        if (this._converters.containsKey(converter.getType())) {
            LOG.warning("Duplicate resource converter for " + converter.getType() + ", " + converter.getClass());
        }

        this._converters.put(converter.getType(), converter);
    }

    /**
     * Inject the passed bean's properties from the passed map. The prefix is
     * used to find the configuration keys in the map. Keys in the
     * map have to look like prefix.propertyName. The dot is added to
     * the prefix.
     *
     * @param bean   The bean whose properties are injected.
     * @param prefix The prefix used to filter the map's keys.
     * @param map    Inject the passed bean's properties from this map.
     */
    public void injectProperties(final Object bean, String prefix, final ResourceMap map) {
        final BeanInfo beanInfo;
        try {
            beanInfo = Introspector.getBeanInfo(
                    bean.getClass());
        } catch (final IntrospectionException e) {
            throw new IllegalArgumentException("Introspection failed.", e);
        }

        // Add the dot.
        prefix += ".";

        final Set<String> definedKeys = new HashSet<>();
        for (final String c : map.keySet()) {
            if (c.startsWith(prefix)) {
                definedKeys.add(c);
            }
        }

        if (definedKeys.size() == 0) {
            return;
        }

        for (final PropertyDescriptor c : beanInfo.getPropertyDescriptors()) {
            final Method setter = c.getWriteMethod();

            // Skip read-only properties.
            if (setter == null) {
                continue;
            }

            final String currentKey = prefix + c.getName();
            if (!definedKeys.contains(currentKey)) {
                continue;
            }

            definedKeys.remove(currentKey);

            try {
                // This implicitly transforms the key's value.
                setter.invoke(
                        bean,
                        this.convert(
                                c.getPropertyType(),
                                map.get(currentKey),
                                map));
//                        map.get( currentKey, c.getPropertyType() ) );
            } catch (final IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (final InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }

            if (definedKeys.size() == 0) {
                return;
            }
        }

        for (final String c : definedKeys) {
            LOG.warning(String.format("Key '%s' defined in map does not match property.", c));
        }
    }

    public void injectResources(final Object o) {
        if (o instanceof Class) {
            this.injectResources(null, (Class<?>) o);
        } else {
            this.injectResources(o, o.getClass());
        }
    }

    /**
     * @param clazz The class a resource map is requested for.
     *
     * @return The resource map for the passed class.
     */
    public ResourceMap getResourceMap(final Class<?> clazz) {
        return this._resourceMapCache.get(clazz);
    }

    public void injectResources(final Object instance, final Class<?> cIass) {
        if (instance == null && this.staticInjectionDone.containsKey(cIass)) {
            return;
        }

        final List<Pair<Field, Resource>> fields = ReflectionUtil.getAnnotatedFields(
                cIass,
                Resource.class,
                instance == null ? Modifier.STATIC : 0);

        if (fields.isEmpty()) {
            return;
        }

        final ResourceMap rb =
                this.getResourceMap(cIass);

        if (rb.isEmpty()) {
            // @Resource annotations exist, but no property file.
            LOG.severe("No resources found for class " + cIass.getName());
            return;
        }

        for (final Pair<Field, Resource> c : fields) {
            final Field f = c.left;

            if (Modifier.isStatic(f.getModifiers()) &&
                    this.staticInjectionDone.containsKey(cIass)) {
                continue;
            }

            final Resource r = c.right;

            String name = r.name();

            if (StringUtil.isEmpty(name)) {
                name = String.format("%s.%s", cIass.getSimpleName(), f.getName());
            }

            final String value = rb.get(name);

            if (value == null) {
                final String message = String.format(
                        "No resource key found for field '%s#%s'.",
                        f.getDeclaringClass(),
                        f.getName());
                LOG.severe(
                        message);
                continue;
            }

            try {
                this.performInjection(instance, f, value, rb);
            } catch (final Exception e) {
                LOG.log(Level.SEVERE, "Injection failed for field " + f.getName(), e);
            }
        }

        this.staticInjectionDone.put(cIass, rb);
    }

    /**
     * @param instance
     * @param f
     * @param value
     * @param map
     *
     * @throws Exception
     */
    private void performInjection(
            final Object instance,
            final Field f,
            final String value,
            final ResourceMap map) throws Exception {
        final boolean accessible = f.isAccessible();

        try {
            if (!accessible) {
                f.setAccessible(true);
            }

            this.performInjectionImpl(instance, f, value, map);
        } catch (final Exception e) {
            final String msg =
                    String.format(
                            "Resource init for %s failed.",
                            f.getName());

            LOG.log(
                    Level.WARNING, msg, e);
        } finally {
            if (f.isAccessible() != accessible) {
                f.setAccessible(accessible);
            }
        }
    }

    /**
     * @param instance
     * @param f
     * @param resource
     * @param map
     *
     * @throws Exception
     */
    private void performInjectionImpl(
            final Object instance,
            final Field f,
            final String resource,
            final ResourceMap map) throws Exception {
        final Class<?> targetType = f.getType();

        ResourceConverter converter =
                this._converters.get(targetType);

        if (converter != null) {
            f.set(
                    instance,
                    converter.parseString(resource, map));

            return;
        }

        // Check if we can synthesize an array resource converter.
        if (targetType.isArray()) {
            converter = this._converters.get(targetType.getComponentType());
            if (converter != null) {
                converter = new ArrayResourceConverter(converter, targetType);
                f.set(instance, converter.parseString(resource, map));
                return;
            }
        }

        // Check if we can synthesize a constructor-based resource converter.
        if (ReflectionUtil.getConstructor(targetType, String.class) != null) {
            converter = new ConstructorResourceConverter(
                    targetType.getConstructor(String.class),
                    targetType);

            f.set(instance, converter.parseString(resource, map));

            return;
        }

        LOG.warning("No resource converter found for type: " + targetType);
    }

    <T> T convert(final Class<T> targetType, final String toConvert, final ResourceMap map) {
        ResourceConverter converter =
                this._converters.get(targetType);

        if (converter == null) {
            converter = this.synthArrayConverter(targetType);
        }

        if (converter == null) {
            converter = this.synthConstructorConverter(targetType);
        }

        if (converter == null) {
            throw new IllegalArgumentException("No resource converter found for type: " + targetType);
        }

        try {
            return (T) converter.parseString(toConvert, map);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Conversion failed.", e);
        }
    }

    private ResourceConverter synthArrayConverter(final Class<?> targetType) {
        if (!targetType.isArray()) {
            return null;
        }

        final ResourceConverter rc = this._converters.get(
                targetType.getComponentType());

        if (rc == null) {
            return null;
        }

        return new ArrayResourceConverter(rc, targetType);
    }

    private ResourceConverter synthConstructorConverter(final Class<?> targetType) {
        final Constructor<?> ctor =
                ReflectionUtil.getConstructor(targetType, String.class);

        if (ctor == null) {
            return null;
        }

        return new ConstructorResourceConverter(
                ctor,
                targetType);
    }

    /**
     * The Resource annotation marks a resource that is needed
     * by the application.  This annotation may be applied to an
     * application component field. <p>
     */
    @Target({FIELD})
    @Retention(RUNTIME)
    public @interface Resource {

        /**
         * @return The name of the resource.  For field annotations,
         * the default is the field name.
         */
        String name() default StringUtil.EMPTY_STRING;

        /**
         * @return Description of this resource.
         */
        String description() default StringUtil.EMPTY_STRING;
    }

    private static class ArrayResourceConverter extends ResourceConverter {

        private final ResourceConverter _delegate;

        ArrayResourceConverter(final ResourceConverter delegate, final Class<?> type) {
            super(type);

            if (!type.isArray()) {
                throw new IllegalArgumentException();
            }

            this._delegate = delegate;
        }

        @Override
        public Object parseString(final String s, final ResourceMap r)
                throws Exception {
            final String[] split = StringUtil.splitQuoted(s);

            final Object result = Array.newInstance(
                    this.getType().getComponentType(), split.length);

            int idx = 0;
            for (final String c : split) {
                Array.set(
                        result,
                        idx++,
                        this._delegate.parseString(
                                c,
                                r));
            }

            return result;
        }
    }

    private static class ConstructorResourceConverter extends ResourceConverter {

        private final Constructor<?> _ctor;

        ConstructorResourceConverter(final Constructor<?> delegate, final Class<?> type) {
            super(type);

            this._ctor = delegate;
        }

        @Override
        public Object parseString(final String s, final ResourceMap r)
                throws Exception {
            return this._ctor.newInstance(s);
        }
    }
}
