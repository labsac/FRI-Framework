package br.ufma.labsac.frameworkreability.util;

/**
 * Created by M�rio on 01/06/2016.
 */
public interface ServerConnectionResponseListener {
	void responseConnection(boolean response);
	void connectionDown();
}