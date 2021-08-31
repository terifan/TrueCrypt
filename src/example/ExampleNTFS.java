package example;

import java.io.File;
import org.terifan.filesystem.ntfs.INode;
import org.terifan.filesystem.ntfs.NTFSFileSystem;
import org.terifan.pagestore.FilePageStore;
import org.terifan.security.cryptography.TrueCryptPageStore;


public class ExampleNTFS
{
	public static void main(String... args)
	{
		try
		{
			try (NTFSFileSystem fs = new NTFSFileSystem(TrueCryptPageStore.open(new FilePageStore(new File("d:/test.tc")), "password")))
			{
				for (INode node : fs.getNodes(""))
				{
					System.out.println(node);
				}
			}

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


//	private static void iterate(FatFile aDirectory) throws IOException
//	{
//		for (FatFile item : aDirectory.listFiles())
//		{
//			System.out.println(item.getPath());
//
//			if (item.isFile())
//			{
//				byte[] data = item.readAll();
//			}
//			else
//			{
//				iterate(item);
//			}
//		}
//	}
}
