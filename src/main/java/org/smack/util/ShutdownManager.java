/* $Id: f9892dc08575badb342018da8db19fa2816af358 $
 *
 * Copyright © 2003-2015 Michael G. Binz
 */
package org.smack.util;

import java.util.Stack;

/**
 * Manages a number of shutdown procedures.  The difference to the raw usage of
 * <code>Runtime.addShutdownHook()</code> is that this happens in LIFO order.
 *
 * @version $Rev$
 * @see Runtime#addShutdownHook(Thread)
 */
public final class ShutdownManager {
    // We deliberately do not offer a remove() method.

    /**
     * The internal container used to hold the shutdown runnables.
     */
    private static final Stack<Runnable> _shutdownRunnables =
            new Stack<>();
    /**
     * The singleton instance of this class.
     */
    private static ShutdownManager _theInstance;

    /**
     * Creates an instance and registers the real shutdown listener.
     */
    private ShutdownManager() {
        final Thread shutdown = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        performShutdown();
                    }
                },
                this.getClass().getSimpleName());

        Runtime.getRuntime().addShutdownHook(shutdown);
    }

    /**
     * Add a shutdown procedure to be run on application termination.  The
     * added shutdown procedures will be invoked in reverse order of
     * addition.
     *
     * @param shutdownProcedure The shutdown procedure.
     */
    public static void add(final Runnable shutdownProcedure) {
        if (_theInstance == null) {
            _theInstance = new ShutdownManager();
        }

        _shutdownRunnables.push(shutdownProcedure);
    }

    /**
     * Performs the actual shutdown.
     */
    private static void performShutdown() {
        while (!_shutdownRunnables.empty()) {
            _shutdownRunnables.pop().run();
        }
    }
}
