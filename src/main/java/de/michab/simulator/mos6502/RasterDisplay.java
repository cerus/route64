/* $Id: RasterDisplay.java 11 2008-09-20 11:06:39Z binzm $
 *
 * Project: Route64
 *
 * Released under GNU public license (www.gnu.org/copyleft/gpl.html)
 * Copyright (c) 2000-2005 Michael G. Binz
 */
package de.michab.simulator.mos6502;

import de.michab.simulator.Clock;
import de.michab.simulator.Memory;
import de.michab.simulator.Processor;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.Arrays;


/**
 * Responsible for rastering the whole VIC screen.  Controls the video mode
 * dependent rasterers as well as the sprite rasterer.
 * <p>
 * TODO we need the full timing of the VIC.  The following questions have to
 * be answered:
 * How many cycles does it take between two full images, i.e. how long does it
 * take the beam to return from the bottom of the video screen to the top
 * where the next pictures starts?
 * What is the impact if the video screen is switched off (Like when doing a
 * datasette load) Is that then for all raster lines the same as with the
 * frame lines?
 *
 * @author Michael G. Binz
 * @version $Revision: 11 $
 * @see ScanlineRasterer
 * @see RasterSprites
 */
public class RasterDisplay
        extends Component
        implements Runnable {

    /**
     * Frame width in pixels. Used for the right and left frame.
     */
    static final int FRAME_HORIZ = 4 * 8;
    /**
     * Height of the visible center window.
     */
    static final int INNER_VERT = 200;
    /**
     * Width of the visible center window.
     */
    static final int INNER_HORIZ = 320;
    /**
     * Overall width of the screen, includes the frame.
     */
    static final int OVERALL_W =
            FRAME_HORIZ + INNER_HORIZ + FRAME_HORIZ;
    /**
     *
     */
    private static final long serialVersionUID = 4468735705975651008L;
    private static final boolean _debug = false;
    /**
     * The height of the vertical frame.  Note that this also is the scanline
     * number of the first line of the display window.
     */
    private static final int FRAME_VERT = 51;
    /**
     * Visible frame height in pixels.  Used for the upper and lower frame.  Note
     * that this value can be adjusted freely without impacting the emulation.
     */
    private static final int VISIBLE_FRAME_VERT = 3 * 8;
    /**
     * The number of invisible scan lines.
     */
    static private final int VERTICAL_INVISIBLE =
            FRAME_VERT - VISIBLE_FRAME_VERT;
    /**
     * Overall height of the screen, includes the frame.
     */
    private static final int OVERALL_H =
            VISIBLE_FRAME_VERT + INNER_VERT + VISIBLE_FRAME_VERT;


    /**
     * Sprite coordinate system offset in y direction.
     */
    private static final int SPRITE_Y_OFFSET = 30;
    /**
     * Used for layout management.
     */
    private static final Dimension _componentsSize
            = new Dimension(OVERALL_W, OVERALL_H);
    /**
     * The monochrome text rasterer.
     */
    private final ScanlineRasterer _txtNormal;
    /**
     * The multi colour text mode rasterer.
     */
    private final ScanlineRasterer _txtMulti;
    /**
     * The extended color text mode rasterer.
     */
    private final ScanlineRasterer _txtExt;
    /**
     * The monochrome bitmap rasterer.
     */
    private final ScanlineRasterer _gfxNormal;
    /**
     * The multi color bitmap rasterer.
     */
    private final ScanlineRasterer _gfxMulti;
    /**
     * The raster engine responsible for sprite rastering.
     */
    private final RasterSprites _spriteRasterer;
    /**
     * Our handle to the system clock.
     */
    private final Clock.ClockHandle _clockId;
    /**
     * The display raster.  Each integer in this array represents one pixel
     * on the 64s screen in rgb color.
     */
    private final int[] _screen = new int[OVERALL_W * OVERALL_H];
    /**
     * The image that is ultimately drawn onto the component.
     */
    private final BufferedImage _bufferedImage =
            new BufferedImage(OVERALL_W,
                    OVERALL_H,
                    BufferedImage.TYPE_INT_RGB);
    /**
     * A reference to our home VIC.
     */
    private final Vic _vic;
    /**
     * A reference to the computer's main memory.
     */
    private final Memory _memory;
    /**
     * A reference to color RAM.
     */
    private final byte[] _colorRam;
    /**
     * This thread drives display drawing.
     */
    private final Thread _repaintThread;
    /**
     * A reference to the current video mode raster engine.
     */
    private ScanlineRasterer _currentVideoMode;
    private ScanlineRasterer _scheduledVideoMode;
    /**
     * The raster line that is currently drawn.
     */
    private int _currentRasterLine = 0;


    /**
     *
     */
    private int _characterSetAdr = 0;


    /**
     *
     */
    private int _videoRamAddress = 0;


    /**
     *
     */
    private int _bitmapAddress = 0;


    /**
     * The graphics object used for drawing.  The instance is injected by paint
     * calls from our UI context.
     *
     * @see RasterDisplay#paint(Graphics)
     */
    private Graphics _graphics = null;


    /**
     * Creates a raster display instance.
     *
     * @param vic      The home VIC.
     * @param mem      A reference to the main memory.
     * @param colorRam The color RAM.
     */
    protected RasterDisplay(
            final Vic vic,
            final Memory mem,
            final byte[] colorRam,
            final Clock clock) {
        this.setSize(_componentsSize);

        this._vic = vic;
        this._colorRam = colorRam;

        // Get a reference to the system's memory.
        this._memory = mem;

        // Init the sprite rasterer.
        this._spriteRasterer = new RasterSprites(
                this._screen,
                this._memory,
                vic);

        // Init the default video mode raster engine.
        this._txtNormal = new RasterCharacter(
                vic,
                this._screen,
                this._memory,
                this._colorRam);

        this._txtMulti = new RasterCharacterMulti(
                vic,
                this._screen,
                this._memory,
                this._colorRam);

        this._txtExt = new RasterCharacterExtended(
                vic,
                this._screen,
                this._memory,
                this._colorRam);

        this._gfxNormal = new RasterBitmap(
                this._screen,
                this._memory);

        this._gfxMulti = new RasterBitmapMulti(
                vic,
                this._screen,
                this._memory,
                this._colorRam);

        // Set the default rasterer.
        this._currentVideoMode = this._txtNormal;

        // Register with the clock.
        this._clockId = clock.register();
        // Create the repaint thread.
        this._repaintThread = new Thread(this, this.getClass().getName());
        this._repaintThread.setDaemon(true);
        this._repaintThread.start();
    }


    /**
     * Reset the component to its preferred size.
     */
    public void resetSize() {
        this.setSize(this.getPreferredSize());
    }


    /**
     * Set the addresses of the different memory regions in a single step.
     *
     * @param charAdr
     * @param bitmap
     * @param videoram
     */
    synchronized final void setAddresses(final int charAdr, final int bitmap, final int videoram) {
        this._characterSetAdr = charAdr;
        this._videoRamAddress = videoram;
        this._bitmapAddress = bitmap & 0xe000;
        this._currentVideoMode.startFrame(
                this._characterSetAdr,
                this._videoRamAddress,
                this._bitmapAddress);
    }


    /**
     * Shuts down the raster thread.
     */
    void terminate() {
        this._repaintThread.interrupt();
    }


    /**
     * Thread driven main repaint loop.
     */
    @Override
    public void run() {
        this._clockId.prepare();
        try {
            // We unschedule the repaint thread until we are actually receiving
            // the first paint notification.  For the reschedule() operation
            // see the paint() implementation.
            this._clockId.unschedule();

            while (!this._repaintThread.isInterrupted()) {
                this._currentVideoMode.startFrame(
                        this._characterSetAdr,
                        this._videoRamAddress,
                        this._bitmapAddress);
                // Draw a single frame.
                this.drawFrame();
            }
        }
        // Catch all remaining untagged exceptions.  ArrayIndexOutOfBounds is quite
        // common here.
        catch (final Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }


    /**
     * Draws a single frame.  The all-time goal for this method is: DRAW THE
     * RASTER FASTER.  This is an example for really *hot* code performance-wise.
     */
    private void drawFrame() {
        final int rasterMax =
                FRAME_VERT +
                        INNER_VERT +
                        FRAME_VERT;

        // Iterate over one scanline after the other.
        for (
                this._currentRasterLine = 0;
                this._currentRasterLine < rasterMax;
                this._currentRasterLine++) {
            if (this.isBadLine(this._currentRasterLine)) {
                this._vic.stealCycles(40);

                if (this._scheduledVideoMode != null) {
                    this._scheduledVideoMode.startFrame(
                            this._characterSetAdr,
                            this._videoRamAddress,
                            this._bitmapAddress);
                    this._currentVideoMode = this._scheduledVideoMode;
                    this._scheduledVideoMode = null;
                }

                this._currentVideoMode.badLine(this._currentRasterLine);
            }

            this.drawRasterLine(this._currentRasterLine);
            this._clockId.advance(64);
        }

        // Raster screen is complete and up to date, now beam the whole thing into
        // the image...
        this._bufferedImage.getRaster().setDataElements(
                0,
                0,
                OVERALL_W,
                OVERALL_H,
                this._screen);

        // ...and bang out the data to where the sun always shines.
        this._graphics.drawImage(
                this._bufferedImage,
                0,
                0,
                this.getWidth(),
                this.getHeight(),
                0,
                0,
                this._bufferedImage.getWidth(),
                this._bufferedImage.getHeight(),
                null);
    }


    /**
     * Draws a particular raster line.
     *
     * @param rasterLine The raster line to draw.
     */
    private void drawRasterLine(final int rasterLine) {
//    int offset = screenOffsetY();
        final int frameColor = Vic.VIC_RGB_COLORS[this._vic.read(Vic.EXTERIORCOL)];

        // Check for raster irqs and their relatives.
        final boolean isRasterInterruptLine =
                rasterLine == this.getInterruptRasterLine();
        if (isRasterInterruptLine) {
            this._vic.rasterInterrupt();
        }

        // Leave if not in the visible area.
        if (rasterLine < VERTICAL_INVISIBLE ||
                rasterLine >= FRAME_VERT + INNER_VERT + VISIBLE_FRAME_VERT) {
            return;
        }

        // The index of the current raster line in the screen array.
        final int rasterlineIdx =
                (rasterLine - VERTICAL_INVISIBLE) * OVERALL_W;

        /////////////////////////////////
        // Draw the inner character area.
        /////////////////////////////////
        final int framePlusTopBottom = this.isWideBorderY() ? 7 : 0;
        if (rasterLine >= (FRAME_VERT + framePlusTopBottom) &&
                rasterLine < (FRAME_VERT + INNER_VERT - framePlusTopBottom) &&
                this.isScreenOn()) {
            // Fill the current scanline with background pixels.
            this._currentVideoMode.backfill(rasterlineIdx + FRAME_HORIZ);

            // Raster the background sprites.
            this._spriteRasterer.rasterBackInto(
                    this._videoRamAddress,
                    rasterlineIdx,
                    rasterLine - FRAME_VERT + SPRITE_Y_OFFSET);

            // The current raster mode is responsible for drawing the screen's
            // content.
            this._currentVideoMode.rasterInto(
                    rasterlineIdx + FRAME_HORIZ,
                    rasterLine - FRAME_VERT);

            // Raster the foreground sprites.
            this._spriteRasterer.rasterFrontInto(
                    rasterlineIdx,
                    rasterLine - FRAME_VERT + SPRITE_Y_OFFSET);

            // Draw the right and left frame.
            final int framePlusLeft;
            final int framePlusRight;
            if (this.isWideBorderX()) {
                framePlusLeft = 7;
                framePlusRight = 9;
            } else {
                framePlusLeft = 0;
                framePlusRight = 0;
            }
            Arrays.fill(
                    this._screen,
                    rasterlineIdx,
                    rasterlineIdx + FRAME_HORIZ + framePlusLeft,
                    frameColor);
            Arrays.fill(
                    this._screen,
                    rasterlineIdx + FRAME_HORIZ + INNER_HORIZ - framePlusRight,
                    rasterlineIdx + FRAME_HORIZ + INNER_HORIZ + FRAME_HORIZ,
                    frameColor);
        }
        ///////////////////////
        // Draw the frame part.
        ///////////////////////
        else {
            // Draw the top and bottom frame scanlines.
            Arrays.fill(
                    this._screen,
                    rasterlineIdx,
                    rasterlineIdx + OVERALL_W,
                    frameColor);
        }

        if (_debug) {
            this._screen[rasterlineIdx] =
                    Vic.VIC_RGB_COLORS[this._currentVideoMode.getDebugColor()];
            this._screen[rasterlineIdx + 1] =
                    Vic.VIC_RGB_COLORS[Vic.BLACK_IDX];
            this._screen[rasterlineIdx + 2] =
                    Vic.VIC_RGB_COLORS[
                            isRasterInterruptLine ? Vic.WHITE_IDX : Vic.BLACK_IDX];
            this._screen[rasterlineIdx + 3] =
                    Vic.VIC_RGB_COLORS[Vic.BLACK_IDX];
            this._screen[rasterlineIdx + 4] = Vic.VIC_RGB_COLORS[
                    this.isBadLine(rasterLine) ? Vic.WHITE_IDX : Vic.BLACK_IDX];
            this._screen[rasterlineIdx + 5] =
                    Vic.VIC_RGB_COLORS[Vic.BLACK_IDX];
        }
    }


    /**
     * Set the video mode according to the passed flags.
     *
     * @param bitmap   <code>True</code> if graphics mode is selected.
     * @param extended <code>True</code> if extended color mode is selected.
     * @param multi    <code>True</code> if multi color mode is selected.
     */
    synchronized void setVideoMode(
            final boolean bitmap,
            final boolean extended,
            final boolean multi) {
        final ScanlineRasterer newVideoMode;

        if (bitmap) {
            // Extended isn't supported for bitmap modes.
            if (multi) {
                newVideoMode = this._gfxMulti;
            } else {
                newVideoMode = this._gfxNormal;
            }
        } else {
            // Not clear what to do if multi and extended are set.  Currently multi-
            // color just overrides extended.
            if (multi) {
                newVideoMode = this._txtMulti;
            } else if (extended) {
                newVideoMode = this._txtExt;
            } else {
                newVideoMode = this._txtNormal;
            }
        }

        this._scheduledVideoMode = newVideoMode;
    }


    /**
     * Returns the current raster line.
     *
     * @return The current raster line.
     */
    synchronized int getCurrentRasterLine() {
        return this._currentRasterLine;
    }


    /**
     * Get the raster line that will trigger an interrupt on drawing.
     *
     * @return The interrupt raster line that is currently set.
     */
    private int getInterruptRasterLine() {
        // Note that we cannot read() the vic here since the RASTERIRQ register has
        // different behaviour on read and write:  Write sets the raster line on
        // which an interrupt is triggered, read reads the current raster line.
        int rasterLine = this._vic.getRawRegisters()[Vic.RASTERIRQ];
        rasterLine &= 0xff;
        if ((this._vic.read(Vic.CTRL1) & Processor.BIT_7) != 0) {
            rasterLine |= 0x100;
        }
        return rasterLine;
    }


    /**
     * Is the display switched on?
     *
     * @return True if display is on, else false.
     */
    private boolean isScreenOn() {
        final byte ctrl1 = this._vic.read(Vic.CTRL1);
        return 0 != (ctrl1 & Processor.BIT_4);
    }


    /**
     * Get the offset of the visible display window.  This is a three bit value
     * that can be accessed in VIC register CTRL1 bits 2-0.
     *
     * @return The y offset of the visible display window.
     */
    private int screenOffsetY() {
        return this.screenOffset(Vic.CTRL1);
    }


    /**
     * The common implementation for screen offset in x and y direction.
     *
     * @param register The register to read.  One of CTRL1 or CTRL2.
     *
     * @return The offset of the visible display window.
     */
    private int screenOffset(final int register) {
        final byte ctrl = this._vic.read(register);
        return ctrl & (Processor.BIT_2 | Processor.BIT_1 | Processor.BIT_0);
    }


    /**
     * Checks whether the right and left borders have to be drawn in wide mode.
     * This check is based on VIC register 0x16, bit 3.
     *
     * @return If the border is to be drawn extended, <code>true</code> is
     * returned.
     */
    private boolean isWideBorderX() {
        return this.isWideBorder(Vic.CTRL2);
    }


    /**
     * Checks whether the top and bottom borders have to be drawn in wide mode.
     * This check is based on VIC register 0x16, bit 3.
     *
     * @return If the border is to be drawn extended, <code>true</code> is
     * returned.
     */
    private boolean isWideBorderY() {
        return this.isWideBorder(Vic.CTRL1);
    }


    /**
     * The common implementation for the wide border tests.
     *
     * @param register Allowed are either Vic.CTRL1 or Vic.CTRL2.
     *
     * @return True if bit 3 of the respective register is not set.
     */
    private boolean isWideBorder(final int register) {
        return 0 == (this._vic.read(register) & Processor.BIT_3);
    }


    /**
     * Check whether this is a bad line.
     *
     * @return true if this is a bad line.
     */
    private boolean isBadLine(final int scanline) {
        return (scanline & 7) == this.screenOffsetY();
    }


    /*
     * Inherit Javadoc.
     */
    @Override
    public Dimension getPreferredSize() {
        return _componentsSize;
    }


    /*
     * Inherit Javadoc.
     */
    @Override
    public Dimension getMinimumSize() {
        return this.getPreferredSize();
    }


    /**
     * Handles the component paint.  The implementation only has the
     * responsibility to compute the graphics object that is used for screen
     * updating.  Actual screen updating occurs in an internal thread, not here.
     *
     * @param g The graphics object to use for painting.
     *
     * @see java.awt.Component#paint(java.awt.Graphics)
     */
    @Override
    public void paint(final Graphics g) {
        final Graphics oldGfx = this._graphics;
        // Since the graphics object passed into this method will be disposed()
        // by the ui subsystem, we create one for our own usage.
        this._graphics = this.getGraphics();

        if (oldGfx != null) {
            oldGfx.dispose();
        } else
        // We had no previous graphics object, so this must be the very first
        // paint.  Start the raster thread.
        {
            this._clockId.reschedule();
        }
    }
}
