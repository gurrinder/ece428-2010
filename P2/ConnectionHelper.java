import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.List;

import ece428.socket.T_DatagramSocket;

abstract class ConnectionHelperTask implements Runnable
{
	private boolean bRun = true;
	protected ConnectionHelper connHelper = null;
	protected S_StreamSocket.TaskCallback callback = null;
	
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
    
    @Override
	public void run()
	{
		while(bRun)
		{
			performTask();
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
		}
		catch (IOException e)
		{
			System.err.println("ReceiveTask::performTask - IOException: " + e.getMessage());
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
