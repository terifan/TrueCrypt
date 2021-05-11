package org.terifan.filesystem.ntfs;


enum FileAccess
{
	Read(1),
	ReadWrite(3),
	Write(2);

	final int CODE;


	private FileAccess(int aCode)
	{
		CODE = aCode;
	}
}
