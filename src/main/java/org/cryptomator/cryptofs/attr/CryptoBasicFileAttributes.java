/*******************************************************************************
 * Copyright (c) 2016 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.cryptofs.attr;

import org.cryptomator.cryptofs.common.CiphertextFileType;
import org.cryptomator.cryptofs.fh.OpenCryptoFile;
import org.cryptomator.cryptolib.api.Cryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Optional;

sealed class CryptoBasicFileAttributes implements BasicFileAttributes
		permits CryptoDosFileAttributes, CryptoPosixFileAttributes {

	private static final Logger LOG = LoggerFactory.getLogger(CryptoBasicFileAttributes.class);

	private final CiphertextFileType ciphertextFileType;
	private final long size;
	private final FileTime lastModifiedTime;
	private final FileTime lastAccessTime;
	private final FileTime creationTime;
	private final Object fileKey;

	public CryptoBasicFileAttributes(BasicFileAttributes delegate, CiphertextFileType ciphertextFileType, Path ciphertextPath, Cryptor cryptor, Optional<OpenCryptoFile> openCryptoFile) {
		this.ciphertextFileType = ciphertextFileType;
		this.size = switch (ciphertextFileType) {
			case DIRECTORY -> delegate.size();
			case SYMLINK, FILE -> getPlaintextFileSize(ciphertextPath, delegate.size(), openCryptoFile, cryptor);
		};
		this.lastModifiedTime =  openCryptoFile.map(OpenCryptoFile::getLastModifiedTime).orElseGet(delegate::lastModifiedTime);
		this.lastAccessTime = openCryptoFile.map(openFile -> FileTime.from(Instant.now())).orElseGet(delegate::lastAccessTime);
		this.creationTime = delegate.creationTime();
		this.fileKey = delegate.fileKey();
	}

	private static long getPlaintextFileSize(Path ciphertextPath, long size, Optional<OpenCryptoFile> openCryptoFile, Cryptor cryptor) {
		return openCryptoFile.flatMap(OpenCryptoFile::size).orElseGet(() -> calculatePlaintextFileSize(ciphertextPath, size, cryptor));
	}

	private static long calculatePlaintextFileSize(Path ciphertextPath, long size, Cryptor cryptor) {
		try {
			long payloadSize = size - cryptor.fileHeaderCryptor().headerSize();
			return cryptor.fileContentCryptor().cleartextSize(payloadSize);
		} catch (IllegalArgumentException e) {
			LOG.warn("Unable to calculate cleartext file size for {}. Ciphertext size (including header): {}", ciphertextPath, size);
			return 0l;
		}
	}


	@Override
	public FileTime lastModifiedTime() {
		return lastModifiedTime;
	}

	@Override
	public FileTime lastAccessTime() {
		return lastAccessTime;
	}

	@Override
	public FileTime creationTime() {
		return creationTime;
	}

	@Override
	public boolean isRegularFile() {
		return CiphertextFileType.FILE == ciphertextFileType;
	}

	@Override
	public boolean isDirectory() {
		return CiphertextFileType.DIRECTORY == ciphertextFileType;
	}

	@Override
	public boolean isSymbolicLink() {
		return CiphertextFileType.SYMLINK == ciphertextFileType;
	}

	@Override
	public boolean isOther() {
		assert isRegularFile() || isDirectory() || isSymbolicLink();
		return false;
	}

	@Override
	public long size() {
		return size;
	}

	@Override
	public Object fileKey() {
		return fileKey;
	}

}
