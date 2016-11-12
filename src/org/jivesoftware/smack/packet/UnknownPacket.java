package org.jivesoftware.smack.packet;

public abstract class UnknownPacket extends Packet implements PacketExtension {
   public UnknownPacket() {}

	public String toXML() {
		StringBuilder var1 = new StringBuilder();
		var1.append("<").append(this.getElementName());
		if (this.getXmlns() != null) {
			var1.append(" xmlns=\"").append(this.getNamespace()).append("\"");
		}

		var1.append("/>");
		return var1.toString();
	}
}