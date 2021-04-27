package org.jdesktop.util.converters;

import java.awt.Point;
import java.util.List;
import org.jdesktop.util.ResourceConverter;
import org.jdesktop.util.ResourceMap;

public class PointStringConverter extends ResourceConverter {

    public PointStringConverter() {
        super(Point.class);
    }

    @Override
    public Object parseString(final String s, final ResourceMap ignore) throws Exception {
        final List<Double> xy = ConverterUtils.parseDoubles(s, 2, "invalid x,y Point string");
        final Point p = new Point();
        p.setLocation(xy.get(0), xy.get(1));
        return p;
    }
}

