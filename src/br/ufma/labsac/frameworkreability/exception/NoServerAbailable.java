package br.ufma.labsac.frameworkreability.exception;

/**
 * Created by MarioH on 17/03/2016.
 */
@SuppressWarnings("serial")
public class NoServerAbailable extends Exception {

   public NoServerAbailable(String arg0, Throwable arg1) {
      super(arg0, arg1);
   }

   public NoServerAbailable(String message) {
      super(message, new Throwable(message));
   }
}