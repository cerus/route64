/* $Id: 743bc54d777b209382128f488aa08c0aecc3b205 $
 *
 * Common.
 *
 * Released under Gnu Public License
 * Copyright © 2011 Michael G. Binz
 */
package org.smack.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * General utilities.
 *
 * @author Michael Binz
 */
public class JavaUtil
{
    private static final Logger LOG =
            Logger.getLogger( JavaUtil.class.getName() );

    private JavaUtil()
    {
        throw new AssertionError();
    }

    public static void Assert( boolean condition, String message )
    {
        if ( condition )
            return;
        throw new AssertionError( message );
    }

    public static void Assert( boolean condition )
    {
        if ( condition )
            return;
        throw new AssertionError();
    }

    /**
     * Sleep for an amount of milliseconds or until interrupted.
     * @param millis The time to sleep.
     */
    public static void sleep( long millis )
    {
        try
        {
            Thread.sleep( millis );
        }
        catch ( InterruptedException ignore )
        {
        }
    }

    /**
     * A parameterless operation throwing an exception.
     */
    public interface Fx
    {
        void call()
            throws Exception;
    }

    /**
     * Calls the passed operation, ignoring an exception.
     * @param operation The operation to call.
     */
    public static void force( Fx operation )
    {
        try
        {
            operation.call();
        }
        catch ( Exception e )
        {
            LOG.log( Level.INFO, "Force exception.", e );
        }
    }


    /**
     * Test if an array is empty.
     *
     * @param <T> The array type.
     * @param array The array to test. {@code null} is allowed.
     * @return {@code true} if the array is not null and has a length greater
     * than zero.
     */
    public static <T> boolean isEmptyArray( T[] array )
    {
        return array == null || array.length == 0;
    }

    /**
     * Create an exception with a formatted message.
     *
     * @param fmt The format string.
     * @param args The arguments.
     * @return The newly created exception.
     */
    public static Exception fmtX( String fmt, Object... args )
    {
        return new Exception(
                String.format( fmt, args ) );
    }

    /**
     * Create an exception with a formatted message. Allows to
     * add a cause.
     *
     * @param cause The cause of the created exception.
     * @param fmt The format string.
     * @param args The arguments.
     * @return The newly created exception.
     */
    public static Exception fmtX(
            Throwable cause,
            String fmt,
            Object... args )
    {
        return new Exception(
                String.format( fmt, args ),
                cause );
    }
}
