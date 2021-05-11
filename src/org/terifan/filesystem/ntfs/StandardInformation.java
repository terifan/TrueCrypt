package org.terifan.filesystem.ntfs;


class StandardInformation
{
	public DateTime mCreationTime;
	public DateTime mLastAccessTime;
	public DateTime mLastChangeTime;


	public StandardInformation(DateTime aCreationTime, DateTime aLastAccessTime, DateTime aLastChangeTime)
	{
		mCreationTime = aCreationTime;
		mLastAccessTime = aLastAccessTime;
		mLastChangeTime = aLastChangeTime;
	}
}
