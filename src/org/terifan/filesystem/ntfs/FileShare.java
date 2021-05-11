package org.terifan.filesystem.ntfs;


enum FileShare
{
	None(0),
	Read(1),
	Write(2),
	Delete(4),
	All(1 + 2 + 4);

	private int mFlag;


	private FileShare(int aFlag)
	{
		mFlag = aFlag;
	}
}
