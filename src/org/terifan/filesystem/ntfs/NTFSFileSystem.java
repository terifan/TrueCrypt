package org.terifan.filesystem.ntfs;

import java.io.FileOutputStream;
import org.terifan.pagestore.PageStore;
import java.io.IOException;
import org.terifan.util.Debug;


public class NTFSFileSystem implements AutoCloseable
{
	private PageStore mPageStore;


	public NTFSFileSystem(PageStore aPageStore) throws IOException
	{
		mPageStore = aPageStore;

		byte[] buffer = new byte[221440 / 512 * 512 + 512];

		mPageStore.read(16032, buffer);

//		Debug.hexDump(buffer);
	}


	PageStore getPageStore()
	{
		return mPageStore;
	}


	@Override
	public void close() throws IOException
	{
		if (mPageStore != null)
		{
			mPageStore.close();
			mPageStore = null;
		}
	}


	public boolean equals(NTFSFileSystem obj)
	{
		if (obj instanceof NTFSFileSystem)
		{
			return this.mPageStore == ((NTFSFileSystem)obj).mPageStore;
		}
		return true;
	}
}
