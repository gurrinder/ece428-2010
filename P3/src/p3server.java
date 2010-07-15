import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Date;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.omg.CORBA.Environment;

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
	public static void main(String[] args) throws IOException,
			NoSuchAlgorithmException, NoSuchPaddingException,
			InvalidKeyException, IllegalBlockSizeException,
			ShortBufferException, BadPaddingException,
			InvalidAlgorithmParameterException
	{
		Decoder decoder = new Decoder(Integer.valueOf(args[1]));
		DatagramSocket welcomeSocket = new DatagramSocket(12321);
		//System.console().printf("setenv P "+welcomeSocket.getLocalPort()); 
		welcomeSocket.setReceiveBufferSize(64 * 1024 * 1024); // 64MB
		byte[] buf = new byte[1024];
		BufferedWriter file = new BufferedWriter(new FileWriter(
		"out.dat"));
		while (true)
		{
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			welcomeSocket.receive(packet);
			if (new String(buf, 0, packet.getLength()).equals("   KEY CHANGE   "))
			{
				//System.out.println("hopefully KEY CHANGE " + packet.getLength() + " = " + new String(getSubByte(buf, 0, packet.getLength())) + "    =" + Decoder.getHexText(getSubByte(buf, 0, packet.getLength()), packet.getLength()));
				decoder.resetKey();
			} else
			{
				byte[] crypto = getSubByte(buf, 0, packet.getLength());
				byte[] plainText = decoder.decode(crypto);
				file.write(new String(plainText, 1, plainText.length-1));
				file.flush();
			}

		}
	}

	private static byte[] getSubByte(byte[] buf, int s, int length)
	{
		byte[] ret = new byte[length];
		for (int i = 0; i < length; i++)
			ret[i] = buf[s + i];
		return ret;
	}

}

class Decoder
{
	int numZero;
	SecretKeySpec keyFound = null;
	Cipher cipher;
	ArrayList<Integer> lastStop = new ArrayList<Integer>();
	long startTime;
	int counter;
	final byte[] iv = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
	private IvParameterSpec ips = new IvParameterSpec(iv);

	Decoder(int n) throws NoSuchAlgorithmException, NoSuchPaddingException
	{
		this.numZero = n;
		cipher = Cipher.getInstance("AES/CBC/NoPadding");
		System.out.println("numZero=" + this.numZero);
	}

	public void resetKey()
	{
		System.out.println("Reset Key");
		this.lastStop.clear();
		keyFound = null;
	}

	public byte[] decode(byte[] msg) throws InvalidKeyException,
			InvalidAlgorithmParameterException, ShortBufferException,
			IllegalBlockSizeException, BadPaddingException
	{
		byte[] plainText;
		byte[] keyval = { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
				-1, -1, -1 };
		if (keyFound != null)
		{
			plainText = new byte[msg.length];
			int ptLength = cipher.update(msg, 0, msg.length, plainText, 0);
			ptLength += cipher.doFinal(plainText, ptLength);
			if (checkString(plainText, ptLength))
				return plainText;
			
			//keyval = keyFound.getEncoded();
		}
		startTime = new Date().getTime();
		plainText = computeKeyAndDecode(msg, 0, 128 - this.numZero, 0, keyval);
		counter = 0;
		long endTime = new Date().getTime();
		System.out.println("Time Execution = " + (endTime-startTime));
		return plainText;

	}

	public static String getHexText(byte[] buf, int len)
	{
		// TODO Auto-generated method stub
		StringBuilder hex = new StringBuilder();
		for (int i = 0; i < len; i++)
		{
			int v = 0;
			if (buf[i] < 0)
				v+=128;
			v += buf[i] & masks[0][0];
			hex.append(Integer.toHexString(v).toUpperCase());
		}
		return hex.toString();
	}

	private byte[] computeKeyAndDecode(byte[] msg, int start, int end, int z, byte[] keyval)
			throws ShortBufferException, IllegalBlockSizeException,
			BadPaddingException, InvalidKeyException,
			InvalidAlgorithmParameterException
	{
		byte[] plainText;
		if (z == numZero)
		{
			SecretKeySpec key;
			counter++;
			if (counter >= 1000000)
			{
				counter = 0;
				System.out.println("Time So Far for 1000000 keys = " + (new Date().getTime() - this.startTime));
			}
			//System.out.println("Key Attempted : " + getHexText(keyval, keyval.length));
			key = new SecretKeySpec(keyval, "AES");
			cipher.init(Cipher.DECRYPT_MODE, key, ips);
			plainText = new byte[msg.length];
			int ptLength = cipher.update(msg, 0, msg.length, plainText, 0);
			ptLength += cipher.doFinal(plainText, ptLength);
			if (checkString(plainText, ptLength))
			{
				this.keyFound = key;
				System.out.println("Key Found : " + getHexText(keyval, keyval.length));
				return plainText;
			}
			else
				return null;
		}
		if (this.lastStop.size() > 0)
		{
			start = this.lastStop.remove(0);
		}
		byte[] newkeyval = new byte[keyval.length];
		for (int i = start; i <= end; i++)
		{
			for (int j = 0; j < keyval.length; j++)
				newkeyval[j] = keyval[j];

			setBit(newkeyval, i, 0);
			plainText = computeKeyAndDecodeDivider(msg, i+1, z+1, newkeyval);
			if (plainText != null)
			{
				this.lastStop.add(0, i);
				return plainText;
			}
		}
		return null;
	}
	
	
	
	private byte[] computeKeyAndDecodeDivider(byte[] msg, int start, int z,	byte[] keyval) throws InvalidKeyException, ShortBufferException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException
	{
		// TODO Auto-generated method stub
		return computeKeyAndDecode(msg, start, 128-this.numZero+z, z, keyval);
	}

	private int findFirstZero(byte[] keyval, int start)
	{
		for (int i = start; i < 128; i++)
		{
			if ((keyval[i/8] & masks[0][i%8]) == 0)
				return i;
		}
		return start;
	}



	private final static byte[][] masks = {{127, -65, -33, -17, -9, -5, -3, -2},
		{-128, 64, 32, 16, 8, 4, 2, 1}};
	private static void setBit(byte[] data, int pos, int val)
	{
		int posByte = pos / 8;
		int posBit = pos % 8;
		if (val == 0)
			data[posByte] = (byte) (data[posByte] & masks[val][posBit]);
		else
			data[posByte] = (byte) (data[posByte] | masks[val][posBit]);
	}

	private static boolean checkString(byte[] plainText, int len)
	{
		byte xor = 0;
		for (int i = 1; i < len; i++)
		{
			xor ^= plainText[i];
			if (plainText[i] < 0)
				return false;
		}
		return xor == plainText[0];
	}
}
