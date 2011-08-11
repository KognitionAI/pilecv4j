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


package com.jiminger.util;

import java.io.File;

public class FilenameUtils
{
   public static String lengthConstToString(int val, int len)
   {
      StringBuffer sbuf = new StringBuffer();
      int factor = 1;
//      int curval = val;

      for (int i = 0; i < len; i++)
         factor *= 10;

      for (boolean done = false; !done;)
      {
         if (val/factor == 0)
         {
            sbuf.append('0');
            factor /= 10;

            if (factor == 0)
               done = true;
         }
         else
         {
            sbuf.append(Integer.toString(val));
            done = true;
         }
      }

      return sbuf.toString();
   }

   public static File makeFile(File directory, String baseFilename, 
                               int filecount, int countfieldlen, String extention)
   {
      extention = extention.trim();
      String retfname = baseFilename + lengthConstToString(filecount,countfieldlen) +
         (extention.startsWith(".") ? "" : ".") + extention;

      return new File(directory,retfname);
   }
}

