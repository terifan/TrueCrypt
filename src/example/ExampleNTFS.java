package example;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.terifan.filesystem.ntfs.INode;
import org.terifan.filesystem.ntfs.IStream;
import org.terifan.filesystem.ntfs.NTFSFileSystem;
import org.terifan.pagestore.FilePageStore;
import org.terifan.truecrypt.TrueCryptPageStore;


public class ExampleNTFS
{
	public static void main(String... args)
	{
		try
		{
			try (NTFSFileSystem fs = new NTFSFileSystem(TrueCryptPageStore.open(new FilePageStore(new File("d:/test.tc")), "test")))
			{
				for (INode node : fs.getNodes(""))
				{
		//			System.out.println(node);

					if (node.getName().equals("9.jpg"))
					{
						IStream stream = node.getStreams().get(0);

						BufferedImage image = ImageIO.read(fs.readStream(stream));

						System.out.println(image);
					}
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
