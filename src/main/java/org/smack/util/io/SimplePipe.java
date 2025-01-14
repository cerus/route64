/* $Id: 669714be2d67441c0da98bbd45d509aa54239353 $
 *
 * Common.
 *
 * Released under Gnu Public License
 * Copyright © 2012 Michael G. Binz
 */
package org.smack.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import org.smack.util.JavaUtil;

/**
 * A simple pipe.  Write to the write end, read from the read end.
 *
 * @author Michael Binz
 * @version $Rev$
 */
public final class SimplePipe implements Pipe {

    private static final int BUFFER_SIZE = 10 * 1024;

    private final PipedOutputStream _writeEnd;
    private final PipedInputStream _readEnd;

    /**
     * Create an instance.
     *
     * @param bufferSize The size of the pipe's internal buffer.
     */
    public SimplePipe(final int bufferSize) {
        try {
            this._writeEnd = new PipedOutputStream();
            this._readEnd = new PipedInputStream(this._writeEnd, bufferSize);
        } catch (final IOException e) {
            // PipedStreams can throw 'AlreadyConnected' which should not be
            // possible in the context of this implementation.
            throw new InternalError(e.toString());
        }
    }

    /**
     * Create an instance with a 10k buffer size.
     */
    public SimplePipe() {
        this(BUFFER_SIZE);
    }

    /**
     * Get the Pipe's write end.
     *
     * @return The write end.
     */
    @Override
    public OutputStream getWriteEnd() {
        return this._writeEnd;
    }

    /**
     * Get the pipes read end.
     *
     * @return The read end.
     */
    @Override
    public InputStream getReadEnd() {
        return this._readEnd;
    }

    /**
     * Close the pipe.
     */
    @Override
    public void close() {
        JavaUtil.force(
                this._readEnd::close);
        JavaUtil.force(
                this._writeEnd::close);
    }
}
