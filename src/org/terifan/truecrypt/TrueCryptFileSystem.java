package org.terifan.truecrypt;

import java.io.File;
import java.io.IOException;
import org.terifan.fat32.FatFileSystem;
import org.terifan.pagestore.FilePageStore;


public class TrueCryptFileSystem extends FatFileSystem
{
	public TrueCryptFileSystem(File aFile, String aPassword) throws IOException
	{
		super(new TrueCryptPageStore(new FilePageStore(aFile, true, 512), aPassword));
	}
}
