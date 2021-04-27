/* $Id: 00a3bef0825c17dbfdcc5f04c382965cad1b882b $
 *
 * Common.
 *
 * Released under Gnu Public License
 * Copyright © 2012 Michael G. Binz
 */
package org.smack.util.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.jdesktop.util.InterruptibleThread;

/**
 * An output stream that decouples the writing thread from the thread that
 * forwards the data to the passed target stream.
 *
 * @author Michael Binz
 * @version $Rev$
 */
final public class OutputStreamForwarder
        extends OutputStream {

    /**
     * The target output stream.
     */
    private final OutputStream _target;
    /**
     * The queue that holds the data packets to forward.
     */
    private final BlockingQueue<byte[]> _outgoing;
    /**
     * The thread forwarding the data.
     */
    private final InterruptibleThread _dataPumpThread;
    /**
     * If the data copy thread receives an exception this is placed here and returned
     * on the next write call.
     */
    volatile private IOException _failed;
    /**
     * The actual forwarding algorithm.
     */
    private final Runnable _dataPump = new Runnable() {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    OutputStreamForwarder.this._target.write(OutputStreamForwarder.this._outgoing.take());
                } catch (final IOException e) {
                    OutputStreamForwarder.this._failed = e;
                    return;
                } catch (final InterruptedException e) {
                    return;
                }
            }
        }
    };

    /**
     * Create an instance.
     *
     * @param target The target output stream receiving incoming data.
     */
    public OutputStreamForwarder(final OutputStream target, final int capacity) {
        if (null == target) {
            throw new NullPointerException();
        }

        this._outgoing = new ArrayBlockingQueue<>(capacity);

        this._target = target;

        this._dataPumpThread = new InterruptibleThread(
                this._dataPump,
                this.getClass().getSimpleName(),
                true);
        this._dataPumpThread.start();
    }

    /* (non-Javadoc)
     * @see java.io.OutputStream#write(byte[])
     */
    @Override
    public void write(final byte[] b) throws IOException {
        if (this._failed != null) {
            throw this._failed;
        }

        try {
            this._outgoing.add(b.clone());
        } catch (final IllegalStateException e) {
            throw new IOException("Pipe broken -- no consumer.");
        }
    }

    /* (non-Javadoc)
     * @see java.io.OutputStream#write(byte[], int, int)
     */
    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        if (this._failed != null) {
            throw this._failed;
        }

        try {
            this._outgoing.add(Arrays.copyOfRange(b, off, len));
        } catch (final IllegalStateException e) {
            throw new IOException("Pipe broken -- no consumer.");
        }
    }

    @Override
    public void write(final int b) throws IOException {
        if (this._failed != null) {
            throw this._failed;
        }

        try {
            this._outgoing.add(new byte[] {(byte) b});
        } catch (final IllegalStateException e) {
            throw new IOException("Pipe broken -- no consumer.");
        }
    }

    /* (non-Javadoc)
     * @see java.io.OutputStream#flush()
     */
    @Override
    public void flush() throws IOException {
        if (this._failed != null) {
            throw this._failed;
        }

        this._target.flush();
    }

    /* (non-Javadoc)
     * @see java.io.OutputStream#close()
     */
    @Override
    public void close() throws IOException {
        this._dataPumpThread.interrupt();
        this._target.flush();
        this._target.close();
    }
}
