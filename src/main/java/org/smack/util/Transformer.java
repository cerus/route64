/* $Id: 1364872f1d43a43d9408fd39b784e0742c7c4e60 $
 *
 * Michael's Application Construction Kit (MACK)
 *
 * Released under Gnu Public License
 * Copyright © 2008 Michael G. Binz
 */
package org.smack.util;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * A class capable of transforming types.
 *
 * @param <T> The transformation target type.
 * @param <F> The transformation source type.
 *
 * @author Michael Binz
 * @version $Rev$
 */
public class Transformer<T, F> {

    /*
     * The logger for this class.
     */
    private static final Logger _log = Logger.getLogger(Transformer.class
            .getName());

    /**
     * The target type constructor;
     */
    private final Constructor<T> _ctor;


    /**
     * Creates a transformer for the passed types.
     *
     * @param targetClass
     * @param from
     *
     * @throws IllegalArgumentException
     */
    public Transformer(final Class<T> targetClass, final Class<F> from) {
        if (from.equals(targetClass)) {
            this._ctor = null;
            return;
        }

        try {
            this._ctor = targetClass.getConstructor(from);
        } catch (final NoSuchMethodException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * @param e
     * @param o
     */
    private static void unexpectedException(final Throwable e, final Object o) {
        _log.log(Level.SEVERE, e.getLocalizedMessage() + ":" + o, e);
    }

    /**
     * Checks whether automatic conversion from fromClass to targetClass is
     * possible.
     *
     * @param fromClass   The class to convert from.
     * @param targetClass The class to convert to.
     *
     * @return True if a conversion seems possible.
     */
    public static boolean canConvert(
            final Class<?> fromClass,
            final Class<?> targetClass) {
        try {
            targetClass.getConstructor(fromClass);
            return true;
        } catch (final NoSuchMethodException e) {
            // This does not need to be handled.  If the ctor
            // does not exist, this means 'not convertible'.
        }

        return false;
    }

    /**
     * Transforms the passed value to the passed target class.
     * Expects that a constructor for the target class is available accepting
     * a parameter of the value's type.
     *
     * @param value       The value to transform.
     * @param targetClass The class to create an instance of.
     *
     * @return The transformed value.
     *
     * @throws Exception An unspecified exception, if the transformation
     *                   was not possible.
     */
    public static <T> T transform(final Object value, final Class<T> targetClass)
            throws Exception {
        if (value == null) {
            return null;
        }

        final Class<?> sourceType = value.getClass();

        // Check whether the types are directly assignable.
        if (targetClass.isAssignableFrom(sourceType)) {
            return (T) value;
        }

        final Constructor<T> ctor = targetClass.getConstructor(
                sourceType);

        return ctor.newInstance(
                value);
    }

    /**
     * Performs a transformation.
     *
     * @param f The object to transform.
     *
     * @return The transformation result.
     */
    public T transform(final F f) {
        try {
            return this.transformX(f);
        } catch (final Exception e) {
            unexpectedException(e, f);
            return null;
        }
    }

    /**
     * Performs a transformation.
     *
     * @param f The object to transform.
     *
     * @return The transformation result.
     *
     * @throws Exception if the transformation failed.
     */
    public T transformX(final F f)
            throws Exception {
        if (this._ctor == null) {
            return (T) f;
        }

        try {
            return this._ctor.newInstance(f);
        } catch (final InvocationTargetException e) {
            final Throwable cause = e.getCause();

            if (cause instanceof Exception) {
                throw (Exception) cause;
            }

            throw e;
        }
    }

    /**
     * Transform an array.
     *
     * @param fa The array to transform.
     *
     * @return The transformed array.
     */
    public T[] transform(final F[] fa) {
        final T[] result = (T[]) Array.newInstance(
                this._ctor.getDeclaringClass(),
                fa.length);

        for (int i = 0; i < result.length; i++) {
            result[i] = this.transform(fa[i]);
        }

        return result;
    }
}
