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

package com.jiminger.image.canny;

import java.awt.RenderingHints;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderedImageFactory;

import javax.media.jai.BorderExtender;
import javax.media.jai.ImageLayout;
import javax.media.jai.OperationDescriptorImpl;

import com.sun.media.jai.opimage.RIFUtil;


/**
 * A single class that is both an OperationDescriptor and
 * a RenderedImageFactory along with the one OpImage it is
 * capable of creating.  The operation implemented is a variation
 * on threshold, although the code may be used as a template for
 * a variety of other point operations.
 *
 */

@SuppressWarnings("restriction")
public class EdgeDetectorDescriptor extends OperationDescriptorImpl 
   implements RenderedImageFactory 
{

    private static final long serialVersionUID = 3771244856219230982L;

    /**
     * The resource strings that provide the general documentation and
     * specify the parameter list for the "EdgeDetector" operation.
     */


    private static final String[][] resources = {
        {"GlobalName",  "EdgeDetector"},
        {"LocalName",   "EdgeDetector"},
        {"Vendor",      "com.mycompany"},
        {"Description", "A sample operation that thresholds source pixels"},
        {"DocURL",      "http://www.mycompany.com/EdgeDetectorDescriptor.html"},
        {"Version",     "1.0"},
        {"arg0Desc",    "tlow"},
        {"arg1Desc",    "thigh"},
        {"arg2Desc",    "sigma"},
        {"arg3Desc",    "gradientDirImageHolder"}
    };

    /** 
     *  The parameter names for the "EdgeDetector" operation. Extenders may
     *  want to rename them to something more meaningful. 
     */


    private static final String[] paramNames = {
       "tlow", "thigh", "sigma", "gradientDirImageHolder"
    };
 
    /** 
     *  The class types for the parameters of the "EdgeDetector" operation.  
     *  User defined classes can be used here as long as the fully 
     *  qualified name is used and the classes can be loaded.
     */


    private static final Class<?>[] paramClasses = {
       java.lang.Float.class, 
       java.lang.Float.class, 
       java.lang.Float.class,
       EdgeDetectorOpImage.GradientDirectionImageHolder.class
    };
 
    /**
     * The default parameter values for the "EdgeDetector" operation
     * when using a ParameterBlockJAI.
     */


    private static final Object[] paramDefaults = {
       new Float(0.5),
       new Float(0.5),
       new Float(2.0),
       null
    };

    /**
     * The supported modes.
     */


    private static final String[] supportedModes = {
	"rendered"
    };

    /** Constructor. */


    public EdgeDetectorDescriptor() {
        super(resources, supportedModes, 1, paramNames, paramClasses, 
	      paramDefaults, null);
    }

    /** 
     *  Creates a EdgeDetectorOpImage with the given ParameterBlock if the 
     *  EdgeDetectorOpImage can handle the particular ParameterBlock.
     */


    public RenderedImage create(ParameterBlock pb,
                                RenderingHints renderHints) {
        if (!validateParameters(pb)) {
            return null;
        }

        ImageLayout imagelayout = RIFUtil.getImageLayoutHint(renderHints);
        BorderExtender borderextender = RIFUtil.getBorderExtenderHint(renderHints);

        float lowThreshold = pb.getFloatParameter(0);
        float highThreshold = pb.getFloatParameter(1);
        float sigma = pb.getFloatParameter(2);
        EdgeDetectorOpImage.GradientDirectionImageHolder gradientDirImageHolder = 
           (EdgeDetectorOpImage.GradientDirectionImageHolder)pb.getObjectParameter(3);

        return new EdgeDetectorOpImage(pb.getRenderedSource(0),
                                       imagelayout,renderHints,borderextender,
                                       lowThreshold,highThreshold,sigma,
                                       gradientDirImageHolder);
    }

    /**
     *  Checks that all parameters in the ParameterBlock have the 
     *  correct type before constructing the EdgeDetectorOpImage
     */


    public boolean validateParameters(ParameterBlock paramBlock) {
       return true;
    }
}



