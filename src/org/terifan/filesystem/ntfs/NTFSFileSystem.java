package org.terifan.filesystem.ntfs;

import java.io.ByteArrayInputStream;
import org.terifan.pagestore.PageStore;
import java.io.IOException;
import java.io.InputStream;
import static java.util.Arrays.stream;


public class NTFSFileSystem implements AutoCloseable
{
	private PageStore mPageStore;
	private NtfsReader mReader;


	public NTFSFileSystem(PageStore aPageStore) throws IOException
	{
		mPageStore = aPageStore;

		mReader = new NtfsReader(mPageStore, RetrieveMode.All.CODE);
	}


	public Iterable<INode> getNodes(String aRootPath)
	{
		return mReader.getNodes(aRootPath);
	}


	public InputStream readStream(IStream aStream)
	{
		IFragment f = aStream.getFragments().get(0);

		int len = ((int)aStream.getSize() + 512 - 1) / 512 * 512;

		byte[] output = new byte[len];

		mReader.readFragment(f, 0, output, 0, output.length);

		return new ByteArrayInputStream(output, 0, (int)aStream.getSize());
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
