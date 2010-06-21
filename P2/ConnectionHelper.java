import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

import ece428.socket.T_DatagramSocket;

abstract class ConnectionHelperTask implements Runnable
{
	private boolean bRun = true;
	protected ConnectionHelper connHelper = null;
	protected S_StreamSocket.TaskCallback callback = null;
	private boolean running = false;
	
	public abstract void performTask();
	
	public ConnectionHelperTask (ConnectionHelper helper, S_StreamSocket.TaskCallback callback)
	{
		this.connHelper = helper;
		this.callback = callback;
	}
	
    public void setRunnable (boolean runnable)
	{
		bRun = runnable;
	}
    
    public boolean isRunning()
    {
    	return bRun || running;
    }
    
    @Override
	public void run()
	{
		while(bRun)
		{
			running = true;
			performTask();
		}
		running = false;
	}
}

class DiscardPacketTask extends ConnectionHelperTask
{
	private List<TCPHeader> receiveList = null;

	public DiscardPacketTask(ConnectionHelper helper, S_StreamSocket.TaskCallback callback, List<TCPHeader> receiveList) 
	{
		super(helper, callback);
		this.receiveList = receiveList;
	}
	
	public void performTask()
	{
		long curTime = Calendar.getInstance().getTimeInMillis();
		long diff = 0;
		
		try 
		{
			Thread.sleep(callback.GetRetransmitTimeout());
		} 
		catch (InterruptedException e) 
		{
			e.printStackTrace();
		}
		
		synchronized(receiveList)
		{
			for(int i = 0; i < receiveList.size(); i++)
			{
				diff = curTime - receiveList.get(i).recvTime;
				if(diff >= callback.GetRetransmitTimeout())
				{
					receiveList.remove(i);
					i--;
				}
			}
		}
	}
}

// puts the received packets into recvBuffer
class ReceiveTask extends ConnectionHelperTask
{
	private List<TCPHeader> receiveList = null;
	
	public ReceiveTask(ConnectionHelper helper, S_StreamSocket.TaskCallback callback, List<TCPHeader> receiveList) 
	{
		super(helper, callback);
		this.receiveList = receiveList;
	}

	public void performTask()
	{
		TCPHeader recvHeader = null;
		try 
		{
			recvHeader = connHelper.recv(TCPHeader.AGGREGATED_HEADER_SIZE);			
			if(recvHeader.checksum.toInt() != TCPHeaderUtil.calculateCheckSum(recvHeader))
			{
				recvHeader = null;
			}
			else
			{
				// this time field is used to tell which packets to drop since they have been past
				// their receiving time
				recvHeader.recvTime = Calendar.getInstance().getTimeInMillis();
			}
		}
		catch (IOException e)
		{
			//System.err.println("ReceiveTask::performTask - IOException: " + e.getMessage());
		}
		
		if(recvHeader != null)
		{
			synchronized(receiveList)
			{
				receiveList.add(recvHeader);
			}
			callback.OnTCPHeaderRecieved(recvHeader);
		}
	}
}

//sends packets in the buffer to destination
class SendTask extends ConnectionHelperTask
{
	List<TCPHeader> sendList = null;
	public SendTask(ConnectionHelper helper, S_StreamSocket.TaskCallback callback, List<TCPHeader> sendList) 
	{
		super(helper, callback);
		this.sendList = sendList;
	}

	public void performTask()
	{
		TCPHeader sendHeader = null;
		synchronized(sendList)
		{
			if(sendList.size() > 0)
			{
				sendHeader = sendList.remove(0);
			}
		}
		if(sendHeader != null)
		{
			try 
			{
				connHelper.send(sendHeader.toBytes(), TCPHeader.AGGREGATED_HEADER_SIZE, sendHeader.senderAddr);
				callback.OnTCPHeaderSent(sendHeader);
			}
			catch (IOException e) 
			{
				System.err.println("SendTask::performTask - IOException: " + e.getMessage());
				// re-queue the tcp header for transmission in case of error
				synchronized(sendList)
				{
					sendList.add(sendHeader);
				}
			}
		}
	}
}

abstract class Connection extends ConnectionHelperTask
{
	public boolean isConnected = false;
	public InetSocketAddress sourceAddress = null;
	public InetSocketAddress destAddress = null;
	
	public Connection(ConnectionHelper helper, S_StreamSocket.TaskCallback callback) 
	{
		super(helper, callback);
	}	
}

// represents a connection between source and destination
class ConnectTask extends Connection
{	
	public ConnectTask(
			ConnectionHelper helper, 
			S_StreamSocket.TaskCallback callback, 
			InetSocketAddress srcAddr,
			InetSocketAddress destAddr) 
	{
		super(helper, callback);
		sourceAddress = srcAddr;
		destAddress = destAddr;
	}
	
	@Override
	public void performTask() 
	{
		int seqNum = new Random().nextInt(0x0FFFFFFF);
		int retry = 30;
		byte[] data = new byte[0];
		TCPHeader synAckHdr = null;
		TCPHeader ackHdr = null;
		TCPHeader synHdr = TCPHeaderUtil.createTCPHeader(
				sourceAddress.getPort(), 
				destAddress.getPort(), 
				seqNum, 
				0, 
				true, 
				false, 
				false, 
				callback.GetDestWindowSize(), 
				data);
		
		synHdr.senderAddr = destAddress;
		
		// send a syn
		while(retry > 0 && synAckHdr == null)
		{
			retry--;
			
			callback.PerformTCPSend(synHdr);
			callback.SimpleSleep(2000);
			synAckHdr = callback.GetReceivedHeaderOfType(new TCPHeaderType(TCPHeaderType.SYN + TCPHeaderType.ACK, seqNum + 1));
		}
		
		if(synAckHdr == null)
		{
			callback.OnConnectionFailed(this);
			return;
		}
		
		ackHdr = TCPHeaderUtil.createTCPHeader(
				sourceAddress.getPort(), 
				destAddress.getPort(), 
				synAckHdr.ackNum.toInt(), 
				synAckHdr.seqNum.toInt() + 1, 
				false, 
				false, 
				true, 
				callback.GetDestWindowSize(), 
				data);
		ackHdr.senderAddr = destAddress;
		
		retry = 30;
		// we now send the last ack a number of times (hopefully server gets atleast one of them)
		while(retry > 0)
		{
			retry--;
			callback.SimpleSleep(100);
			callback.PerformTCPSend(ackHdr);
		}
		
		callback.SimpleSleep(2000);
		
		// check if server got atleast one last ack
		ackHdr = callback.GetReceivedHeaderOfType(new TCPHeaderType(TCPHeaderType.ACK, synAckHdr.seqNum.toInt() + 1));
		if(ackHdr == null)
		{
			callback.OnConnectionFailed(this);
			return;
		}
		
		this.isConnected = true;		
		callback.OnConnectionSucceeded(this);
	}
}

//represents a connection between source and destination
class AcceptTask extends Connection
{	
	public TCPHeader synHdr = null;
	
	public AcceptTask(
			ConnectionHelper helper, 
			S_StreamSocket.TaskCallback callback, 
			InetSocketAddress srcAddr, 
			TCPHeader synHdr) 
	{
		super(helper, callback);
		this.sourceAddress = srcAddr;
		this.synHdr = synHdr;
		this.destAddress = synHdr.senderAddr;
	}

	@Override
	public void performTask() 
	{
		int retry = 30;
		int seqNum = new Random().nextInt(0x0FFFFFFF);
		byte[] data = new byte[0];
		TCPHeader ackHdr = null;
		
		TCPHeader synAckHdr = TCPHeaderUtil.createTCPHeader(
				sourceAddress.getPort(), 
				destAddress.getPort(), 
				seqNum, 
				synHdr.seqNum.toInt() + 1, 
				true, 
				false, 
				true, 
				callback.GetSourceWindowSize(), 
				data);
		synAckHdr.senderAddr = destAddress;
		
		while(retry > 0 && ackHdr == null)
		{
			retry--;
			callback.PerformTCPSend(synAckHdr);
			callback.SimpleSleep(1000);
			ackHdr = callback.GetReceivedHeaderOfType(new TCPHeaderType(TCPHeaderType.ACK, seqNum + 1));
		}
		
		if(ackHdr == null)
		{
			callback.OnConnectionFailed(this);
			return;
		}
		
		retry = 30;
		// we now send the ack for last ack back to client
		while(retry > 0)
		{
			retry--;
			callback.SimpleSleep(100);
			callback.PerformTCPSend(ackHdr);
		}
		
		this.isConnected = true;
		callback.OnConnectionSucceeded(this);
	}
}

// provides basic send and receive features
public class ConnectionHelper 
{
	private T_DatagramSocket socket = null;
	
	ConnectionHelper(T_DatagramSocket socket)
	{
		this.socket = socket;
	}
	
	void send (byte[] bytes, int len, InetSocketAddress toAddr) throws IOException
	{
		socket.T_sendto(bytes, len, toAddr);
	}
	
	TCPHeader recv(int len) throws IOException
	{
		TCPHeader outHeader = null;
		DatagramPacket recvPacket = socket.T_recvfrom(len);
		
		// set the senders address
		outHeader = TCPHeader.createFromBytes(recvPacket.getData());
		outHeader.senderAddr = (InetSocketAddress) recvPacket.getSocketAddress();
	
		return outHeader;
	}
}
