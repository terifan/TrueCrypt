package org.terifan.filesystem.ntfs;


enum FileAccess
{
	Read(1),
	ReadWrite(3),
	Write(2);

	private int mFlag;


	private FileAccess(int aFlag)
	{
		mFlag = aFlag;
	}
}
