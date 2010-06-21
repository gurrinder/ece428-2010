import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;


public class testClient
{

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException
	{
		// TODO Auto-generated method stub
		System.out.println(Thread.activeCount());
		InetSocketAddress add = new InetSocketAddress("localhost", 12346);
		InetSocketAddress to = new InetSocketAddress("localhost", 12345);
		S_StreamSocket s = new S_StreamSocket(add);
		s.S_connect(to);
		System.out.println(s.connState.GetState());
		System.out.println(Thread.activeCount());
	}

}
