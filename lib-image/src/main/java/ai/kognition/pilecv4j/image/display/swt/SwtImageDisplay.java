package ai.kognition.pilecv4j.image.display.swt;

import static net.dempsy.util.Functional.chain;

import java.util.function.Function;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.opencv.core.Mat;

import ai.kognition.pilecv4j.image.display.ImageDisplay;

public class SwtImageDisplay implements ImageDisplay {

   private final String name;

   private Display display = null;
   private Shell shell = null;

   private SwtCanvasImageDisplay canvasWriter = null;

   private boolean setupCalled = false;

   private final Function<Shell, SwtCanvasImageDisplay> canvasHandlerMaker;

   public SwtImageDisplay(final Mat mat, final String name, final Runnable closeCallback, final KeyPressCallback kpCallback,
         final SelectCallback selectCallback) {
      this.name = name;
      canvasHandlerMaker = s -> new ScrollableSwtCanvasImageDisplay(shell, closeCallback, kpCallback, selectCallback);
      // canvasHandlerMaker = s -> new ResizableSwtCanvasImageDisplay(shell, closeCallback, kpCallback, selectCallback);
      if(mat != null)
         update(mat);
   }

   @Override
   public void setCloseCallback(final Runnable closeCallback) {
      canvasWriter.setCloseCallback(closeCallback);
   }

   private void setup() {
      setupCalled = true;
      this.display = SwtUtils.getDisplay();
      display.syncExec(() -> {
         shell = new Shell(display);
         if(name != null) shell.setText(name);

         // set the GridLayout on the shell
         chain(new GridLayout(), l -> l.numColumns = 1, shell::setLayout);

         canvasWriter = canvasHandlerMaker.apply(shell);

         shell.addListener(SWT.Close, e -> {
            if(!shell.isDisposed())
               shell.dispose();
         });

         shell.open();
      });
   }

   @Override
   public synchronized void update(final Mat image) {
      if(!setupCalled)
         setup();

      canvasWriter.update(image);
   }

   @Override
   public void close() {
      if(display != null) {
         display.syncExec(() -> {
            if(canvasWriter != null)
               canvasWriter.close();
            if(shell != null)
               shell.close();
         });
      }
   }

   @Override
   public void waitUntilClosed() throws InterruptedException {
      canvasWriter.waitUntilClosed();
   }
}
