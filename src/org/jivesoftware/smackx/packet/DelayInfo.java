package org.jivesoftware.smackx.packet;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.jivesoftware.smack.util.DateFormatType;
import org.jivesoftware.smack.util.StringUtils;

public class DelayInfo extends DelayInformation {
   private static SimpleDateFormat dateTimeFormatter;

	DelayInformation wrappedInfo;
   
   static {
   	dateTimeFormatter = DateFormatType.XEP_0082_DATETIME_MILLIS_PROFILE.createFormatter();
   	TimeZone var0 = TimeZone.getTimeZone("UTC");
   	dateTimeFormatter.setTimeZone(var0);
   }

   public DelayInfo(DelayInformation var1) {
      super(var1.getStamp());
      this.wrappedInfo = var1;
   }

   public String getFrom() {
      return this.wrappedInfo.getFrom();
   }

   public String getReason() {
      return this.wrappedInfo.getReason();
   }

   public Date getStamp() {
      return (this.wrappedInfo.getStamp());
   }

   public void setFrom(String var1) {
      this.wrappedInfo.setFrom(var1);
   }

   public void setReason(String var1) {
      this.wrappedInfo.setReason(var1);
   }

   public String getElementName() {
      return "delay";
   }

   public String getNamespace() {
      return "urn:xmpp:delay";
   }

   public String toXML() {
      StringBuilder var1 = new StringBuilder();
      var1.append("<").append(this.getElementName()).append(" xmlns=\"").append(this.getNamespace()).append("\"");
      var1.append(" stamp=\"");
      var1.append(formatXEP0082Date(this.getStamp()));
      var1.append("\"");
      if(this.getFrom() != null && this.getFrom().length() > 0) {
         var1.append(" from=\"").append(this.getFrom()).append("\"");
      }

      var1.append(">");
      if(this.getReason() != null && this.getReason().length() > 0) {
         var1.append(this.getReason());
      }

      var1.append("</").append(this.getElementName()).append(">");
      return var1.toString();
   }
   
   public static String formatXEP0082Date(Date var0) {
      DateFormat var1 = dateTimeFormatter;
      synchronized(dateTimeFormatter) {
         return dateTimeFormatter.format(var0);
      }
   }
}