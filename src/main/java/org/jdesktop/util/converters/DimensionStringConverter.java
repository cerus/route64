package org.jdesktop.util.converters;

import java.awt.Dimension;
import java.util.List;
import org.jdesktop.util.ResourceConverter;
import org.jdesktop.util.ResourceMap;


public class DimensionStringConverter extends ResourceConverter {

    public DimensionStringConverter() {
        super(Dimension.class);
    }

    @Override
    public Object parseString(final String s, final ResourceMap ignore) throws Exception {
        final List<Double> xy = ConverterUtils.parseDoubles(s, 2, "invalid x,y Dimension string");
        final Dimension d = new Dimension();
        d.setSize(xy.get(0), xy.get(1));
        return d;
    }
}

