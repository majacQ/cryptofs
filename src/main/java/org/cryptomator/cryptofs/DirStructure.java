package org.cryptomator.cryptofs;

import org.cryptomator.cryptofs.common.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Enumeration of the vault directory structure resemblances.
 * <p>
 * A valid vault must contain a `d` directory.
 * Beginning with vault format 8, it must also contain a vault config file.
 * If the vault format is lower than 8, it must instead contain a masterkey file.
 * <p>
 * In the latter case, to distinguish between a damaged vault 8 directory and a legacy vault the masterkey file must be read.
 * For efficiency reasons, this class only checks for existence/readability of the above elements.
 * Hence, if the result of {@link #checkDirStructure(Path, String, String)} is {@link #MAYBE_LEGACY}, one needs to parse
 * the masterkey file and read out the vault version to determine this case.
 *
 * @since 2.0.0
 */
public enum DirStructure {

	/**
	 * Dir contains a <code>d</code> dir as well as a vault config file.
	 */
	VAULT,

	/**
	 * Dir contains a <code>d</code> dir and a masterkey file, but misses a vault config file.
	 * Either needs migration to a newer format or damaged.
	 */
	MAYBE_LEGACY,

	/**
	 * Dir does not qualify as vault.
	 */
	UNRELATED;


	/**
	 * Analyzes the structure of the given directory under certain vault existence criteria.
	 *
	 * @param pathToVault A directory path
	 * @param vaultConfigFilename Name of the vault config file
	 * @param masterkeyFilename Name of the masterkey file (may be null to skip detection of legacy vaults)
	 * @return enum indicating what this directory might be
	 * @throws IOException if the provided path is not a directory, does not exist or cannot be read
	 */
	public static DirStructure checkDirStructure(Path pathToVault, String vaultConfigFilename, String masterkeyFilename) throws IOException {
		if(! Files.readAttributes(pathToVault, BasicFileAttributes.class).isDirectory()) {
			throw new NotDirectoryException(pathToVault.toString());
		}
		Path dataDirPath = pathToVault.resolve(Constants.DATA_DIR_NAME);
		if (Files.isDirectory(dataDirPath)) {
			if (Files.isReadable(pathToVault.resolve(vaultConfigFilename))) {
				return VAULT;
			} else if (masterkeyFilename != null &&  Files.isReadable(pathToVault.resolve(masterkeyFilename))) {
				return MAYBE_LEGACY;
			}
		}
		return UNRELATED;
	}
}
