package example;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import org.terifan.fat32.FatFile;
import org.terifan.pagestore.FilePageStore;
import org.terifan.truecrypt.TrueCryptPageStore;


public class Example
{
	public static void main(String... args)
	{
		try
		{
			Consumer<Long> prog = t ->
			{
				System.out.println(t);
			};

			TrueCryptPageStore.create(new FilePageStore(new File("d:/test.tc"), false, 512), 1000, "password", TrueCryptPageStore.CipherOption.AES_TWOFISH_SERPENT, TrueCryptPageStore.DigestOption.SHA512, prog);

//			try (FatFileSystem fs = new FatFileSystem(TrueCryptPageStore.open(new FilePageStore(new File("d:/test.tc")), "password")))
//			{
//				FatFile file = fs.getFile("/Wallpapers Fantasy/darth-vader-hd-1080p-wallpaper-full-hd-wallpaper.jpg");
//
//				try (FileOutputStream fos = new FileOutputStream("d:/test.jpg"))
//				{
//					fos.write(file.readAll());
//				}
//
//				iterate(fs.getFile(""));
//			}
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
