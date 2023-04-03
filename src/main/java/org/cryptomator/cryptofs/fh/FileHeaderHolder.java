package org.cryptomator.cryptofs.fh;

import org.cryptomator.cryptolib.api.CryptoException;
import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.cryptolib.api.FileHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@OpenFileScoped
public class FileHeaderHolder {

	private static final Logger LOG = LoggerFactory.getLogger(FileHeaderHolder.class);

	private final Cryptor cryptor;
	private final AtomicReference<Path> path;
	private final AtomicReference<FileHeader> header = new AtomicReference<>();
	private final AtomicBoolean isPersisted = new AtomicBoolean();

	@Inject
	public FileHeaderHolder(Cryptor cryptor, @CurrentOpenFilePath AtomicReference<Path> path) {
		this.cryptor = cryptor;
		this.path = path;
	}

	public FileHeader get() {
		FileHeader result = header.get();
		if (result == null) {
			throw new IllegalStateException("Header not set.");
		}
		return result;
	}

	FileHeader createNew() {
		LOG.trace("Generating file header for {}", path.get());
		FileHeader newHeader = cryptor.fileHeaderCryptor().create();
		header.set(newHeader);
		return newHeader;
	}


	/**
	 * Reads, decrypts and caches the file header from the given file channel.
	 *
	 * @param ch File channel to the encrypted file
	 * @return {@link FileHeader} of the encrypted file
	 * @throws IOException if the file header cannot be read or decrypted
	 */
	FileHeader loadExisting(FileChannel ch) throws IOException {
		LOG.trace("Reading file header from {}", path.get());
		ByteBuffer existingHeaderBuf = ByteBuffer.allocate(cryptor.fileHeaderCryptor().headerSize());
		ch.read(existingHeaderBuf, 0);
		existingHeaderBuf.flip();
		try {
			FileHeader existingHeader = cryptor.fileHeaderCryptor().decryptHeader(existingHeaderBuf);
			header.set(existingHeader);
			isPersisted.set(true);
			return existingHeader;
		} catch (IllegalArgumentException | CryptoException e) {
			throw new IOException("Unable to decrypt header of file " + path.get(), e);
		}
	}

	public AtomicBoolean headerIsPersisted() {
		return isPersisted;
	}

}
