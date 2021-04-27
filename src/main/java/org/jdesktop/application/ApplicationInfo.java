/* $Id: 91fa902cd71065bd8ad79c5e1b33f3d588e80274 $
 *
 * Common.
 *
 * Released under Gnu Public License
 * Copyright © 2016 Michael G. Binz
 */
package org.jdesktop.application;

import java.awt.Image;
import java.util.Objects;
import org.jdesktop.util.ResourceManager;
import org.jdesktop.util.ServiceManager;


/**
 * Application information.
 *
 * @author Michael Binz
 * @version $Rev$
 * @deprecated moved
 */
@Deprecated
public class ApplicationInfo {

    private final Class<?> _applicationClass;
    private final String id;
    private final String title;
    private final String version;
    private final Image icon;
    private final String vendor;
    private final String vendorId;

    /**
     * Catches automatic instantiation in ServiceManager, being not
     * allowed for this class.  Instead in {@code main} explicitly initialize
     * the ServiceMenager with an instance of this class.
     */
    public ApplicationInfo() {
        throw new IllegalStateException("Init ServiceManager in main.");
    }

    public ApplicationInfo(final Class<?> applicationClass) {
        this._applicationClass =
                Objects.requireNonNull(applicationClass);
        final ResourceManager rm =
                ServiceManager.getApplicationService(
                        ResourceManager.class);
        final org.jdesktop.util.ResourceMap arm =
                rm.getResourceMap(this._applicationClass);

        this.id = arm.get(
                "Application.id");
        this.title = arm.get(
                "Application.title");
        this.version = arm.get(
                "Application.version");
        this.icon = arm.getAs(
                "Application.icon", Image.class);
        this.vendor = arm.get(
                "Application.vendor");
        this.vendorId = arm.get(
                "Application.vendorId");
    }

    public Class<?> getApplicationClass() {
        return this._applicationClass;
    }

    /**
     * Return the application's id as defined in the resources.
     *
     * @return The application's id.
     */
    public String getId() {
        return this.id;
    }

    /**
     * Return the application's title as defined in the resources.
     *
     * @return The application's title.
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * Return the application's version as defined in the resources.
     *
     * @return The application's version.
     */
    public String getVersion() {
        return this.version;
    }

    /**
     * Return the application's icon as defined in the resources.
     *
     * @return The application icon.
     */
    public Image getIcon() {
        return this.icon;
    }

    /**
     * Return the application's vendor as defined in the resources.
     *
     * @return The vendor name.
     */
    public String getVendor() {
        return this.vendor;
    }

    /**
     * Return the application's vendor as defined in the resources.
     *
     * @return The vendor name.
     */
    public String getVendorId() {
        return this.vendorId;
    }
}
