/* $Id: 45abd4ad60436ffde5035ec6765ee95ab3a43e60 $
 *
 * Utilities
 *
 * Released under Gnu Public License
 * Copyright © 2017 Michael G. Binz
 */
package org.smack.util.resource;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Logger;
import org.smack.util.ReflectionUtil;
import org.smack.util.ServiceManager;
import org.smack.util.StringUtil;
import org.smack.util.collections.WeakMapWithProducer;
import org.smack.util.converters.StringConverter;
import org.smack.util.converters.StringConverter.Converter;

/**
 * A ResourceManager.
 *
 * @author Michael Binz
 * @version $Rev$
 */
public class ResourceManager {

    private final static Logger LOG =
            Logger.getLogger(ResourceManager.class.getName());
    /**
     * Used as a default null value for the @Resource.dflt field. Never
     * modify.
     */
    static final private String DFLT_NULL = "313544b196b54c29a26e43bdf204b023";
    private final StringConverter _converters =
            ServiceManager.getApplicationService(StringConverter.class);
    private final WeakMapWithProducer<Class<?>, ResourceMap> _resourceMapCache =
            new WeakMapWithProducer<>(ResourceMap::getResourceMap);
    private final WeakHashMap<Class<?>, Boolean> staticInjectionDone =
            new WeakHashMap<>();

    /**
     * Create an instance.  Commonly done via the ServiceManager.
     */
    public ResourceManager() {
    }

    /**
     * Get the normalized value of the annotation's dflt field.
     *
     * @param r The annotation reference.
     *
     * @return The normalized value which includes null if not set.
     */
    private static String getDefaultField(final Resource r) {
        final var result = r.dflt();

        if (DFLT_NULL.equals(result)) {
            return null;
        }

        return result;
    }

    /**
     * @param converter A converter to add to the list of known converters.
     */
    public <T> void addConverter(final Class<T> cl, final Converter<String, T> f) {
        this._converters.put(
                cl,
                f);
    }

    /**
     * Get a converter for a target class.
     *
     * @param <T> The target type.
     * @param cl  The target class.
     *
     * @return A converter, null if none is found.
     */
    public <T> Converter<String, T> getConverter(final Class<T> cl) {
        return this._converters.getConverter(cl);
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
                setter.invoke(
                        bean,
                        this._converters.convert(
                                c.getPropertyType(),
                                map.get(currentKey)));
            } catch (final InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }

            if (definedKeys.size() == 0) {
                return;
            }
        }

        for (final String c : definedKeys) {
            LOG.warning(String.format(
                    "Key '%s' defined in map does not match property.", c));
        }
    }

    /**
     * @param cl The class for which a resource map is requested.
     *
     * @return The resource map for the passed class. null if
     * no resources are found.
     */
    public ResourceMap getResourceMap(final Class<?> cl) {
        return this._resourceMapCache.get(cl);
    }

    /**
     * @param cl The class for which a resource map is requested.
     *
     * @return The resource map for the passed class. An empty
     * map if no resources were defined.
     */
    public Map<String, String> getResourceMap2(final Class<?> cl) {
        final var result = this._resourceMapCache.get(cl);
        if (result != null) {
            return result;
        }

        return Collections.emptyMap();
    }

    /**
     * Inject fields annotated by @Resource on the passed object.
     *
     * @param o The target object for the injection.  If this is
     *          a class object, then only the static fields are injected.
     */
    public void injectResources(final Object o) {
        if (o instanceof Class) {
            this.injectResources(null, (Class<?>) o);
        } else {
            this.injectResources(o, o.getClass());
        }
    }

    public void injectResources(final Object instance, final Class<?> cl) {
        if (instance == null && this.staticInjectionDone.containsKey(cl)) {
            return;
        }

        final var map =
                this.getResourceMap2(cl);
        // Note that it may be valid that map is empty, as long
        // as all @Resources offer a dflt value.

        ReflectionUtil.processAnnotation(
                Resource.class,
                cl::getDeclaredFields,
                s -> {
                    if (instance == null) {
                        return Modifier.isStatic(s.getModifiers());
                    }
                    return true;
                },
                (f, r) -> {

                    if (Modifier.isStatic(f.getModifiers()) &&
                            this.staticInjectionDone.containsKey(cl)) {
                        return;
                    }

                    String name = r.name();

                    if (StringUtil.isEmpty(name)) {
                        name = String.format(
                                "%s.%s",
                                cl.getSimpleName(),
                                f.getName());
                    }

                    String value = map.get(name);

                    // If we got no value, get the Resource default
                    // definition.
                    if (value == null) {
                        value = getDefaultField(r);
                        // If the resource default definition is set to
                        // the empty string this means not to touch the field.
                        if (StringUtil.EMPTY_STRING.equals(value)) {
                            return;
                        }
                    }

                    // If no value found bail out.
                    if (value == null) {
                        final var msg = String.format(
                                "No resource key found for field '%s#%s'.",
                                f.getDeclaringClass(),
                                f.getName());
                        throw new MissingResourceException(
                                msg,
                                f.getDeclaringClass().toString(),
                                name);
                    }

                    this.performInjection(instance, f, value);
                });

        this.staticInjectionDone.put(cl, Boolean.TRUE);
    }

    private void performInjection(
            final Object instance,
            final Field f,
            final String resource) {
        final var value = this._converters.convert(
                f.getType(),
                resource);
        try {
            if (!f.canAccess(instance)) {
                f.setAccessible(true);
            }

            f.set(instance, value);
        } catch (final Exception e) {
            throw new RuntimeException(String.format(
                    "Injecting %s failed: %s",
                    f.toString(),
                    e.getMessage()),
                    e);
        }
    }

    /**
     * The Resource annotation marks a field that is injected by
     * ResourceManager.
     * If a field is annotated and no definition is found in the property file
     * then this is an error and an exception is thrown.
     * If a dflt value is provided then this is used and no error is
     * signaled.  If dflt is set to the empty string, then no injection
     * is performed.
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
         * @return A default value for this resource.
         */
        String dflt() default DFLT_NULL;
    }
}
