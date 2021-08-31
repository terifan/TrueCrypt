package example;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.terifan.filesystem.fat32.FatFile;
import org.terifan.filesystem.fat32.FatFileSystem;
import org.terifan.pagestore.FilePageStore;
import org.terifan.security.cryptography.TrueCryptPageStore;


public class ExampleFAT
{
	public static void main(String... args)
	{
		try
		{
			try (FatFileSystem fs = new FatFileSystem(TrueCryptPageStore.open(new FilePageStore(new File("d:/test.tc")), "password")))
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
		for (FatFile item : aDirectory.listFiles())
		{
			System.out.println(item.getPath());

			if (item.isFile())
			{
				byte[] data = item.readAll();
			}
			else
			{
				iterate(item);
			}
		}
	}
}
