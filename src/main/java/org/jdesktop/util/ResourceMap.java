/* $Id: 9a2ee847f1c7f256cf3fe2deffeaab0074b57e1c $
 *
 * Utilities
 *
 * Released under Gnu Public License
 * Copyright © 2017 Michael G. Binz
 */
package org.jdesktop.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import org.smack.util.ResourceUtil;
import org.smack.util.StringUtil;


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
@Deprecated
public class ResourceMap extends HashMap<String, String> {

    private final String _bundleName;

    private final Class<?> _class;

    private final String _resourcePath;

    public ResourceMap(final Class<?> cl) {
        this._class =
                Objects.requireNonNull(cl);
        final String simpleName =
                this._class.getSimpleName();

        final ResourceBundle crb =
                ResourceUtil.getClassResources(cl);

        if (crb == null) {
            this._bundleName = StringUtil.EMPTY_STRING;
            this._resourcePath = StringUtil.EMPTY_STRING;
            return;
        }

        this._bundleName =
                crb.getBaseBundleName();
        JavaUtil.Assert(
                this._bundleName.endsWith(simpleName));

        this._resourcePath =
                this._bundleName.substring(
                        0, this._bundleName.length() -
                                simpleName.length()).replace('.', '/');

        final Map<String, String> bundle =
                ResourceUtil.preprocessResourceBundle(
                        crb);

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

    /**
     * Looks up the value for the qualified name. For a key {@code x} and a
     * map for {@code org.jdesktop.Test} the qualified name is {@code Test.x}.
     *
     * @param key The requested key.
     *
     * @return The associated value.
     */
    public Optional<String> getQualified(final String key) {
        return Optional.ofNullable(
                this.get(this._class.getSimpleName() + "." + key));
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
     * @return A resource dir, slash-separated, with a trailing slash.
     * For class org.jdesktop.Test the resource dir
     * is org/jdesktop/resources/. Used for the resolution of
     * secondary resources like icons. If no underlying resource
     * bundle existed, then this is null.
     */
    public String getResourceDir() {
        return this._resourcePath;
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
    public <T> T getAs(final String key, final Class<T> targetType) {
        final String resolved =
                this.get(key);
        if (resolved == null) {
            throw new IllegalArgumentException("Key not found: " + key);
        }

        final ResourceManager rm = ServiceManager.getApplicationService(
                ResourceManager.class);

        return rm.convert(
                targetType,
                this.get(key),
                this);
    }
}
