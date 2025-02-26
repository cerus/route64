/*
 * Copyright (C) 2009 Illya Yalovyy
 * Use is subject to license terms.
 */

package org.jdesktop.util;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.Objects;
import org.smack.util.ReflectionUtil;
import org.smack.util.ResourceUtil;
import org.smack.util.StringUtil;

/**
 * @author Illya Yalovyy
 */
public enum PlatformType {
    DEFAULT(
            "Default",
            StringUtil.EMPTY_STRING),
    FREE_BSD(
            "FreeBSD",
            "bsd",
            "FreeBSD"),
    LINUX(
            "Linux",
            "lin",
            "linux"),
    OS_X(
            "Mac OS X",
            "mac",
            "mac os x"),
    WINDOWS(
            "Windows",
            "win",
            "windows");

    private static PlatformType activePlatformType;
    /**
     * An end user compatible platform name.
     */
    public final String name;
    /**
     * A resource suffix.
     */
    public final String resourceSuffix;
    /**
     * Patterns matched against os.name to detect a platform type.
     */
    private final String[] patterns;

    private PlatformType(final String name, final String resourcePrefix, final String... patterns) {
        this.name = name;
        this.resourceSuffix = resourcePrefix;
        this.patterns = patterns;
    }

    /**
     * @param platformType A platform type.
     *
     * @return True if the passed platform equals the current platform.
     */
    public static boolean is(final PlatformType platformType) {
        return getPlatform() == Objects.requireNonNull(platformType);
    }

    /**
     * @return The current platform.
     */
    public static PlatformType getPlatform() {
        if (activePlatformType != null) {
            return activePlatformType;
        }

        activePlatformType = PlatformType.DEFAULT;

        final PrivilegedAction<String> doGetOSName =
                () -> System.getProperty("os.name");
        String osName = AccessController.doPrivileged(
                doGetOSName);

        if (osName != null) {
            osName = osName.toLowerCase();
            for (final PlatformType platformType : PlatformType.values()) {
                for (final String pattern : platformType.getPatterns()) {
                    if (osName.startsWith(pattern)) {
                        return activePlatformType = platformType;
                    }
                }
            }
        }

        return activePlatformType;
    }

    private static <T> T createDefaultInstance(final Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Load a platform specific class.
     *
     * @param pClass The prototype class which may be of interface type.
     *               This classes resources are read and the resource suffix key is
     *               resolved against the class.  If the key is defined, then its
     *               value must be a class name.  This gets loaded and an instance is
     *               returned.
     *               If the key is not defined, then it is tried to create an instance of
     *               the passed class.
     *               Design:  If no fallback exists, pass an interface and handle the
     *               thrown exception as 'Platform not supported'. Otherwise pass a class
     *               with a public constructor.
     * @param <T>    The result type.
     *
     * @return An instance of the passed class or interface. Throws a runtime
     * exception if loading fails.
     */
    public static <T> T load(final Class<T> pClass) {
        final PlatformType platformType =
                getPlatform();

        if (platformType == PlatformType.DEFAULT) {
            // If platform is default, the passed class must be
            // creatable.
            return createDefaultInstance(pClass);
        }

        final Map<String, String> resrc =
                ResourceUtil.getClassResourceMap(pClass);

        final String platformClassName =
                resrc.get(platformType.resourceSuffix);

        if (StringUtil.isEmpty(platformClassName)) {
            // If no name is defined we try to fall back
            // on an instance of the passed class.
            return createDefaultInstance(pClass);

        }

        try {
            final Class<T> resultClass =
                    (Class<T>) pClass.getClassLoader().loadClass(platformClassName);

            return ReflectionUtil.createInstanceX(resultClass);
        } catch (final Exception e) {
            throw new RuntimeException(ReflectionUtil.unwrap(e));
        }
    }

    private String[] getPatterns() {
        return this.patterns;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
