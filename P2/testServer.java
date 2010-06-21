import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;


public class testServer
{

	/**
	 * @param args
	 * @throws IOException 
	 * @throws SocketTimeoutException 
	 */
	public static void main(String[] args) throws SocketTimeoutException, IOException
	{
		// TODO Auto-generated method stub
		System.out.println(Thread.activeCount());
		InetSocketAddress add = new InetSocketAddress("localhost", 12345);
		S_StreamSocket s = new S_StreamSocket(add);
		//s.S_setSoTimeout(10000);
		s.S_accept();
		System.out.println(s.connState.GetState());
		System.out.println(Thread.activeCount());
	}

}
