package br.ufma.labsac.frameworkreability.util;

/**
 * Created by MarioH on 23/06/2016.
 */
public interface DeliveryReceiptCallback {
	void delivered(String fromJid, String toJid, String receiptId);
}