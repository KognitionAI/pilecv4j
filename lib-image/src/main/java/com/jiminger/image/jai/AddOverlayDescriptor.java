package com.jiminger.image.jai;
/***********************************************************************
    Legacy Film to DVD Project
    Copyright (C) 2005 James F. Carroll

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
****************************************************************************/


import javax.media.jai.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.image.renderable.*;
import com.sun.media.jai.opimage.*;

/**
 * AddOverlayOpImage is an extension of PointOpImage that takes two
 * integer parameters and one source and performs a modified threshold
 * operation on the given source.
 */

@SuppressWarnings("restriction")
public class AddOverlayDescriptor extends OperationDescriptorImpl 
   implements RenderedImageFactory 
{

    /**
     * 
     */
    private static final long serialVersionUID = -6807987877249299371L;

    /**
     * The resource strings that provide the general documentation and
     * specify the parameter list for the "AddOverlay" operation.
     */


    private static final String[][] resources = {
        {"GlobalName",  "AddOverlay"},
        {"LocalName",   "AddOverlay"},
        {"Vendor",      "com.mycompany"},
        {"Description", "A sample operation that thresholds source pixels"},
        {"DocURL",      "http://www.mycompany.com/AddOverlayDescriptor.html"},
        {"Version",     "1.0"}
    };

    /** 
     *  The parameter names for the "AddOverlay" operation. Extenders may
     *  want to rename them to something more meaningful. 
     */


    private static final String[] paramNames = {
    };
 
    /** 
     *  The class types for the parameters of the "AddOverlay" operation.  
     *  User defined classes can be used here as long as the fully 
     *  qualified name is used and the classes can be loaded.
     */


    private static final Class<?>[] paramClasses = {
    };
 
    /**
     * The default parameter values for the "AddOverlay" operation
     * when using a ParameterBlockJAI.
     */


    private static final Object[] paramDefaults = {
    };

    /**
     * The supported modes.
     */


    private static final String[] supportedModes = {
	"rendered"
    };

    /** Constructor. */


    public AddOverlayDescriptor() {
        super(resources, supportedModes, 1, paramNames, paramClasses, 
	      paramDefaults, null);
    }

    /** 
     *  Creates a AddOverlayOpImage with the given ParameterBlock if the 
     *  AddOverlayOpImage can handle the particular ParameterBlock.
     */


    public RenderedImage create(ParameterBlock pb,
                                RenderingHints renderHints) {
        if (!validateParameters(pb)) {
            return null;
        }

        ImageLayout imagelayout = RIFUtil.getImageLayoutHint(renderHints);
//        BorderExtender borderextender = RIFUtil.getBorderExtenderHint(renderHints);

        return new AddOverlayOpImage(pb.getRenderedSource(0),
                                     imagelayout,renderHints);
    }

    /**
     *  Checks that all parameters in the ParameterBlock have the 
     *  correct type before constructing the AddOverlayOpImage
     */


    public boolean validateParameters(ParameterBlock paramBlock) {
       return true;
    }
}

