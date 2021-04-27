package org.jdesktop.util.converters;

import javax.swing.Icon;
import org.jdesktop.util.ResourceConverter;
import org.jdesktop.util.ResourceMap;

public class IconStringConverter extends ResourceConverter {

    public IconStringConverter() {
        super(Icon.class);
    }

    @Override
    public Object parseString(final String s, final ResourceMap resourceMap) throws Exception {
        return ConverterUtils.loadImageIcon(s, resourceMap);
    }
}
