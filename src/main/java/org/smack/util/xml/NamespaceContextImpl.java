/**
 * $Id: 4ce299b6806bf62e862de57f5cd374f8a89472de $
 * <p>
 * Unpublished work.
 * Copyright © 2020 Michael G. Binz
 */
package org.smack.util.xml;

import java.util.HashMap;
import java.util.Iterator;
import javax.xml.namespace.NamespaceContext;

/**
 * A default implementation of {@link NamespaceContext}.
 *
 * @author micbinz
 */
class NamespaceContextImpl
// Deliberate inheritance to inherit the normal iteration operations and a
// decent toString() operation.
        extends HashMap<String, String>
        implements NamespaceContext {

    public NamespaceContextImpl() {
    }

    @Override
    public String getNamespaceURI(final String prefix) {
        return this.get(prefix);
    }

    @Override
    public String getPrefix(final String uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<String> getPrefixes(final String uri) {
        throw new UnsupportedOperationException();
    }
}
