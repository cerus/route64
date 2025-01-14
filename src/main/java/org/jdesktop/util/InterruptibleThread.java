/* $Id: d92a81789b5dc1f701dcf7cecd406277653e954e $
 *
 * Common.
 *
 * Released under Gnu Public License
 * Copyright © 2010 Michael G. Binz
 */
package org.jdesktop.util;

/**
 * Extends a {@link Thread} with a deterministic interruption flag that is set
 * whenever the {@link Thread#interrupt()} operation is called and whose status
 * can be accessed by {@link #isInterrupted()}.  The expected scheme is that a
 * thread periodically checks its interruption status by calling
 * {@link #isInterrupted()}.  In case an outer thread calls
 * {@link #interrupt()} this will return {@code true} and allow the thread to
 * terminate.
 *
 * @version $Rev$
 * @author Michael Binz
 */
public class InterruptibleThread
    extends Thread
{
    private volatile boolean _interrupted = false;

    /**
     * @param r The thread's runnable.
     * @see Thread#Thread(Runnable)
     */
    public InterruptibleThread( Runnable r )
    {
        this( r, r.getClass().getSimpleName(), false );
    }



    /**
     * @param name The thread's name.
     * @param r The thread's runnable.
     * @see Thread#Thread(Runnable,String)
     */
    public InterruptibleThread( Runnable r, String name )
    {
        this( r, name, false );
    }

    /**
     * @param isDaemon The thread's daemon flag.
     * @param name The thread's name.
     * @param r The thread's runnable.
     * @see Thread#Thread(Runnable,String)
     */
    public InterruptibleThread( Runnable r, String name, boolean isDaemon )
    {
        super( r, name );

        setDaemon( isDaemon );
    }

    /**
     * Get the status of the interrupt flag.  This is set when
     * {@link #interrupt()} is called.
     *
     * @return {@code true} if the interrupt flag was set
     * by a call to {@link #interrupt()} or when {@link Thread#isInterrupted()}
     * returned {@code true}.
     */
    @Override
    public final boolean isInterrupted()
    {
        return _interrupted || super.isInterrupted();
    }

    /**
     * Sets the interrupted flag and calls the normal
     * {@link Thread#interrupt()} operation.
     *
     * @see #isInterrupted()
     */
    @Override
    public void interrupt()
    {
        _interrupted = true;
        super.interrupt();
    }
}
