/*
 * Copyright (C) 2006 Sun Microsystems, Inc. All rights reserved. Use is
 * subject to license terms.
 */
package org.smack.application;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.smack.util.ServiceManager;

/**
 * An application service that offers a simple means to store short-term,
 * low-value preferences values per user and application.
 * This is intended to be used to save for example ui settings from session
 * to session.
 * <p>
 * This is similar to the {@link Preferences} system, but simpler to use.
 * <p>
 * All put-operations perform an implicit write of a serialized map to the
 * file system. This class is not intended as a transactional high volume
 * storage.
 *
 * @author Michael Binz
 * @version $Rev$
 */
public class ApplicationProperties {

    private static final Logger L =
            Logger.getLogger(ApplicationProperties.class.getName());

    private final LocalStorage _localStorage;
    private final File _fileName;

    private final Map<String, String> _storage;

    /**
     * Create an instance.
     *
     * @param application The application requesting the service.
     */
    ApplicationProperties() {
        final ApplicationInfo info =
                ServiceManager.getApplicationService(ApplicationInfo.class);

        this._fileName = new File(String.format("%s_%s.aps",
                info.getId(),
                info.getVendorId()));

        this._localStorage =
                ServiceManager.getApplicationService(LocalStorage.class);

        Map<String, String> localMap;

        try (final ObjectInputStream is = new ObjectInputStream(
                this._localStorage.openInputFile(this._fileName))) {
            localMap = (Map<String, String>) is.readObject();
        } catch (final Exception e) {
            localMap = new HashMap<>();
        }

        this._storage = localMap;
    }

    /**
     * Check if a key is defined.
     *
     * @param client The client class.
     * @param key    The key to check for.
     *
     * @return True if the key is defined, false otherwise.
     */
    public boolean containsKey(final Class<?> client, final String key) {
        return this._storage.containsKey(this.makeKey(client, key));
    }

    /**
     * @param client The client class.
     * @param key
     */
    public void remove(final Class<?> client, final String key) {
        this._storage.remove(this.makeKey(client, key));
        this.flush();
    }

    /**
     * Store a string property.
     *
     * @param client The client class.
     * @param key    The key. Must not be null.
     * @param value  The value. Must not be null.
     */
    public void put(final Class<?> client, final String key, final String value) {
        this._storage.put(
                this.makeKey(client, key),
                Objects.requireNonNull(value));

        this.flush();
    }

    /**
     * Get a string value.
     *
     * @param client The client class.
     * @param key    The key. Must not be null.
     * @param deflt  A default result.
     *
     * @return If the key exists the attached value or the default result.
     */
    public String get(final Class<?> client, final String key, final String deflt) {
        final String normalizedKey = this.makeKey(client, key);

        if (this._storage.containsKey(normalizedKey)) {
            return this._storage.get(normalizedKey);
        }

        return deflt;
    }

    /**
     * Store a long value.
     *
     * @param client The client class.
     * @param key    The key. Must not be null.
     * @param value  The value to store.
     */
    public void putLong(final Class<?> client, final String key, final long value) {
        this._storage.put(
                this.makeKey(client, key),
                Long.toString(value));

        this.flush();
    }

    /**
     * Get a long value.
     *
     * @param client The client class.
     * @param key    The key. Must not be null.
     * @param def    A default result.
     *
     * @return The integer value from the storage or the default value
     * if the key was not set.
     */
    public long getLong(final Class<?> client, final String key, final long def) {
        final String normalizedKey = this.makeKey(client, key);

        if (this._storage.containsKey(normalizedKey)) {
            final String content = this._storage.get(normalizedKey);

            try {
                return Long.parseLong(content);
            } catch (final Exception ignore) {
                L.warning("Unexpected content: " + content);
            }
        }

        return def;
    }

    /**
     * Store a floating point value.
     *
     * @param client The client class.
     * @param key    The key. Must not be null.
     * @param value  The value to store.
     */
    public void putDouble(final Class<?> client, final String key, final double value) {
        this._storage.put(
                this.makeKey(client, key),
                Double.toString(value));

        this.flush();
    }

    /**
     * Get a floating point value.
     *
     * @param client The client class.
     * @param key    The key. Must not be null.
     * @param def    A default result.
     *
     * @return A value from the storage or the passed default value
     * if the key was not set.
     */
    public double getDouble(final Class<?> client, final String key, final double def) {
        final String normalizedKey = this.makeKey(client, key);

        if (this._storage.containsKey(normalizedKey)) {
            final String content = this._storage.get(normalizedKey);

            try {
                return Double.parseDouble(content);
            } catch (final Exception ignore) {
                L.warning("Unexpected content: " + content);
            }
        }

        return def;
    }

    /**
     * Get the keys defined for the passed client.
     *
     * @param client The client class.
     *
     * @return A newly allocated map holding the available keys. Empty
     * if no keys are defined.
     */
    public Set<String> keys(final Class<?> client) {
        final HashSet<String> result = new HashSet<>();

        for (final String c : this._storage.keySet()) {
            if (c.startsWith(client.getName())) {
                result.add(c);
            }
        }

        return result;
    }

    /**
     * Flushes our storage to persistent storage.
     */
    private void flush() {
        try (final ObjectOutputStream oos =
                     new ObjectOutputStream(this._localStorage.openOutputFile(this._fileName))) {
            oos.writeObject(this._storage);
            oos.flush();
        } catch (final IOException e) {
            L.log(Level.WARNING, "Storing application properties failed.", e);
        }
    }

    private String makeKey(final Class<?> c, final String key) {
        if (key == null || c == null) {
            throw new NullPointerException();
        }

        return c.getName() + "." + key;
    }
}
