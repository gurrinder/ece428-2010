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
    	
    	recvTask.setThread(new Thread(recvTask));
    	recvTask.getThread().start();
    	sendTask.setThread(new Thread(sendTask));
    	sendTask.getThread().start();
    	
    	while(retry > 0 && connState.GetState() != ConnectionState.ESTABLISHED)
    	{
        	retry--;
        	System.out.println("client connecting..");
	    	activeConn = new ConnectTask(connHelper, callback, localAddr, serverAddr);
	    	activeConn.setRunnable(true);
	    	activeConn.setThread(new Thread(activeConn));
	    	activeConn.getThread().start();
	    	
	    	// wait for the connection to succeed or fail
	    	try
			{
				activeConn.getThread().join();
			} catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	
    	System.out.println("outside the trying");
    	
    	if(connState.GetState() != ConnectionState.ESTABLISHED)
    	{
    		System.out.println("we failed on connect");
    		recvTask.setRunnable(false);
    		sendTask.setRunnable(false);

    		// wait for both tasks to stop
    		try
			{
    			recvTask.getThread().join();
				sendTask.getThread().join();
			} catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		
    		throw new SocketException();
    	}
    	else
    	{
    		System.out.println("we succeeeded on connect");
    		
    		System.out.println("starting the discard thread");
    		discardTask.setRunnable(true);
    		discardTask.setThread(new Thread(discardTask));
    		discardTask.getThread().start();
    		
    		System.out.println("discard thread started");
    	}

    	System.out.println("Client connected after : " + (100-retry) + " try(s)");
    }

    /* Used by server to accept a new connection */
    /* Returns the IP & port of the client */
    public InetSocketAddress S_accept() throws IOException, SocketTimeoutException
    {    	
    	// start the receiving and sending tasks
    	recvTask.setRunnable(true);
    	sendTask.setRunnable(true);
    	
    	recvTask.setThread(new Thread(recvTask));
    	recvTask.getThread().start();
    	sendTask.setThread(new Thread(sendTask));
    	sendTask.getThread().start();
    	
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
			try
			{
				recvTask.getThread().join();
				sendTask.getThread().join();
			} catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		
			throw new SocketException();    		
    	}
    	else
    	{
    		discardTask.setRunnable(true);
    		discardTask.setThread(new Thread(discardTask));
    		discardTask.getThread().start();
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
    public int S_receive(byte[] buf, int len) throws SocketException
    {
		socket.T_setSoTimeout(recvTimeout);
		
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
    			// remove the header type from the list
    			GetReceivedHeaderOfType(new TCPHeaderType(TCPHeaderType.SYN, header.ackNum.toInt()));
    			    			
    			// if currently connected, tell the client it is connected
    			if(activeConn != null && activeConn.isConnected 
    					&& header.senderAddr.getHostName().equals(activeConn.destAddress.getHostName())
    					&& header.senderAddr.getPort() == activeConn.destAddress.getPort())
    			{
    				System.out.println("letting client know i am already connected to him");
    				header.ack = 1;
    				header.ackNum = DWord.createFromInt(header.seqNum.toInt() + 1);
    				header.seqNum = DWord.createFromInt(-1);
    				header.checksum = Word.createFromInt(TCPHeaderUtil.calculateCheckSum(header));
    				PerformTCPSend(header);
    			}
    			else if(activeConn != null && ((AcceptTask)activeConn).synHdr.checksum.toInt() != header.checksum.toInt())
    			{
    				activeConn.setRunnable(false);
    				try
					{
						activeConn.getThread().join();
					} catch (InterruptedException e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    				activeConn = null;
    			}
    			
    			// create a new connection
    			if(activeConn == null)
    			{
					activeConn = new AcceptTask(connHelper, callback, localAddr, header);
	    			activeConn.setRunnable(true);
	    			activeConn.setThread(new Thread(activeConn));
	    			activeConn.getThread().start();
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
    		System.out.println("Connection Succeeded");
    		connState.SetState(ConnectionState.ESTABLISHED);
    		
    		remoteAddr = conn.destAddress;
    		conn.setRunnable(false);
    		System.out.println("Exiting onConnSucceeded");
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
	    				return hdr;
	    			}
	    		}
    		}
    		return null;
    	}
    }
}

