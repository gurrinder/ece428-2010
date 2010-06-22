import java.io.IOException;
import java.net.*;
import java.util.*;
import ece428.socket.T_DatagramSocket;

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
	boolean firstRecv = true;
	boolean firstSend = true;
	boolean isClosing = false;
	int sendSeqNum = 0;
	
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
//		socket.T_setSoTimeout(timeout);
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
    	
    	while(retry > 0 && connState.GetState() != ConnectionState.ESTABLISHED && !isClosing)
    	{
        	retry--;
        	callback.SimpleSleep(2000);
        	//System.out.println("client connecting..");
	    	activeConn = new ConnectTask(connHelper, callback, localAddr, serverAddr);
	    	activeConn.setRunnable(true);
	    	activeConn.setThread(new Thread(activeConn));
	    	activeConn.getThread().start();
	    	
	    	// wait for the connection to succeed or fail
	    	try
			{
				activeConn.getThread().join();
			} 
	    	catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	
    	//System.out.println("outside the trying");
    	
    	synchronized(this)
    	{
    	if(connState.GetState() != ConnectionState.ESTABLISHED && !isClosing)
    	{
    		//System.out.println("we failed on connect");
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
    		//System.out.println("we succeeeded on connect");
    		
    		synchronized(discardTask)
    		{
    			if(connState.GetState() == ConnectionState.ESTABLISHED)
    			{
    	    		//System.out.println("starting the discard thread");
		    		discardTask.setRunnable(true);
		    		discardTask.setThread(new Thread(discardTask));
		    		discardTask.getThread().start();
		    		//System.out.println("discard thread started");
    			}	    		
    		}
    	}

    	//System.out.println("Client connected after : " + (100-retry) + " try(s)");
    	}
    }

    /* Used by server to accept a new connection */
    /* Returns the IP & port of the client */
    public InetSocketAddress S_accept() throws IOException, SocketTimeoutException
    {
    	socket.T_setSoTimeout(retransmitTimeout);
    	
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
    		while(connState.GetState() != ConnectionState.ESTABLISHED && !isClosing)
    		{
    			callback.SimpleSleep(100);
    		}
    	}
    	
    	synchronized(this)
    	{
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
    		synchronized(discardTask)
    		{
    			if(connState.GetState() == ConnectionState.ESTABLISHED)
    			{
		    		discardTask.setRunnable(true);
		    		discardTask.setThread(new Thread(discardTask));
		    		discardTask.getThread().start();
    			}
    		}
    	}
    }
    	return remoteAddr;
    }

    /* Used to send data. len can be arbitrarily large or small */
    public void S_send(byte[] buf, int len) throws Exception
    {
		if(firstSend)
		{
			Thread.sleep(callback.GetRetransmitTimeout());
			Thread.sleep(callback.GetRetransmitTimeout());
			Thread.sleep(callback.GetRetransmitTimeout());
			firstSend = false;
		}
		
		ArrayList<byte[]> data = new ArrayList<byte[]>();
		while (buf.length > 0)
		{
			data.add(getSubArray(buf, 127));
			buf = getRestArray(buf, 127);
		}
		for (int i = 0; i < data.size(); i++)
		{
			TCPHeader packetAckHdr = null;
			TCPHeader packetHdr = TCPHeaderUtil.createTCPHeader(
					this.localAddr.getPort(), 
					this.remoteAddr.getPort(), 
					sendSeqNum, 
					0, 
					false, 
					false, 
					false, 
					0, 
					data.get(i));
			
			packetHdr.senderAddr = this.remoteAddr;
			sendSeqNum = (int) ((sendSeqNum + 528)%Math.pow(2, 31));
			// send a syn
			while(packetAckHdr == null)
			{
				//System.out.println("Sending data : seqNum = " + sendSeqNum + " length = " + data.get(i).length);
				callback.PerformTCPSend(packetHdr);
				callback.SimpleSleep(10);
				packetAckHdr = callback.GetReceivedHeaderOfType(new TCPHeaderType(TCPHeaderType.ACK, (long) ((sendSeqNum+1)%Math.pow(2, 31))));
				//System.out.println("Sending data");
			}
		}
    }

    private byte[] getRestArray(byte[] buf, int n)
	{
    	int len = 0;
    	if (buf.length > n)
    		len = buf.length-n;
    	byte[] ret = new byte[len];
    	for (int i = 0; i < len; i++)
    		ret[i] = buf[n+i];
		return ret;
	}

	private byte[] getSubArray(byte[] buf, int n)
	{
		// TODO Auto-generated method stub
    	if (buf.length < n)
    		n = buf.length;
    	byte[] ret = new byte[n];
    	for (int i = 0; i < n; i++)
    		ret[i] = buf[i];
		return ret;
	}

	/* Used to receive data. Max chunk of data received is len. 
     * The actual number of bytes received is returned */
    public int S_receive(byte[] buf, int len) throws Exception
    {		
		if(firstRecv)
		{
			Thread.sleep(callback.GetRetransmitTimeout());
			Thread.sleep(callback.GetRetransmitTimeout());
			Thread.sleep(callback.GetRetransmitTimeout());
			firstRecv = false;
		}
		
    	return 0;
	/* Your code here */
    }

    /* To close the connection */
    public void S_close() throws SocketException
    {
    	if(connState.GetState() != ConnectionState.ESTABLISHED)
    	{
    		return;
    	}
    	byte[] data = new byte[0];
    	TCPHeader finackHdr = null;
		TCPHeader finHdr = TCPHeaderUtil.createTCPHeader(
				localAddr.getPort(), 
				remoteAddr.getPort(), 
				0, 
				0, 
				false, 
				true, 
				false, 
				callback.GetDestWindowSize(), 
				data);
		
		finHdr.senderAddr = remoteAddr;
		
		while(finackHdr == null && connState.GetState() != ConnectionState.CLOSED)
		{
			callback.PerformTCPSend(finHdr);
			callback.SimpleSleep(100);
			finackHdr = callback.GetReceivedHeaderOfType(new TCPHeaderType(TCPHeaderType.FIN + TCPHeaderType.ACK, 0));
		}
		
		internalClose(true);
    }
    
    void internalClose(boolean val) throws SocketException
    {
    	synchronized(this)
    	{
    	isClosing = true;
    	
    	// clear the buffers
		synchronized(recvList)
		{
        	recvList.clear();
		}
		synchronized(sendList)
		{
			sendList.clear();
		}
		
		recvTask.setRunnable(false);
		sendTask.setRunnable(false);
		discardTask.setRunnable(false);
		
		// wait for both tasks to stop
		try
		{
			if(val && recvTask.getThread() != null)
			{
				recvTask.getThread().join();
			}
			if(sendTask.getThread() != null)
			{
				sendTask.getThread().join();
			}
			
			synchronized(discardTask)
    		{
				val = discardTask.getThread() != null;
    		}
			if(val)
			{
				discardTask.getThread().join();
			}
		} catch (InterruptedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
    	// initialize buffers
    	recvList = Collections.synchronizedList(new ArrayList<TCPHeader>());
    	sendList = Collections.synchronizedList(new ArrayList<TCPHeader>());
    	// clear the buffers
    	recvList.clear();
    	sendList.clear();
    	
//    	socket = new T_DatagramSocket(localAddr);
    	connState = new ConnectionState(ConnectionState.CLOSED);
    	connHelper = new ConnectionHelper(socket);
    	callback = new TaskCallback();

    	recvTask = new ReceiveTask(connHelper, callback, recvList);
    	sendTask = new SendTask(connHelper, callback, sendList);
    	discardTask = new DiscardPacketTask(connHelper, callback, recvList);
    	
    	////System.out.println("successfully internal closed");
    	}
    }
    
    class TaskCallback
    {
    	int lastSeqNum = -1;
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
    		
    		//we got a request to close
    		if(header.syn == 0 && header.ack == 0 && header.fin == 1)
    		{
    			// we send fin+ack
    			header.ack = 1;
    			header.checksum = Word.createFromInt(TCPHeaderUtil.calculateCheckSum(header));
    			byte[] bytes = header.toBytes();

    			for(int i = 0; i < 300; i++)
    			{
	    			try 
	    			{
						connHelper.send(header.toBytes(), TCPHeader.AGGREGATED_HEADER_SIZE, header.senderAddr);
					} 
	    			catch (IOException e) 
	    			{
						e.printStackTrace();
					}
    			}
    			try {
					internalClose(false);
				} catch (SocketException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		}
    		// got ack
    		else if (header.syn == 0 && header.ack == 1 && header.fin == 0)
    		{
    			////System.out.println("Got Ack seqNum = " + header.seqNum.toInt() + "ack = " + header.ackNum.toInt() + " length = " + header.dataSize);
    		}
    		// got data
    		else if (header.syn == 0 && header.ack == 0 && header.fin == 0)
        	{
    			////System.out.println("Got Data seqNum = " + header.seqNum.toInt() + "ack = " + header.ackNum.toInt() + " length = " + header.dataSize + " dat = " + header.data[0]);
    		    TCPHeader packetHdr = TCPHeaderUtil.createTCPHeader(
    					header.destPort.toInt(), 
    					header.sourcePort.toInt(), 
    					header.seqNum.toInt(), 
    					(int) ((header.seqNum.toInt() + 528 + 1)%(Math.pow(2, 31))), 
    					false, 
    					false, 
    					true, 
    					0, 
    					new byte[1]);
    			packetHdr.senderAddr = header.senderAddr;
    			if (this.lastSeqNum == -1 || this.lastSeqNum != header.seqNum.toInt())
    				this.lastSeqNum = header.seqNum.toInt();
    			else
    			{
    				////System.out.println("                     delete header");
    				RemoveHeader(header);
    			}
    			PerformTCPSend(packetHdr);
    		}
    		// a new connection has arrived
    		else if(header.syn == 1 && header.ack == 0 && header.fin == 0)
    		{
    			// remove the header type from the list
    			GetReceivedHeaderOfType(new TCPHeaderType(TCPHeaderType.SYN, header.ackNum.toInt()));
    			    			
    			// if currently connected, tell the client it is connected
    			if(activeConn != null && activeConn.isConnected 
    					&& header.senderAddr.getHostName().equals(activeConn.destAddress.getHostName())
    					&& header.senderAddr.getPort() == activeConn.destAddress.getPort())
    			{
    				////System.out.println("letting client know i am already connected to him");
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
    	
    	void AddHeaderToRecvListAtHead(TCPHeader hdr)
    	{
    		synchronized(recvList)
    		{
    			recvList.add(0, hdr);
    		}
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
    		////System.out.println("Connection Succeeded");
    		connState.SetState(ConnectionState.ESTABLISHED);
    		
    		remoteAddr = conn.destAddress;
    		conn.setRunnable(false);
    		////System.out.println("Exiting onConnSucceeded");
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
    	void RemoveHeader(TCPHeader hdr)
    	{
    		
    		synchronized(recvList)
    		{
	    		for(int i = 0; i < recvList.size(); i++)
	    		{
	    			if (recvList.get(i).equals(hdr))
	    			{
	    				recvList.remove(i);
	    				break;
	    			}
	    		}
    		}
    	}
    }
}

