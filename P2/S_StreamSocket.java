import java.io.IOException;
import java.net.*;
import java.util.*;
import ece428.socket.*;

class S_StreamSocket
{
    /* Data members */
	List<TCPHeader> recvList = null;
	List<TCPHeader> sendList = null;
	T_DatagramSocket socket = null;
	int srcWindowSize = 1000;
	int destWindowSize = 1000;
	int recvTimeout = 0;
	int retransmitTimeout = 1000; // 10 seconds
	ConnectionHelper connHelper = null;
	ConnectionState connState = null;
	Connection activeConn = null;
	TaskCallback callback = null;
	ReceiveTask recvTask = null;
	SendTask sendTask = null;
	DiscardPacketTask discardTask = null;
	Thread threadWaitingOnRecv = null;
	InetSocketAddress localAddr = null;
	InetSocketAddress remoteAddr = null;
	
    /* Constructor. Binds socket to addr */
    public S_StreamSocket(InetSocketAddress addr) throws SocketException
    {
    	if (addr == null)
    	{
    		addr = new InetSocketAddress("localhost", 0);
    	}
    	localAddr = addr;
    	
    	// initialize buffers
    	recvList = Collections.synchronizedList(new ArrayList<TCPHeader>());
    	sendList = Collections.synchronizedList(new ArrayList<TCPHeader>());
    	// clear the buffers
    	recvList.clear();
    	sendList.clear();
    	
    	socket = new T_DatagramSocket(addr);
    	connState = new ConnectionState(ConnectionState.CLOSED);
    	connHelper = new ConnectionHelper(socket);
    	callback = new TaskCallback();

    	recvTask = new ReceiveTask(connHelper, callback, recvList);
    	sendTask = new SendTask(connHelper, callback, sendList);
    	discardTask = new DiscardPacketTask(connHelper, callback, recvList);
    }

    /* Receive timeout in milliseconds */
    public void S_setSoTimeout(int timeout) throws SocketException
    {
    	recvTimeout = timeout;
    	socket.T_setSoTimeout(timeout);
    }

    /* Details of local socket (IP & port) */
    public InetSocketAddress S_getLocalSocketAddress()
    {
    	return socket.T_getLocalSocketAddress();
    }

    /* Used by client to connect to server */
    public void S_connect(InetSocketAddress serverAddr) throws SocketException
    {
    	int retry = 100; // we will retry setting up connection 100 times
    	
    	// for the handshake purpose, we set the timeout to be different
    	socket.T_setSoTimeout(retransmitTimeout);
    	
    	// start the receiving and sending tasks
    	recvTask.setRunnable(true);
    	sendTask.setRunnable(true);
    	
    	new Thread(recvTask).start();
    	new Thread(sendTask).start();
    	
    	while(retry > 0 && connState.GetState() != ConnectionState.ESTABLISHED)
    	{
        	retry--;
        	System.out.println("client connecting..");
	    	activeConn = new ConnectTask(connHelper, callback, localAddr, serverAddr);
	    	activeConn.setRunnable(true);
	    	new Thread(activeConn).start();
	    	
	    	// wait for the connection to succeed or fail
	    	while(activeConn.isRunning())
	    	{
	    		callback.SimpleSleep(100);
	    	}
    	}
    	
    	if(connState.GetState() != ConnectionState.ESTABLISHED)
    	{
    		recvTask.setRunnable(false);
    		sendTask.setRunnable(false);

    		// wait for both tasks to stop
    		while(recvTask.isRunning() || sendTask.isRunning())
    		{
    			callback.SimpleSleep(100);
    		}
    		
    		throw new SocketException();
    	}
    	else
    	{
    		// reset the timeout
    		socket.T_setSoTimeout(recvTimeout);
    		
    		discardTask.setRunnable(true);
    		new Thread(discardTask).start();
    	}
    	
    	System.out.println("Client connected after : " + (100-retry) + " retries");
    }

    /* Used by server to accept a new connection */
    /* Returns the IP & port of the client */
    public InetSocketAddress S_accept() throws IOException, SocketTimeoutException
    {    	
    	// start the receiving and sending tasks
    	recvTask.setRunnable(true);
    	sendTask.setRunnable(true);
    	
    	new Thread(recvTask).start();
    	new Thread(sendTask).start();
    	
    	if(recvTimeout > 0)
    	{
    		callback.WaitForPacketRecvTimeout();
    	}
    	else
    	{
    		while(connState.GetState() != ConnectionState.ESTABLISHED)
    		{
    			callback.SimpleSleep(100);
    		}
    	}
    	
    	if(remoteAddr == null)
    	{
    		recvTask.setRunnable(false);
    		sendTask.setRunnable(false);

    		// wait for both tasks to stop
    		while(recvTask.isRunning() || sendTask.isRunning())
    		{
    			callback.SimpleSleep(100);
    		}
    		
			throw new SocketTimeoutException();    		
    	}
    	else
    	{
    		discardTask.setRunnable(true);
    		new Thread(discardTask).start();
    	}
    	
    	return remoteAddr;
    }

    /* Used to send data. len can be arbitrarily large or small */
    public void S_send(byte[] buf, int len) /* throws ... */
    {
	/* Your code here */
    }

    /* Used to receive data. Max chunk of data received is len. 
     * The actual number of bytes received is returned */
    public int S_receive(byte[] buf, int len) /* throws ... */
    {
    	return 0;
	/* Your code here */
    }

    /* To close the connection */
    public void S_close() /* throws ... */
    {
	/* Your code here */
    }
    
    class TaskCallback
    {
    	void WaitForRetransmitTimeout()
    	{
    		try 
    		{
    			Thread.sleep(retransmitTimeout);
    		}
    		catch (InterruptedException e) 
			{
				e.printStackTrace();
			}
    	}
    	
    	synchronized void WaitForPacketRecvTimeout()
    	{
    		boolean wait = false;
    		try 
    		{
	    		if(recvTimeout <= 0)
	    		{
	    			synchronized(recvList)
	    			{
	    				wait = recvList.isEmpty();
	    			}
	    			
	    			if(wait)
	    			{
	    				threadWaitingOnRecv = Thread.currentThread();
			    		threadWaitingOnRecv.suspend();
			    		threadWaitingOnRecv = null;
	    			}	    			
	    		}
	    		else
	    		{
					Thread.sleep(recvTimeout);
	    		}
    		}
    		catch (InterruptedException e) 
			{
				e.printStackTrace();
			}
    	}
    	
    	void SimpleSleep(int millis)
    	{
    		try
    		{
    			Thread.sleep(millis);
    		}
    		catch (InterruptedException e) 
			{
				
			}
    	}
    	synchronized void OnTCPHeaderRecieved(TCPHeader header)
    	{
    		if(threadWaitingOnRecv != null)
    		{
    			threadWaitingOnRecv.resume();
    		}
    		
    		// a new connection has arrived
			if(header.syn == 1 && header.ack == 0 && header.fin == 0)
    		{
    			// remove this from the list
    			header = GetReceivedHeaderOfType(new TCPHeaderType(TCPHeaderType.SYN, header.ackNum.toInt()));
    			
    			assert(header != null);
    			
    			if(activeConn != null && ((AcceptTask)activeConn).synHdr.checksum.toInt() != header.checksum.toInt())
    			{
    				activeConn.setRunnable(false);
    				while(activeConn.isRunning())
    				{
    					SimpleSleep(100);
    				}
    				
    				activeConn = null;
    			}
    			// create a new connection
    			if(activeConn == null)
    			{
					activeConn = new AcceptTask(connHelper, callback, localAddr, header);
	    			activeConn.setRunnable(true);
	    			new Thread(activeConn).start();
    			}
    		}
    	}
    	
    	void OnTCPHeaderSent(TCPHeader header)
    	{
    		
    	}
    	
    	void OnConnectionFailed(Connection conn)
    	{
    		connState.SetState(ConnectionState.CLOSED);
    		conn.setRunnable(false);
    		
    		remoteAddr = null;
    		
        	// clear the buffers
    		synchronized(recvList)
    		{
	        	recvList.clear();
    		}
    		synchronized(sendList)
    		{
    			sendList.clear();
    		}
    	}

    	void OnConnectionSucceeded(Connection conn) 
		{
    		connState.SetState(ConnectionState.ESTABLISHED);
    		
    		remoteAddr = conn.destAddress;
    		conn.setRunnable(false);
		}

    	void PerformTCPSend(TCPHeader hdr)
    	{
    		synchronized(sendList)
    		{
    			sendList.add(hdr);
    		}
    	}
    	
    	int GetRetransmitTimeout()
    	{
    		return retransmitTimeout;
    	}
    	
    	int GetSourceWindowSize()
    	{
    		return 0;
    	}
    	
    	int GetDestWindowSize()
    	{
    		return 0;
    	}
    	
    	TCPHeader GetReceivedHeaderOfType(TCPHeaderType hdrType)
    	{
    		TCPHeader hdr = null;
    		int type = TCPHeaderType.DEFAULT;
    		
    		synchronized(recvList)
    		{
	    		for(int i = 0; i < recvList.size(); i++)
	    		{
	    			hdr = recvList.get(i);
	    			type += hdr.ack == 1 ? TCPHeaderType.ACK : 0;
	    			type += hdr.syn == 1 ? TCPHeaderType.SYN : 0;
	    			type += hdr.fin == 1 ? TCPHeaderType.FIN : 0;
	    			
	    			if((type == hdrType.type) && (hdr.ackNum.toInt() == hdrType.ackNum))
	    			{
	    				recvList.remove(i);
	    				if(hdr == null)
	    				{
	    					System.out.println("NUll header>>>");
	    				}
	    				return hdr;
	    			}
	    		}
    		}
    		return null;
    	}
    }
}

