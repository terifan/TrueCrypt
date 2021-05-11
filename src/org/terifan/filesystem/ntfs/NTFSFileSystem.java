package org.terifan.filesystem.ntfs;

import org.terifan.pagestore.PageStore;
import java.io.IOException;
import org.terifan.util.Debug;


public class NTFSFileSystem implements AutoCloseable
{
	private PageStore mPageStore;


	public NTFSFileSystem(PageStore aPageStore) throws IOException
	{
		mPageStore = aPageStore;

		byte[] buffer = new byte[16*1024];
//		mPageStore.read(147456/512, buffer); // root
		mPageStore.read(8192/512, buffer); // mft
		Debug.hexDump(buffer);
		System.out.println("---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ---- ");

		NtfsReader reader = new NtfsReader(mPageStore, RetrieveMode.All.CODE);

		for (INode node : reader.GetNodes(""))
		{
			System.out.println(node);
		}

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
