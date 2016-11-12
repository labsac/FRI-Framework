package br.ufma.labsac.frameworkreability;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingWorker;

import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Type;
import org.jivesoftware.smackx.packet.DelayInfo;
import org.yaxim.androidclient.service.XmppStreamHandler;

import br.ufma.labsac.frameworkreability.exception.NoServerAbailable;
import br.ufma.labsac.frameworkreability.util.ServerConnectionResponseListener;
import br.ufma.labsac.frameworkreability.util.SystemConfigurations;
import br.ufma.labsac.frameworkreability.util.XMPPStatus;

public class XMPPConnectionManager {
	private static final int packetReplyTimeout = 500; // millis
	private static final int RECONNECT_AFTER = 5;
	private static final int RECONNECT_MAXIMUM = 600;
	private final SystemConfigurations mXMPPConfig;
	private final Object mConnectingThreadMutex = new Object();

	private int mReconnectTimeout = RECONNECT_AFTER;
	private ServerConnectionResponseListener serverConnectionResponseListener;

	/** inicia com não reconexão automática */
	private AtomicBoolean mConnectionReconn = new AtomicBoolean(false);
	private XMPPConnection mXMPPConnection;
	private XMPPStatus actualState = XMPPStatus.OFFLINE;
	private ConnectionConfiguration mXMPPConnConfig;
	private XmppStreamHandler mStreamHandler;
	private PacketListener mPacketListener;
	private Thread mConnectingThread;
	private ChatManager chatManager;
	private ConnectionListener mConnectionListener;
//	private DeliveryReceiptCallback deliveryCallback;

	/**
	 * Construtor para configura&ccedil;&atilde;o e cria&ccedil;&atilde;o da conex&atilde;o.
	 *
	 * @throws NoServerAbailable Exce&ccedil;&atilde;o que indica que nenhum servidor est&aacute;
	 *                           disponp&iacute;vel
	 * @throws XMPPException     Erro XMPP
	 */
	public XMPPConnectionManager() throws NoServerAbailable, XMPPException {
		SmackConfiguration.setPacketReplyTimeout(packetReplyTimeout);

		mXMPPConfig = SystemConfigurations.getConfigurations();

		mConnectionReconn.set(mXMPPConfig.autoConnect);
	}
	
	public void changedState(XMPPStatus changeTo) {
		actualState = changeTo;

		switch (actualState) {
		case DISCONNECTED:
			connectionFailed("Perdeu conexão"); // getString(R.string.conn_disconnected)
			break;
		default:
			break;
		}
	}
	
	public void requestChangeState(XMPPStatus changeTo) {
		if (changeTo == actualState)
			return;

		switch (changeTo) {
			case ONLINE:

				switch (actualState) {
					case RECONNECT_DELAYED:
						// case RECONNECT_NETWORK:
					case OFFLINE:

						changedState(XMPPStatus.CONNECTING);

						new Thread() {
							/**
							 * Calls the <code>run()</code> method of the Runnable object the receiver
							 * holds. If no Runnable is set, does nothing.
							 *
							 * @see Thread#start
							 */
							@Override
							public void run() {
								updateConnectingThread(this);

								try {
									connect();

								} catch (NoServerAbailable noServerAbailable) {
									noServerAbailable.printStackTrace();
									disconnected(noServerAbailable);
								} finally {
									finishConnectingThread();
								}
							}
						}.start();
						break;
					default:
						break;
				}
				break;
			case RECONNECT_DELAYED:
				switch (actualState) {
					case DISCONNECTED:
						changedState(changeTo);
					default:
						break;
				}
				break;
			default:
				break;
		}
	}
	
	private void connectionFailed(String reason) {
		try {
			if (!networkConnected()) {

			} else if (mConnectionReconn.get()) {
				requestChangeState(XMPPStatus.RECONNECT_DELAYED);

				// PEDE PARA RECONECTAR AQUI:
				CountDownTask countDownTask = new CountDownTask();
				countDownTask.execute();

				mReconnectTimeout *= 2;
				if (mReconnectTimeout > RECONNECT_MAXIMUM)
					mReconnectTimeout = RECONNECT_MAXIMUM;

			} else/* connectionClosed() */;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Inicializa as configurações para conexão.<br>
	 * É feita uma tentativa para cada servidor cadastrado até que um se conecte
	 *
	 * @throws NoServerAbailable Nenhum servidor está disponível
	 */
	public void connect() throws NoServerAbailable {
		String[] list = mXMPPConfig.serversList;

		for (String item : list) { // testar para cada server
			System.out.println(String.format("Initializing connection to server %1$s port " + "%2$d", item, mXMPPConfig.port));

			try {
				mXMPPConnConfig = new ConnectionConfiguration(item, mXMPPConfig.port, mXMPPConfig.service);

				mXMPPConnConfig.setSASLAuthenticationEnabled(true);
				mXMPPConnConfig.setReconnectionAllowed(mXMPPConfig.reconnectionAllowed);
//				mXMPPConnConfig.setRosterLoadedAtLogin(mXMPPConfig.rosterLoadedAtLogin);
				mXMPPConnConfig.setCompressionEnabled(mXMPPConfig.compressionEnabled); // opcional
				mXMPPConnConfig.setDebuggerEnabled(mXMPPConfig.debuggerEnabled);
//				mXMPPConnConfig.setSendPresence(mXMPPConfig.setSendPresence); // config opcional

				setTLSConfig();  // configura conexão com TLS

				if (performConnection()) {
					changedState(XMPPStatus.ONLINE);
					serverConnectionResponseListener.responseConnection(true);
					break;
				} else serverConnectionResponseListener.responseConnection(false);

			} catch (Exception e) {
				e.printStackTrace();
				System.out.println(e.getMessage());
			}
		}

		//	MyMessageListener messageListener = new MyMessageListener();
	}
	
	/**
	 * Inicializa de fato a conexão com o servidor e faz o login do usuário no mesmo
	 *
	 * @return True se conexão bem sucedida
	 */
	public boolean performConnection() {
		if (mXMPPConnConfig != null) {
			try {
				this.mXMPPConnection = new XMPPConnection(mXMPPConnConfig);
//				this.mStreamHandler = new XmppStreamHandler(mXMPPConnection, true);

//				this.mStreamHandler.addAckReceivedListener(new XmppStreamHandler.AckReceivedListener() {
//					public void ackReceived(long handled, long total) {}
//				});
//
//				if (mXMPPConnection.isAuthenticated()) {
//					try {
//						mStreamHandler.quickShutdown();
//
//					} catch (Exception e) {	}
//				}

				if (mConnectionListener != null)
					mXMPPConnection.removeConnectionListener(mConnectionListener);

				mConnectionListener = new ConnectionListener() {
					@Override
					public void connectionClosed() {
						// Toast.makeText(xmppClient, "Connection closed", Toast
						// .LENGTH_SHORT).show();
					}

					@Override
					public void connectionClosedOnError(Exception e) {
						// Toast.makeText(ctx, "Connection Error", Toast.LENGTH_SHORT)
						// .show();

						// a partir daqui avisar que o servidor caiu e tentar voltar com a conexão
						// performLogout();
						// mXMPPConnection.shutdown();
						// mStreamHandler.close();
						// new Thread(new ReconnectServer()).start();
						disconnected(e);
					}

					@Override
					public void reconnectingIn(int i) {
					}

					@Override
					public void reconnectionSuccessful() {
					}

					@Override
					public void reconnectionFailed(Exception e) {
					}
				};
//				mXMPPConnection.addConnectionListener(mConnectionListener);

				mXMPPConnection.connect();

				performLogin(mXMPPConfig.username, mXMPPConfig.password);

			} catch (Exception e) {
				e.printStackTrace();
				return (false);
			}

			chatManager = mXMPPConnection.getChatManager();

			return (true);
		}

		return (false);
	}
	
	private void disconnected(Exception reason) {
		changedState(XMPPStatus.DISCONNECTED);
		serverConnectionResponseListener.connectionDown();
	}
	
	/**
	 * Atentica o usuário no servidor.
	 * Se bem sucedida é registrado um ouvinte de mensagem, para gerenciamento das mensagens
	 * recebidas
	 *
	 * @param username Nome de usuário
	 * @param password Senha de usuário
	 * @throws XMPPException Erro na conexão
	 */
	public void performLogin(String username, String password) throws XMPPException {
		if ((mXMPPConnection != null) && (mXMPPConnection.isConnected()))
			mXMPPConnection.login(username, password);

		registerMessageListener();
	}

	/** Executa o logout do usuário no servidor */
	public void performLogout() {
		if ((mXMPPConnection != null) && (mXMPPConnection.isConnected()))
			mXMPPConnection.disconnect();
	}

	/** Seta a configuração para conexão para uso de conexão segura TLS */
	private void setTLSConfig() {
		System.out.println("Tentando fazer conexão segura");
		mXMPPConnConfig.setSecurityMode(SecurityMode.disabled);
		mXMPPConnConfig.setTruststoreType("BKS");
	}

	/**
	 * Definir seu status on-line, também conhecido como presença.
	 *
	 * @param available Tipo do estado
	 * @param status    Mensagem de status
	 */
	public void setStatus(boolean available, String status) {
		Type type = (available) ? (Type.available) : (Type.unavailable);

		Presence presence = new Presence(type);
		presence.setStatus(status);

		mXMPPConnection.sendPacket(presence);
	}

	/**
	 * Recuperar roster do usuário e verificar o estado dos contatos existentes.
	 *
	 * @throws Exception
	 */
	public void printRoster() throws Exception {
		Roster roster = mXMPPConnection.getRoster();

		Collection<RosterEntry> entries = roster.getEntries();
		for (RosterEntry entry : entries)
			System.out.println(String.format("Buddy:%1$s - Status:%2$s", entry.getName(), entry.getStatus()));
	}

	/**
	 * Envia mensagem para o destinat&aacute;rio
	 *
	 * @param message  Mensagem a ser enviada
	 * @param buddyJID Identificador do destinat&aacute;rio
	 * @throws XMPPException
	 */
	public void sendMessage(String message, String buddyJID) throws XMPPException {
		System.out.println(String.format("Sending mesage '%1$s' to user %2$s", message, buddyJID));

		Message newMsg = new Message(buddyJID, Message.Type.chat);
		newMsg.setBody(message);
//		newMsg.addExtension(new DeliveryReceiptRequest());

		if (isAuthenticated()) {
			mXMPPConnection.sendPacket(newMsg);
		} else {
			if (!mXMPPConnection.isConnected()) {
				mXMPPConnection.connect();
				performLogin(mXMPPConfig.username, mXMPPConfig.password);
			}
		}

		//		connection.sendPacket(msg);
	/*	if (msg.getExtension("request", DeliveryReceipt.NAMESPACE) != null) {
			Message ack = new Message(msg.getFrom(), Message.Type.normal);
			ack.addExtension(new DeliveryReceipt(msg.getPacketID()));
			connection.sendPacket(ack);
		}*/
	}

	/**
	 * Um novo amigo pode ser adicionado à sua lista, utilizando a classe Roster. A lista do usuário
	 * é uma coleção de usuários que uma pessoa recebe atualizações de presença para. Um usuário
	 * pode ser associado a grupos, mas no seguinte exemplo a nova entrada lista não pertencem a um
	 * grupo
	 *
	 * @param buddyJID Username
	 * @param nameID   Nickname
	 * @throws Exception
	 */
	public void addFriend(String buddyJID, String nameID) throws Exception {
		System.out.println(String.format("Creating entry for buddy '%1$s' with name %2$s", buddyJID,
				  nameID));
		Roster roster = mXMPPConnection.getRoster();
		roster.createEntry(buddyJID, nameID, null);
	}

	/**
	 * Envia pacote pela rede
	 *
	 * @param toJID Identificador do destinatário
	 * @param id    Identificador de recebimento
	 */
	public void sendPacket(String toJID, String id) {
		final Message ack = new Message(toJID, Message.Type.normal);
//		ack.addExtension(new DeliveryReceipt(id));
		mXMPPConnection.sendPacket(ack);
	}

	public void sendReceipt(String toJID, String id) {
		final Message ack = new Message(toJID, Message.Type.normal);
//		ack.addExtension(new DeliveryReceipt(id));
		mXMPPConnection.sendPacket(ack);
	}
	
	private void registerMessageListener() {
		if (mPacketListener != null)
			mXMPPConnection.removePacketListener(mPacketListener);

		PacketTypeFilter filter = new PacketTypeFilter(Message.class);

		mPacketListener = new PacketListener() {
			@SuppressWarnings("unused")
			@Override
			public void processPacket(Packet packet) {
				if (packet instanceof Message) {
					Message msg = (Message) packet;
					String chatMesssage = msg.getBody();

					// hook off carbonated delivery receipts
					// DeliveryReceipt dr = (DeliveryReceipt)
					// msg.getExtension(DeliveryReceipt.ELEMENT,
					// DeliveryReceipt.NAMESPACE);
					// if (dr != null)
					// Log.d(TAG, "got delivery receipt for " + dr.getId() + " |
					// msg: [" + msg .getSubject() + "]");

					if (chatManager == null)
						return;

					if (msg.getType() == Message.Type.error)
						chatMesssage = "<Error> " + chatMesssage;

					// extract timestamp
					long ts;
					DelayInfo timestamp = null;
					try {
						timestamp = (DelayInfo) msg.getExtension("delay", "urn:xmpp:delay");

					} catch (ClassCastException e) {
						e.printStackTrace();
					}
					if (timestamp == null)
						timestamp = (DelayInfo) msg.getExtension("x", "jabber:x:delay");
					if (timestamp != null)
						ts = timestamp.getStamp().getTime();
					else
						ts = System.currentTimeMillis();

					String fromJID = getJabberID(msg.getFrom());
					String toJID = getJabberID(msg.getTo());

					// if (msg.getExtension(DeliveryReceipt.ELEMENT,
					// DeliveryReceipt.NAMESPACE) != null) {
					// // got XEP-0184 request, send receipt
					// sendReceipt(fromJID, msg.getPacketID());
					// }
				}
			}
		};

		mXMPPConnection.addPacketListener(mPacketListener, filter);
	}
	
	/**
	 * Verifica se o usuário está atenticado, ou seja, logado com o seu usuário
	 *
	 * @return True ou False
	 */
	public boolean isAuthenticated() {
		if (mXMPPConnection != null)
			return (mXMPPConnection.isConnected() && mXMPPConnection.isAuthenticated());

		return (false);
	}

	/**
	 * Resgata o identificador Jabber do servidor
	 *
	 * @param address Endere&ccedil;o de usu&aacute;rio
	 * @return ID correspondente
	 */
	private String getJabberID(String address) {
		String[] res = address.split("/");
		return (res[0].toLowerCase());
	}
	
	public void addPacketListener(PacketListener xmppClient, PacketFilter filter) {
		mXMPPConnection.addPacketListener(xmppClient, filter);
	}
	
	public String getUser() {
		return (mXMPPConnection.getUser());
	}

	// BLOCKING, call on a new Thread!
	private void updateConnectingThread(Thread new_thread) {
		synchronized (mConnectingThreadMutex) {
			if (mConnectingThread == null) {
				mConnectingThread = new_thread;
			} else try {
				mConnectingThread.interrupt();
				mConnectingThread.join(50);

			} catch (InterruptedException e) {
			} finally {
				mConnectingThread = new_thread;
			}
		}
	}

	/** Liberar a thread */
	private void finishConnectingThread() {
		synchronized (mConnectingThreadMutex) {
			mConnectingThread = null;
		}
	}

	/**
	 * Para adicionar um ouvinte de resposta do servidor.<br>
	 * Quando este terminar o processo de conexão, é disparado um evento de resposta se a conexão
	 * foi ou não bem sucedida.
	 *
	 * @param listener O listener implementado
	 */
	public void addServerConnectedListener(ServerConnectionResponseListener listener) {
		this.serverConnectionResponseListener = listener;
	}
	
//	public void addDeliveryReceiptCallback(DeliveryReceiptCallback deliveryReceiptCallback) {
//		this.deliveryCallback = deliveryReceiptCallback;
//	}
	
	private boolean networkConnected() throws IOException {
		// NetworkInfo info = getNetworkInfo();
		InetAddress address = Inet4Address.getLocalHost();

		// return info != null && info.isConnected();
		return (address.isReachable(50));
	}

	private class CountDownTask extends SwingWorker<Object, Integer> {
		@Override
		protected void done() {
			super.done();

			requestChangeState(XMPPStatus.ONLINE);
		}

		@Override
		protected void process(List<Integer> arg0) {
			super.process(arg0);
		}

		@Override
		protected Object doInBackground() throws Exception {
			for (int i = mReconnectTimeout; i >= 0; i--) {
				// Log.d(TAG, "doInBackground: count" + i);
				try {
					Thread.sleep(1000);
					publish(i);

				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			return (null);
		}
	}
}