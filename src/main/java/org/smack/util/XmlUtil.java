/**
 * $Id: d4597fa5984911fa0619068499d3322566d6ac8d $
 * <p>
 * Unpublished work.
 * Copyright © 2019 Michael G. Binz
 */
package org.smack.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * XML utility operations.
 *
 * @author Michael G. Binz
 * @deprecated Use {@link org.smack.util.xml.XmlUtil}.
 */
@Deprecated
public class XmlUtil {

    private XmlUtil() {
        throw new AssertionError();
    }

    /**
     * Transform a file based on an XSLT transformation. Access to
     * non-existent dtds is ignored.
     *
     * @param xslt The transformation.
     * @param toTransform The file to transform.
     * @return The result of the transformation.
     * @throws Exception In case of an error.
     */
    public static String transform(final InputStream xslt, final InputStream toTransform)
            throws Exception {
        return transform(
                new StreamSource(xslt),
                new InputSource(toTransform));
    }

    /**
     * Transform a file based on an XSLT transformation. Access to
     * non-existent dtds is ignored.
     *
     * @param xslt The transformation.
     * @param toTransform The file to transform.
     * @return The result of the transformation.
     * @throws Exception In case of an error.
     */
    public static String transform(final Reader xslt, final Reader toTransform)
            throws Exception {
        return transform(
                new StreamSource(xslt),
                new InputSource(toTransform));
    }

    /**
     * Transform a file based on an XSLT transformation. Access to
     * non-existent dtds is ignored.  This version of the operation
     * internally sets the systemId of the xslt, so that access on the
     * stylesheet via the xls 'document( '' )' operation works.
     *
     * @param xslt The transformation.
     * @param toTransform The file to transform.
     * @return The result of the transformation.
     * @throws Exception In case of an error.
     */
    public static String transform(final File xslt, final File toTransform)
            throws Exception {
        try (final Reader xsltReader = new FileReader(xslt)) {
            try (final Reader toTransformReader = new FileReader(toTransform)) {
                final StreamSource xsltSource =
                        new StreamSource(xsltReader);
                xsltSource.setSystemId(
                        xslt);

                return transform(
                        xsltSource,
                        new InputSource(toTransformReader));
            }
        }
    }

    /**
     * Transform a file based on an XSLT transformation. Access to
     * non-existent dtds is ignored.
     *
     * @param xslt The transformation.
     * @param toTransform The file to transform.
     * @return The result of the transformation.
     * @throws Exception In case of an error.
     */
    private static String transform(final StreamSource xslt, final InputSource toTransform)
            throws Exception {
        final XMLReader reader =
                SAXParserFactory.newInstance().newSAXParser().
                        getXMLReader();

        // Set a resolver that ignores access to non-existent dtds.
        reader.setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(
                    final String publicId,
                    final String systemId)
                    throws SAXException, IOException {
                if (systemId.endsWith(".dtd")) {
                    return new InputSource(new StringReader(" "));
                }

                return null;
            }
        });

        final TransformerFactory tFactory =
                TransformerFactory.newInstance();
        final Transformer transformer =
                tFactory.newTransformer(xslt);
        final ByteArrayOutputStream result =
                new ByteArrayOutputStream();
        transformer.transform(
                new SAXSource(
                        reader,
                        toTransform),
                new StreamResult(result));

        return result.toString();
    }

    /**
     * This operation works on Java 8.  TODO(micbinz) integrate.
     *
     * @param stylesheet The stylesheet.
     * @param datafile The input to process.
     * @param parameters Parameters to be passed to the stylesheet.
     * @return The processing result.
     * @throws Exception In case of an error.
     */
    public static String transform8(
            final File stylesheet,
            final File datafile,
            final Map<String, Object> parameters)
            throws Exception {
        final DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        final ByteArrayOutputStream bos =
                new ByteArrayOutputStream();
        final DocumentBuilder builder =
                factory.newDocumentBuilder();

        // Set a resolver that ignores access to non-existent dtds.
        builder.setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(
                    final String publicId,
                    final String systemId)
                    throws SAXException, IOException {
                if (systemId.endsWith(".dtd")) {
                    return new InputSource(new StringReader(" "));
                }

                return null;
            }
        });

        final Document document =
                builder.parse(datafile);
        final TransformerFactory tFactory =
                TransformerFactory.newInstance();
        final StreamSource stylesource =
                new StreamSource(stylesheet);
        stylesource.setSystemId(
                stylesheet.getPath());
        final Transformer transformer =
                tFactory.newTransformer(stylesource);

        parameters.forEach(
                (k, v) -> transformer.setParameter(k, v));

        final DOMSource source =
                new DOMSource(document);
        final StreamResult result =
                new StreamResult(bos);
        transformer.transform(
                source,
                result);

        return bos.toString();
    }

    /**
     * This operation works on Java 8.  TODO(micbinz) integrate.
     *
     * @param stylesheet The stylesheet.
     * @param datafile The input to process.
     * @param parameters Parameters to be passed to the stylesheet.
     * @return The processing result.
     * @throws Exception In case of an error.
     */
    public static String transform8(
            final InputStream stylesheet,
            final InputStream datafile,
            final Map<String, Object> parameters)
            throws Exception {
        final DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        final ByteArrayOutputStream bos =
                new ByteArrayOutputStream();
        final DocumentBuilder builder =
                factory.newDocumentBuilder();

        // Set a resolver that ignores access to non-existent dtds.
        builder.setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(
                    final String publicId,
                    final String systemId)
                    throws SAXException, IOException {
                if (systemId.endsWith(".dtd")) {
                    return new InputSource(new StringReader(" "));
                }

                return null;
            }
        });

        final Document document =
                builder.parse(datafile);
        final TransformerFactory tFactory =
                TransformerFactory.newInstance();
        final StreamSource stylesource =
                new StreamSource(stylesheet);
//        stylesource.setSystemId(
//                stylesheet.getPath() );
        final Transformer transformer =
                tFactory.newTransformer(stylesource);

        parameters.forEach(
                (k, v) -> transformer.setParameter(k, v));

        final DOMSource source =
                new DOMSource(document);
        final StreamResult result =
                new StreamResult(bos);
        transformer.transform(
                source,
                result);

        return bos.toString();
    }

    public static String transform8(
            final InputStream stylesheet,
            final InputStream datafile)
            throws Exception {
        return transform8(
                stylesheet,
                datafile,
                Collections.emptyMap());
    }

    public static String transform8(
            final File stylesheet,
            final File datafile)
            throws Exception {
        return transform8(
                stylesheet,
                datafile,
                Collections.emptyMap());
    }
}
