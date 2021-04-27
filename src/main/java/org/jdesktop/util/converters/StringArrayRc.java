package org.jdesktop.util.converters;

import java.lang.reflect.Array;
import org.jdesktop.util.ResourceConverter;
import org.jdesktop.util.ResourceMap;
import org.smack.util.StringUtil;

/**
 * Converts string arrays, handles quoting.
 *
 * @author Michael Binz
 * @version $Revision$
 */
public final class StringArrayRc extends ResourceConverter {

    public StringArrayRc() {
        super(String[].class);

        if (!this.getType().isArray()) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public Object parseString(final String s, final ResourceMap r)
            throws Exception {
        final String[] split = StringUtil.splitQuoted(s);

        final Object result = Array.newInstance(
                this.getType().getComponentType(), split.length);

        int idx = 0;
        for (final String c : split) {
            Array.set(
                    result,
                    idx++,
                    c);
        }

        return result;
    }
}
