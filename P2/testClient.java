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
		InetSocketAddress add;
		add = InetSocketAddress.createUnresolved("localhost", 12345);
		S_StreamSocket s = new S_StreamSocket(add);
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		byte[] buf = new byte [50];
		int ret;
		s.S_connect(add);
		String line;
		System.out.print("enter text: ");
		while ((line = in.readLine()).length()>0)
		{
			s.S_send(line.getBytes(), line.length());
			s.S_receive(buf, 50);
			System.out.println(buf.toString());
			System.out.print("enter text: ");
		}

	}

}
