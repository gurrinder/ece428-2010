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
	int retransmitTimeout = 10000; // 10 seconds
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
    	int retry = 20; // we will retry setting up connection 20 times
    	
    	// start the receiving and sending tasks
    	recvTask.setRunnable(true);
    	sendTask.setRunnable(true);
    	
    	new Thread(recvTask).run();
    	new Thread(sendTask).run();
    	
    	while(retry > 0 && connState.GetState() != ConnectionState.ESTABLISHED)
    	{
        	retry--;

        	// clear the buffers
        	recvList.clear();
        	sendList.clear();
    		
	    	activeConn = new ConnectTask(connHelper, callback, localAddr, serverAddr);
	    	activeConn.setRunnable(true);
	    	new Thread(activeConn).run();
	    	
	    	// wait for the connection to succeed or fail
	    	while(activeConn.isRunning());
    	}
    	
    	if(connState.GetState() != ConnectionState.ESTABLISHED)
    	{
    		throw new SocketException();
    	}
    	else
    	{
    		discardTask.setRunnable(true);
    		new Thread(discardTask).run();
    	}
    }

    /* Used by server to accept a new connection */
    /* Returns the IP & port of the client */
    public InetSocketAddress S_accept() throws IOException, SocketTimeoutException
    {        
    	// start the receiving and sending tasks
    	recvTask.setRunnable(true);
    	sendTask.setRunnable(true);
    	
    	new Thread(recvTask).run();
    	new Thread(sendTask).run();
    	
    	if(recvTimeout > 0)
    	{
    		callback.WaitForPacketRecvTimeout();
    	}
    	else
    	{
    		while(connState.GetState() != ConnectionState.ESTABLISHED);
    	}
    	
    	if(remoteAddr == null)
    	{
			throw new SocketTimeoutException();    		
    	}
    	else
    	{
    		discardTask.setRunnable(true);
    		new Thread(discardTask).run();
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
			    		threadWaitingOnRecv.wait();
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
    	
    	synchronized void OnTCPHeaderRecieved(TCPHeader header)
    	{
    		if(threadWaitingOnRecv != null)
    		{
    			threadWaitingOnRecv.notify();
    		}
    		
    		// a new connection has arrived
			if(header.syn == 1 && header.ack == 0 && header.fin == 0)
    		{
    			// remove this from the list
    			header = GetReceivedHeaderOfType(new TCPHeaderType(TCPHeaderType.SYN, header.ackNum.toInt()));
    			
    			assert(header != null);
    			
    			if(activeConn != null)
    			{
    				activeConn.setRunnable(false);
    				while(activeConn.isRunning());
    			}
    			
    			activeConn = new AcceptTask(connHelper, callback, localAddr, header);
    			activeConn.setRunnable(true);
    			new Thread(activeConn).run();
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
	    			type += hdr.ack == 1 ? (type | TCPHeaderType.ACK) : 0;
	    			type += hdr.syn == 1 ? (type | TCPHeaderType.SYN) : 0;
	    			type += hdr.fin == 1 ? (type | TCPHeaderType.FIN) : 0;
	    			
	    			if((type == hdrType.type) && (hdr.ackNum.toInt() == hdrType.ackNum))
	    			{
	    				recvList.remove(i);
	    				return hdr;
	    			}
	    		}
    		}
    		return null;
    	}
    }
}

