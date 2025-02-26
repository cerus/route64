/* $Id: e48bec01d027718483a0701692cfbdd94760b549 $
 *
 * Utilities
 *
 * Released under Gnu Public License
 * Copyright (c) 2008 Michael G. Binz
 */
package org.smack.util;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.swing.filechooser.FileFilter;

/**
 * A library of utility routines for file handling.
 *
 * @author Michael Binz
 * @version $Rev$s
 */
public final class FileUtil {

    /**
     * The logger for this class.
     */
    private static final Logger LOG = Logger.getLogger(
            FileUtil.class.getName());
    private final static char SUFFIX_SEPARATOR = '.';

    /**
     * Instances cannot be created.
     */
    private FileUtil() {
        throw new AssertionError();
    }

    /**
     * Resolves directories in the passed file list. That is, normal file
     * entries in the returned list are returned unmodified in the result. If a
     * directory is contained in the passed file list, then this is replaced
     * recursively by its contained files.
     *
     * @param files The file list including directories.
     *
     * @return A list of files. No directories are part of this list.
     */
    public static File[] resolveDirectories(final File[] files) {
        final Vector<File> fs = new Vector<>();

        for (final File file : files) {
            if (file.isDirectory()) {
                final File[] dirContents = file.listFiles();
                if (dirContents != null && dirContents.length > 0) {
                    for (final File c : resolveDirectories(dirContents)) {
                        fs.add(c);
                    }
                }
            } else {
                fs.add(file);
            }
        }

        return fs.toArray(new File[fs.size()]);
    }

    /**
     * Silently closes the passed closeable.  In case the close
     * operation fails, the exception is written into the log.
     *
     * @param closeable The object to close.  If {@code null} is passed
     *                  this operation does nothing.
     */
    public static void forceClose(final Closeable closeable) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (final Exception e) {
            LOG.log(Level.FINE, e.getMessage(), e);
        }
    }

    /**
     * Filters a list of files.
     *
     * @param files  The files to filter.
     * @param filter The filter to use.
     *
     * @return The files that have been accepted by the filter.
     */
    public static File[] filterFiles(final File[] files, final FileFilter filter) {
        final Vector<File> collector = new Vector<>(files.length);

        for (final File f : files) {
            if (filter.accept(f)) {
                collector.add(f);
            }
        }

        return collector.toArray(new File[collector.size()]);
    }

    /**
     * Get the passed file's suffix.
     *
     * @param file A file.
     *
     * @return The passed file's suffix not including the separator.
     */
    public static String getSuffix(final File file) {
        final String filename = file.getName();

        final int dotidx = filename.lastIndexOf(SUFFIX_SEPARATOR);

        if (dotidx == -1) {
            return StringUtil.EMPTY_STRING;
        }

        return filename.substring(dotidx + 1);
    }

    /**
     * Replace the suffix in a filename.
     *
     * @param filename  The file name.
     * @param newSuffix A new suffix without a separator character.
     *
     * @return The extended file name.
     */
    public static String replaceSuffix(final String filename, final String newSuffix) {
        final int dotidx = filename.lastIndexOf(SUFFIX_SEPARATOR);

        if (dotidx == -1) {
            return filename + newSuffix;
        }

        return filename.substring(0, dotidx) + SUFFIX_SEPARATOR + newSuffix;
    }

    /**
     * Replace the suffix in a file name.
     *
     * @param filename  A file.
     * @param newSuffix The new suffix without a separator character.
     *
     * @return The extended file name.
     */
    public static File replaceSuffix(final File filename, final String newSuffix) {
        return new File(
                filename.getParent(),
                replaceSuffix(
                        filename.getName(),
                        newSuffix)
        );
    }

    /**
     * Read all lines from a reader into a list.  Closes the reader.
     *
     * @param in The reader to use.
     *
     * @return The lines read.
     *
     * @throws IOException In case of an error.
     */
    public static List<String> readLines(final Reader in) throws IOException {
        try (final BufferedReader din = new BufferedReader(in)) {
            return din.lines().collect(Collectors.toList());
        } catch (final UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Read all lines from an stream into a list. Closes the stream.
     *
     * @param in The reader to use.
     *
     * @return The lines read.
     *
     * @throws IOException In case of an error.
     */
    public static List<String> readLines(final InputStream in) throws IOException {
        return readLines(
                new InputStreamReader(in));
    }

    /**
     * Deletes a directory recursively.
     *
     * @param dir The directory to delete.
     *
     * @return true if successful.
     */
    public static boolean delete(final File dir) {
        if (!dir.exists()) {
            return true;
        }

        if (dir.isFile()) {
            return dir.delete();
        }

        try {
            Files.walk(dir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (final Exception e) {
            LOG.log(
                    Level.WARNING,
                    "Failed to delete: " + dir.getPath(),
                    e);
            return false;
        }

        return true;
    }
}
