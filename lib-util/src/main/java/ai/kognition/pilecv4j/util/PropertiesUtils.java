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

package ai.kognition.pilecv4j.util;

import java.util.*;
import java.io.*;

public class PropertiesUtils
{
   public static final String separator = ".";

   public static Properties getSection(Properties props, String sectionName, boolean removeSectionName)                                                                         
   {                                                                                                                                                                            
      Properties ret = new Properties();                                                                                                                                        
                                                                                                                                                                                
      for(Enumeration<?> e = props.propertyNames(); e.hasMoreElements();)                                                                                                       
      {                                                                                                                                                                         
         String key = (String)(e.nextElement());                                                                                                                                
         if(key.startsWith(sectionName + "."))                                                                                                                                  
         {                                                                                                                                                                      
            String newkey = removeSectionName ? key.substring(sectionName.length() + 1) : key;                                                                                  
                                                                                                                                                                                
            ret.setProperty(newkey, props.getProperty(key));                                                                                                                    
         }                                                                                                                                                                      
         else if(key.equals(sectionName) && !removeSectionName)                                                                                                                 
         {                                                                                                                                                                      
            ret.setProperty(key, props.getProperty(key));                                                                                                                       
         }                                                                                                                                                                      
      }                                                                                                                                                                         
                                                                                                                                                                                
      return ret;                                                                                                                                                               
   }                                                                                                                                                                            
   
   public static boolean loadProps(Properties p, String fname)
   {
      FileInputStream fis = null;
      try
      {
         fis = new FileInputStream(fname);
         p.load(fis);
      }
      catch (IOException ioe)
      {
         System.out.println("Couldn't load properties from " + fname);
         return false;
      }
      finally
      {
         try { if (fis != null) fis.close(); } catch (Throwable th) {}
      }

      return true;
   }

   public static void saveProps(Properties p, String fname, String comment)
      throws IOException
   {
      FileOutputStream propertiesStream = new FileOutputStream(fname);
      PrintStream os = new PrintStream(propertiesStream);
      os.println("# " + comment);
      os.println();

      List<String> keys = new ArrayList<String>();
      for (Object c : p.keySet())
         keys.add((String)c);
      Collections.sort(keys);
      for (Iterator<String> iter = keys.iterator(); iter.hasNext();)
      {
         String key = (String)iter.next();
         String val = p.getProperty(key);
         os.println(key + "=" + val);
      }
      os.flush();
      os.close();
   }
}
