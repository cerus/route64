package org.jdesktop.util.converters;

import java.net.MalformedURLException;
import java.net.URL;
import org.jdesktop.util.ResourceConverter;
import org.jdesktop.util.ResourceMap;

@Deprecated
public class UrlStringConverter extends ResourceConverter {

    public UrlStringConverter() {
        super(URL.class);
    }

    @Override
    public Object parseString(final String s, final ResourceMap ignore) throws Exception {
        try {
            return new URL(s);
        } catch (final MalformedURLException e) {
            throw new Exception("invalid URL: " + s, e);
        }
    }
}
