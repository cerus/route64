/* $Id: eaba215c0f88a6d8b9931f8450da1ad555638cbd $
 *
 * Common.
 *
 * Released under Gnu Public License
 * Copyright © 2020 Michael G. Binz
 */
package org.smack;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import org.smack.application.CliApplication;
import org.smack.application.CliApplication.Named;
import org.smack.util.StringUtil;
import org.smack.util.xml.XmlUtil;

/**
 * A collection of cli utilities based on Smack.
 *
 * @author michab66
 */
@Named(description = "A collection of cli utilities based on Smack.")
public final class Cli extends CliApplication {

    private Cli() {
    }

    public static void main(final String[] argv) {
        launch(Cli::new, argv);
    }

    @Command(shortDescription =
            "Tranforms xml using stylesheet.  Writes the result to stdout.")
    public void xsl(
            @Named(value = "stylesheet") final File stylesheet,
            @Named(value = "xml-file") final File xml) throws Exception {
        this.out("%s\n", XmlUtil.transform(stylesheet, xml));
    }

    @Command(shortDescription =
            "Tranforms xml using stylesheet.  "
                    + "Writes the result to target-file.  "
                    + "If target-file exists, it is overwritten.")
    public void xsl(
            @Named(value = "stylesheet") final File stylesheet,
            @Named(value = "xml-file") final File xml,
            @Named(value = "target-file") final String target
    ) throws Exception {
        try (final Writer w = new FileWriter(target)) {
            w.write(XmlUtil.transform(stylesheet, xml));
            w.write(StringUtil.EOL);
        }
    }
}
