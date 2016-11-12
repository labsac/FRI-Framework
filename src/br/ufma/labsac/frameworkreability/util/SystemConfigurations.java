package br.ufma.labsac.frameworkreability.util;

/**
 * Created by labsac on 25/05/2016.
 */
public class SystemConfigurations {
	private static final SystemConfigurations instance;

	static {
		instance = new SystemConfigurations();
	}

	/** time in seconds */
	public int reconnectionTime = 30;

	public String username;
	public String password;
	public String service;
	public int port;
	public String[] serversList;

	/** configurações da Configuração para Conexão */
	public boolean rosterLoadedAtLogin = false;
	public boolean reconnectionAllowed = false; // or true if you want to use built in reconnection
	public boolean compressionEnabled = false;
	public boolean debuggerEnabled = true;

	public boolean setSendPresence = true; // o padrão é True

	public boolean autoConnect = true;

	public static SystemConfigurations getConfigurations() {
		return (instance);
	}
}