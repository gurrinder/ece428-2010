import java.io.IOException;
import java.net.InetSocketAddress;


public class testClient
{

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception
	{
		// TODO Auto-generated method stub
		System.out.println(Thread.activeCount());
		InetSocketAddress add = new InetSocketAddress("localhost", 12346);
		InetSocketAddress to = new InetSocketAddress("localhost", 12345);
		S_StreamSocket s = new S_StreamSocket(add);
		s.S_connect(to);
		String ss = "012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789";
		s.S_send(ss.getBytes(), ss.length());
		s.S_send(ss.getBytes(), ss.length());
		s.S_close();
		System.out.println(s.connState.GetState());
		System.out.println(Thread.activeCount());
	}

}
