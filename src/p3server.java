import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.* ;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class p3server
{

	/**
	 * @param args
	 * @throws IOException 
	 * @throws NoSuchPaddingException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeyException 
	 * @throws BadPaddingException 
	 * @throws ShortBufferException 
	 * @throws IllegalBlockSizeException 
	 * @throws InvalidAlgorithmParameterException 
	 */
	public static void main(String[] args) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, ShortBufferException, BadPaddingException, InvalidAlgorithmParameterException
	{
		// TODO Auto-generated method stub
		final byte[] keyval =
	      { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 };
		IvParameterSpec ips;
		System.out.println("keyval : " +  getHexText(keyval, keyval.length) + "\n");		final byte[] iv =
	      { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
		DatagramSocket welcomeSocket = new DatagramSocket(12321);
		welcomeSocket.setReceiveBufferSize(64*1024*1024); // 64MB
		byte[] buf = new byte[1024];
		SecretKeySpec key = null;
		Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
//		key = new SecretKeySpec(keyval, "AES");
//		ips = new IvParameterSpec(iv);
//		cipher.init(Cipher.DECRYPT_MODE, key, ips);
		while(true)
		{
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			welcomeSocket.receive(packet);
			System.out.println("recieved Encripted length : " + packet.getLength() + "\n    " + getHexText(buf, packet.getLength()) + "\n");
			
			if ( packet.getLength() == 16)
			{
				System.out.println("key : " + getHexText(buf, packet.getLength()) + "\n");
				key = new SecretKeySpec( getSubByte(buf, 0, packet.getLength()), "AES");
				ips = new IvParameterSpec(iv);
				cipher.init(Cipher.DECRYPT_MODE, key, ips);
				System.err.println("Got key\n");
			}
			else if (key != null)
			{
				byte[] crypto = getSubByte(buf, 0, packet.getLength());
				byte[] plainText = new byte[crypto.length];
			    int ptLength = cipher.update(crypto, 0, crypto.length, plainText, 0);
			    ptLength += cipher.doFinal(plainText, ptLength);
				System.out.print(new String (plainText,1, ptLength-1));
				
				checkString(plainText, ptLength);
			}
			else
				System.out.println("droped\n");
				
			
		}
	}

	private static byte[] getSubByte(byte[] buf, int s, int length)
	{
		byte[] ret = new byte[length];
		for (int i = 0; i < length; i++)
			ret[i] = buf[s+i];
		return ret;
	}

	private static void checkString(byte[] plainText, int len)
	{
		byte xor = 0;
		for (int i = 1; i < len; i++)
			xor ^= plainText[i];
		if (xor != plainText[0])
		{
			System.out.println("___________________invalid\n\n");
		}
		
	}

	private static String getHexText(byte[] buf, int len)
	{
		// TODO Auto-generated method stub
		StringBuilder hex = new StringBuilder();
		for (int i = 0; i < len; i++)
			hex.append(Integer.toHexString(buf[i]).toUpperCase());
		return hex.toString();
	}

}
