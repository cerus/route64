/* $Id: d694c2a1c45a88333df89eac98fbdc44dce1eca9 $
 *
 * Common.
 *
 * Released under Gnu Public License
 * Copyright © 2016 Michael G. Binz
 */
package org.smack.application;

import java.awt.Image;
import java.util.Objects;
import org.smack.util.ServiceManager;
import org.smack.util.resource.ResourceManager;

/**
 * Application information.
 *
 * @author Michael Binz
 * @version $Rev$
 */
public class ApplicationInfo {

    private final Class<?> _applicationClass;
    private final String id;
    private final String title;
    private final String version;
    private final String vendor;
    private final String vendorId;
    private Image icon;

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
        final var arm =
                rm.getResourceMap(this._applicationClass);

        this.id = arm.get(
                "Application.id");
        this.title = arm.get(
                "Application.title");
        this.version = arm.get(
                "Application.version");
        try {
            this.icon = arm.getAs(
                    "Application.icon", Image.class);
        } catch (final Exception e) {
            this.icon = null;
        }
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
