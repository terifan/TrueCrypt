package org.terifan.filesystem.ntfs;

import org.terifan.pagestore.PageStore;
import java.io.IOException;


public class NTFSFileSystem implements AutoCloseable
{
	private PageStore mPageStore;


	public NTFSFileSystem(PageStore aPageStore) throws IOException
	{
		mPageStore = aPageStore;

		NtfsReader reader = new NtfsReader(mPageStore, RetrieveMode.All.CODE);

		for (INode node : reader.getNodes(""))
		{
			System.out.println(node);
		}
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
