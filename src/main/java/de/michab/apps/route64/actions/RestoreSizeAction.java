/* $Id: RestoreSizeAction.java 782 2015-01-05 18:05:25Z Michael $
 *
 * Route64.
 *
 * Released under Gnu Public License
 * Copyright © 2010 Michael G. Binz
 */

package de.michab.apps.route64.actions;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JFrame;


/**
 *
 * @version $Rev: 782 $
 * @author Michael Binz
 */
@SuppressWarnings("serial")
public class RestoreSizeAction extends AbstractAction
{
    private JFrame _application;

    /**
     * @param key
     */
    public RestoreSizeAction( String key, JFrame app )
    {
        super( key );

        _application = app;
    }

    /* (non-Javadoc)
     * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
     */
    @Override
    public void actionPerformed( ActionEvent e )
    {
        _application.pack();
    }
}
