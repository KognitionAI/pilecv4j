import java.util.Locale;

import org.junit.Test;


public class TestOS
{

   @Test
   public void testOS() throws Throwable
   {
      String OS_NAME = System.getProperty( "os.name" ).toLowerCase( Locale.US );

      String OS_ARCH = System.getProperty( "os.arch" ).toLowerCase( Locale.US );

      String OS_VERSION = System.getProperty( "os.version" ).toLowerCase( Locale.US );
      
      System.out.println("name:" + OS_NAME);
      System.out.println("arch:" + OS_ARCH);
      System.out.println("version:" + OS_VERSION);

   }
}
