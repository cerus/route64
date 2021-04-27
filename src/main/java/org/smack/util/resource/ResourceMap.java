/* $Id: ed0a576144eb444e4cd9bafa6cd123e44250effe $
 *
 * Utilities
 *
 * Released under Gnu Public License
 * Copyright © 2017 Michael G. Binz
 */
package org.smack.util.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import org.smack.util.Pair;
import org.smack.util.ServiceManager;
import org.smack.util.converters.StringConverter;


/**
 * A map holding all resources defined in the resources for
 * the passed class.  Resources for a class foo.bar.Elk are
 * defined in the property file foo.bar.resources.Elk.
 * <p>
 * A property named "color" in the above resource file is found
 * by the key 'color' and the key 'Elk.color'.
 * </p>
 *
 * @author Michael Binz
 */
public class ResourceMap extends HashMap<String, String> {

    private final String _bundleName;

    private final Class<?> _class;

    private ResourceMap(final Class<?> cl, final URL url, final ResourceBundle rb) {
        this._class =
                Objects.requireNonNull(cl);
        final String simpleName =
                this._class.getSimpleName();
        final Map<String, String> bundle =
                ResourceUtil.preprocessResourceBundle(
                        url, rb);
        this._bundleName =
                cl.getName();
        final String classPrefix =
                simpleName + ".";

        for (final String ck : bundle.keySet()) {
            final String value =
                    bundle.get(ck);

            if (ck.equals(classPrefix)) {
                throw new AssertionError("Invalid property name: " + classPrefix);
            }

            this.put(ck, value);
            if (ck.startsWith(classPrefix)) {
                this.put(
                        ck.substring(classPrefix.length()),
                        value);
            } else {
                this.put(
                        classPrefix + ck,
                        value);
            }
        }
    }

    public static ResourceMap getResourceMap(final Class<?> cl) {
        final Pair<URL, ResourceBundle> crb =
                ResourceUtil.getClassResourcesImpl(
                        Objects.requireNonNull(cl));
        if (crb == null) {
            return null;
        }

        return new ResourceMap(cl, crb.left, crb.right);
    }

    /**
     * @return The name of the underlying resource bundle.
     */
    public String getName() {
        return this._bundleName;
    }

    /**
     * @return The class loader of the associated class.
     */
    public ClassLoader getClassLoader() {
        return this._class.getClassLoader();
    }

    /**
     * @return The class that this resource map holds resources for.
     */
    public Class<?> getResourceClass() {
        return this._class;
    }

    /**
     * @param name The resource name.
     *
     * @return A stream on the content of the result.
     *
     * @throws IOException In case of an error.
     */
    public InputStream getResourceAsStream(final String name) throws IOException {
        final InputStream result = this._class.getClassLoader().getResourceAsStream(
                name);

        if (result != null) {
            return result;
        }

        return
                this._class.getModule().getResourceAsStream(name);
    }

    /**
     * Convert the passed key to a target type.
     *
     * @param <T>        The expected target type.
     * @param targetType The expected result type.
     * @param key        The property key to convert.
     *
     * @return The conversion result.
     */
    public <T> T getAs(final String key, final Class<T> targetType)
            throws Exception {
        final String resolved =
                this.get(key);
        if (resolved == null) {
            throw new IllegalArgumentException("Key not found: " + key);
        }

        final var converter = ServiceManager.getApplicationService(
                StringConverter.class);

        return converter.convert(
                targetType,
                this.get(key));
    }
}
