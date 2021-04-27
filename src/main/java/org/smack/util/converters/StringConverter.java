/**
 * $Id: 9bfbc75bb0cf247670487bb146bc1e8632bb4552 $
 * <p>
 * Unpublished work.
 * Copyright © 2021 Michael G. Binz
 */
package org.smack.util.converters;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.smack.util.ReflectionUtil;
import org.smack.util.ServiceManager;
import org.smack.util.StringUtil;
import org.smack.util.resource.ResourceConverter;
import org.smack.util.resource.ResourceMap;

/**
 * Offers conversions services for strings to arbitrary
 * types.
 *
 * @version $Revision$
 * @author Michael Binz
 */
public final class StringConverter {

    private final Logger LOG = Logger.getLogger(
            StringConverter.class.getName());
    private final HashMap<Class<?>, Converter<String, ?>> _registry =
            new HashMap<>();

    /**
     * Create an instance.  Use with {@link ServiceManager} to get the common
     * instance.
     */
    public StringConverter() {
        this.LOG.setLevel(Level.WARNING);

        for (final StringConverterExtension c : ServiceLoader.load(StringConverterExtension.class)) {
            c.extendTypeMap(this);
        }

        for (final ResourceConverter c : ServiceLoader.load(ResourceConverter.class)) {
            this.put(c.getType(), c);
        }
    }

    /**
     * Check if a converter for the passed class is available.
     * @param cl The class to convert to.
     * @return true if a converter is available.
     */
    public boolean containsKey(final Class<?> cl) {
        return this.getConverter(cl) != null;
    }

    /**
     * @param converter A converter to add to the list of known converters.
     */
    public <T> void put(final Class<T> cl, final Converter<String, T> f) {
        Objects.requireNonNull(cl);
        Objects.requireNonNull(f);

        this.LOG.info("Adding rc for: " + cl);

        // Directly ask the has table.  The outbound containsKey
        // triggers creation of entries.
        if (this._registry.containsKey(cl)) {
            this.LOG.warning("Duplicate resource converter for " + cl + ".");
        }

        this._registry.put(
                cl,
                f);
    }

    @Deprecated
    public <T> void put(final Class<T> cl, final ResourceConverter converter) {
        final Converter<String, T> c =
                s -> (T) converter.parseString(s, null);
        this.put(
                cl,
                c);

    }

    @Deprecated
    public <T> ResourceConverter get(final Class<T> cl) {
        if (!this.containsKey(cl)) {
            return null;
        }

        return new DynamicResourceConverter(cl, this._registry.get(cl));
    }

    /**
     * Get a conversion function.
     *
     * @param cl The conversion target class.
     * @return A converter function or null if none is available.
     */
    public <T> Converter<String, T> getConverter(final Class<T> cl) {
        return (Converter<String, T>) this._registry.computeIfAbsent(cl, this::synthesize);
    }

    /**
     * Convert a string to a target class.
     * @param <T> The target type.
     * @param cl The target type's class.
     * @param s The string to convert.
     * @return The conversion result.  This may be null, depending
     * on the converter.
     * @throws IllegalArgumentException In case of conversion failure.
     */
    public <T> T convert(final Class<T> cl, final String s) {
        if (!this.containsKey(cl)) {
            throw new IllegalArgumentException(
                    "No resource converter found for type: " + cl);
        }
        try {
            return (T) this._registry.get(cl).convert(s);
        } catch (final IllegalArgumentException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalArgumentException(
                    String.format(
                            "Cannot convert '%s' to %s: %s",
                            s,
                            cl.getName(),
                            e.getMessage()),
                    e);
        }
    }

    private <T> Converter<String, T> synthesizeEnum(final Class<T> cl) {
        this.LOG.info("Synthesize enum for: " + cl);
        return s -> ReflectionUtil.getEnumElement(cl, s);
    }

    private <T> Converter<String, T> synthesizeStringCtor(final Class<T> cl, final Constructor<T> ctor) {
        this.LOG.info("Synthesize string ctor for: " + cl);
        return s -> ctor.newInstance(s);
    }

    private <T> Converter<String, T> synthesizeArray(final Class<T> cl) {
        this.LOG.info("Synthesize array for: " + cl);

        final var componentConverter =
                this.getConverter(cl.getComponentType());

        return s -> {
            final String[] split = StringUtil.splitQuoted(s);

            final var result = Array.newInstance(
                    cl.getComponentType(), split.length);

            int idx = 0;
            for (final String c : split) {
                Array.set(
                        result,
                        idx++,
                        componentConverter.convert(c));
            }

            return (T) result;
        };
    }

    /**
     * Synthesizes missing converters.
     *
     * @param <T>
     * @param cl
     * @return
     */
    private <T> Converter<String, T> synthesize(final Class<T> cl) {
        if (cl.isEnum()) {
            return this.synthesizeEnum(cl);
        }

        if (cl.isArray() && this.getConverter(cl.getComponentType()) != null) {
            return this.synthesizeArray(cl);
        }

        final var stringCtor = ReflectionUtil.getConstructor(cl, String.class);
        if (stringCtor != null) {
            return this.synthesizeStringCtor(cl, stringCtor);
        }

        return null;
    }

    @FunctionalInterface
    public interface Converter<F, T> {

        T convert(F f)
                throws Exception;
    }

    public static class DynamicResourceConverter<T> extends ResourceConverter {

        private final Converter<String, T> _function;

        DynamicResourceConverter(final Class<T> cl, final Converter<String, T> f) {
            super(cl);

            this._function = f;
        }

        @Override
        public Object parseString(final String s, final ResourceMap r) throws Exception {
            return this._function.convert(s);
        }
    }
}
