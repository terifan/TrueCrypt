package dev;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.terifan.fat32.FatFile;
import org.terifan.truecrypt.TrueCryptFileSystem;


public class Test
{
	public static void main(String... args)
	{
		try
		{
			try (TrueCryptFileSystem fs = new TrueCryptFileSystem(new File("d:/test.tc"), "password"))
			{
				FatFile file = fs.getFile("/Wallpapers Fantasy/darth-vader-hd-1080p-wallpaper-full-hd-wallpaper.jpg");

				try (FileOutputStream fos = new FileOutputStream("d:/test.jpg"))
				{
					fos.write(file.readAll());
				}

				iterate(fs.getFile(""));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace(System.out);
		}
	}


	private static void iterate(FatFile aDirectory) throws IOException
	{
		for (FatFile file : aDirectory.listFiles())
		{
			System.out.println(file.getPath());

			if (file.isFile())
			{
				byte[] data = file.readAll();
			}
			else
			{
				iterate((FatFile)file);
			}
		}
	}
}