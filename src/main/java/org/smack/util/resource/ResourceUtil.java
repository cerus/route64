/* $Id: 1428c5b3353127fe7f82c907fc222033459ae82a $
 *
 * Copyright © 2014 Michael G. Binz
 */
package org.smack.util.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import org.smack.util.JavaUtil;
import org.smack.util.Pair;
import org.smack.util.StringUtil;

/**
 * Resource Bundle helpers.
 *
 * @author Michael Binz
 * @version $Rev$
 */
public class ResourceUtil {

    /**
     * Forbid instantiation.
     */
    private ResourceUtil() {
        throw new AssertionError();
    }

    /**
     * Populates the passed Map with the preprocessed values from the passed
     * resource bundle.
     *
     * @param bundle The resource bundle whose entries are processed.
     *
     * @return The requested resource bundle or {@code null} if the bundle
     * did not exist.
     */
    static Map<String, String> preprocessResourceBundle(
            final URL url, final ResourceBundle bundle) {
        final Map<String, String> result = new HashMap<>();

        var urlPrefix = url.toExternalForm();

        final var lastSlash = urlPrefix.lastIndexOf('/');
        if (lastSlash > 0) {
            urlPrefix = urlPrefix.substring(0, lastSlash + 1);
        }

        JavaUtil.Assert(urlPrefix.endsWith("/"));

        for (final String key : bundle.keySet()) {
            var value = bundle.getString(key);

            if (value.startsWith("@")) {
                value =
                        urlPrefix +
                                value.substring(1);
            } else {
                value = evaluateStringExpression(
                        value,
                        bundle);
            }

            result.put(
                    key,
                    value);
        }

        return result;
    }

    /**
     * Evaluates a string expression in the context of a passed environment used
     * to look up token definitions.
     * <p>
     * Given the following resources:
     *
     * <pre><code>
     * hello = Hello
     * world = World
     * place = ${world}
     * </code></pre>
     * <p>
     * The value of evaluateStringExpression("${hello} ${place}") would be
     * "Hello World". The value of ${null} is null.
     *
     * @param expr The expression to evaluate.
     * @param env  The resource bundle to use for token look-up.
     *
     * @return The evaluated expression.
     */
    private static String evaluateStringExpression(
            final String expr,
            final ResourceBundle env) {
        if (!expr.contains("${")) {
            return expr;
        }

        if (expr.trim().equals("${null}")) {
            return null;
        }

        final StringBuilder result = new StringBuilder();
        int i0 = 0, i1;
        while ((i1 = expr.indexOf("${", i0)) != -1) {
            if ((i1 == 0) || ((i1 > 0) && (expr.charAt(i1 - 1) != '\\'))) {
                final int i2 = expr.indexOf("}", i1);
                if ((i2 != -1) && (i2 > i1 + 2)) {
                    result.append(expr.substring(i0, i1));
                    final String k = expr.substring(i1 + 2, i2);

                    if (env.containsKey(k)) {
                        final String resolved = env.getObject(k).toString();
                        // The resolved string is again evaluated.
                        result.append(evaluateStringExpression(
                                resolved,
                                env));
                    } else {
                        final String msg = String.format(
                                "no value for \"%s\" in \"%s\"", k, expr);
                        throw new LookupException(msg, k, String.class);
                    }

                    i0 = i2 + 1; // skip trailing "}"
                } else {
                    final String msg =
                            String.format("no closing brace in \"%s\"", expr);
                    throw new LookupException(msg, "<not found>", String.class);
                }
            } else { // we've found an escaped variable - "\${"
                result.append(expr.substring(i0, i1 - 1));
                result.append("${");
                i0 = i1 + 2; // skip past "${"
            }
        }
        result.append(expr.substring(i0));
        return result.toString();
    }

    private static boolean stringsValid(final String... strings) {
        for (final var c : strings) {
            if (StringUtil.isEmpty(c)) {
                return false;
            }
        }
        return true;
    }

    private static URL findResourceBundle(String baseName, final Module module) {
        if (StringUtil.isEmpty(baseName)) {
            throw new IllegalArgumentException("basename");
        }
        Objects.requireNonNull(module);

        baseName = baseName.replace(".", "/");

        final Locale locale = Locale.getDefault();

        final var language = locale.getLanguage();
        final var script = locale.getScript();
        final var country = locale.getCountry();
        final var variant = locale.getVariant();

        final ArrayList<String> toCheck = new ArrayList<>();

//        baseName + "_" + language + "_" + script + "_" + country + "_" + variant
        if (stringsValid(language, script, country, variant)) {
            toCheck.add(StringUtil.concatenate(
                    "_",
                    new String[] {baseName, language, script, country, variant}));
        }

//        baseName + "_" + language + "_" + script + "_" + country
        if (stringsValid(language, script, country)) {
            toCheck.add(StringUtil.concatenate(
                    "_",
                    new String[] {baseName, language, script, country}));
        }
//        baseName + "_" + language + "_" + script
        if (stringsValid(language, script)) {
            toCheck.add(StringUtil.concatenate(
                    "_",
                    new String[] {baseName, language, script}));
        }

//        baseName + "_" + language + "_" + country + "_" + variant
        if (stringsValid(language, country, variant)) {
            toCheck.add(StringUtil.concatenate(
                    "_",
                    new String[] {baseName, language, country, variant}));
        }
//        baseName + "_" + language + "_" + country
        if (stringsValid(language, country)) {
            toCheck.add(StringUtil.concatenate(
                    "_",
                    new String[] {baseName, language, country}));
        }

//        baseName + "_" + language
        if (stringsValid(language)) {
            toCheck.add(StringUtil.concatenate(
                    "_",
                    new String[] {baseName, language}));
        }

        toCheck.add(baseName);

        for (final var c : toCheck) {
            final var name = c + ".properties";

            final var url = module.getClassLoader().getResource(name);
            if (url != null) {
                return url;
            }
        }

        return null;
    }

    /**
     * Get class specific resources. If the passed classes full
     * name is "org.good.Class" then this operation loads
     * the resource bundle "org/good/Class.properties".
     * Prefer {@link #getClassResourceMap(Class)}.
     *
     * @param c The class for which the resources should be loaded.
     *
     * @return A ResourceBundle and its URL. If no resource bundle was found
     * for the passed class, then the result is {@code null}.
     */
    static Pair<URL, ResourceBundle> getClassResourcesImpl(final Class<?> c) {
        final String name = c.getName();

        try {
            return new Pair<>(
                    findResourceBundle(name, c.getModule()),
                    ResourceBundle.getBundle(name, c.getModule()));
        } catch (final MissingResourceException e) {
            return null;
        }
    }

    /**
     * Load a named resource from the resource package of the passed
     * class.
     *
     * @param c1ass The class used to locate the resource package.
     * @param name  The name of the resource.
     *
     * @return The resource content. Never null, throws a
     * RuntimeException if the resource was not found.
     */
    public static byte[] loadResource(
            final Class<?> c1ass,
            final String name) {
        try (final InputStream is = c1ass.getResourceAsStream(name)) {
            if (is == null) {
                throw new RuntimeException(
                        "Resource not found: " + name);
            }

            return is.readAllBytes();
        } catch (final IOException e) {
            throw new IllegalArgumentException(name, e);
        }
    }

    /**
     * Unchecked exception thrown when resource lookup
     * fails, for example because string conversion fails.  This is
     * not a missing resource exception.  If a resource isn't defined
     * for a particular key, no exception is thrown.
     */
    private static class LookupException extends RuntimeException {

        /**
         * Constructs an instance of this class with some useful information
         * about the failure.
         *
         * @param msg  the detail message
         * @param type the type of the resource
         * @param key  the name of the resource
         */
        public LookupException(final String msg, final String key, final Class<?> type) {
            super(String.format("%s: resource %s, type %s", msg, key, type));
        }
    }
}
