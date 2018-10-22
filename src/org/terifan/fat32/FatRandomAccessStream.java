package org.terifan.fat32;

import java.io.IOException;
import java.util.ArrayList;


public class FatRandomAccessStream implements AutoCloseable
{
	private final static int BUFFER_SIZE = 65536;

	private FatFile mFile;
	private byte[] mBuffer;
	private long mFilePointer;
	private int mBufferPointer;
	private int mBufferLength;
	private long mFileLength;
	private ArrayList<Long> mClusters;
	private int mClusterIndex;
	private boolean mClusterEnd;
	private boolean mUnknownLength;
	private int mPrefetchClusterCount;


	public FatRandomAccessStream(FatFile aFatFile)
	{
//		System.out.println("Open stream for object " + aFatFile);

		mFile = aFatFile;
		mBuffer = new byte[BUFFER_SIZE];
		mClusters = new ArrayList<>();
		mFileLength = aFatFile.getLength();
		mUnknownLength = mFile.isDirectory();

		mPrefetchClusterCount = BUFFER_SIZE / aFatFile.getFileSystem().getClusterSize() - 1;

		if (mUnknownLength)
		{
			mFileLength = Long.MAX_VALUE;
		}
	}


	public int read() throws IOException
	{
		if (mFilePointer == mFileLength)
		{
			return -1;
		}
		if (mBufferPointer == mBufferLength)
		{
			mBufferPointer = 0;
			loadFile();
		}
		if (mBufferPointer == mBufferLength) // only when unknown length
		{
			return -1;
		}

		mFilePointer++;
		return mBuffer[mBufferPointer++];
	}


	public int read(byte[] aBuffer) throws IOException
	{
		return read(aBuffer, 0, aBuffer.length);
	}


	public int read(byte[] aBuffer, int aOffset, int aLength) throws IOException
	{
		int remaining = aLength;

		if (mFileLength - mFilePointer < remaining)
		{
			remaining = (int)(mFileLength - mFilePointer);
		}

		for (int totalRead = 0;;)
		{
			if (mFilePointer == mFileLength || remaining == 0)
			{
				return totalRead;
			}

			int len = Math.min(remaining, mBufferLength - mBufferPointer);

			//System.out.printf("%8d %8d %8d %8b %8d %8d\n", remaining, mBufferLength, mBufferPointer, mClusterEnd, mClusterIndex, mClusters.size());
			if (len > 0)
			{
				System.arraycopy(mBuffer, mBufferPointer, aBuffer, aOffset, len);
				mBufferPointer += len;
				mFilePointer += len;
				aOffset += len;
				remaining -= len;
				totalRead += len;
			}

			if (remaining > 0)
			{
				//System.out.println("# remaining="+remaining);
				mBufferPointer = 0;
				loadFile();

				if (mUnknownLength && mClusterEnd && mBufferLength == mBufferPointer)
				{
					return totalRead;
				}
			}
		}
	}


	private void loadFile() throws IOException
	{
		FatFileSystem fileSystem = mFile.getFileSystem();

		if (mClusterIndex == mClusters.size() && !mClusterEnd)
		{
			int pref = mPrefetchClusterCount;
			if (mClusters.isEmpty())
			{
				mClusters.add(mFile.getStartCluster());
				pref--;
			}
			mClusterEnd = fileSystem.getAllocationTable().getChain(mClusters.get(Math.max(mClusterIndex - 1, 0)), pref, mClusters);
		}

		int clusterLength = fileSystem.getClusterSize();
		mBufferLength = 0;

		for (int i = 0; i < mBuffer.length / clusterLength && mClusterIndex < mClusters.size(); i++)
		{
//			System.out.println("Reading object cluster " + mClusters.get(mClusterIndex) + " from disk");

			fileSystem.getPageStore().read(fileSystem.getSectorOffset(mClusters.get(mClusterIndex)), mBuffer, mBufferLength, clusterLength);
			mClusterIndex++;
			mBufferLength += clusterLength;
		}

		//System.out.println("mBufferLength="+mBufferLength+", mClusterIndex="+mClusterIndex+", mClusters.size()="+mClusters.size());
	}


	@Override
	public void close() throws IOException
	{
//		System.out.println("Close stream for object " + mFile);

		mFile = null;
		mBuffer = null;
		mClusters = null;
	}


	public long getFilePointer() throws IOException
	{
		return mFilePointer;
	}


	public long length() throws IOException
	{
		return mFileLength;
	}


//	public void write(int b) throws IOException
//	{
//		throw new UnsupportedOperationException("Not supported yet.");
//	}
//
//
//	public void write(byte[] aBuffer) throws IOException
//	{
//		throw new UnsupportedOperationException("Not supported yet.");
//	}
//
//
//	public void write(byte[] aBuffer, int aOffset, int aLength) throws IOException
//	{
//		throw new UnsupportedOperationException("Not supported yet.");
//	}
//
//
//	public void seek(long aOffset) throws IOException
//	{
//		throw new UnsupportedOperationException("Not supported yet.");
//	}
//
//
//	public void setLength(long aLength) throws IOException
//	{
//		throw new UnsupportedOperationException("Not supported yet.");
//	}
}
