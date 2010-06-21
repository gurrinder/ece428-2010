import java.net.InetSocketAddress;


public class testServer
{

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		// TODO Auto-generated method stub
		InetSocketAddress add;
		add = InetSocketAddress.createUnresolved("localhost", 12345);
		S_StreamSocket s = new S_StreamSocket(add);
		
		byte[] buf = new byte [50];
		int ret;
		s.S_accept();
		while ((ret = s.S_receive(buf, 50)) > 0)
		{
			byte[] buf2 = buf.toString().toUpperCase().getBytes();
			s.S_send(buf2, buf2.length);
		}
		s.S_close();
	}

}
