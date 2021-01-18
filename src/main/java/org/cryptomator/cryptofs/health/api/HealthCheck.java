package org.cryptomator.cryptofs.health.api;

import org.cryptomator.cryptofs.VaultConfig;
import org.cryptomator.cryptolib.api.Masterkey;

import java.nio.file.Path;
import java.util.Collection;

public interface HealthCheck {

	/**
	 * @return A unique name for this check (that might be used as a translation key)
	 */
	default String identifier() {
		return getClass().getCanonicalName();
	}

	/**
	 * Checks the vault at the given path.
	 *
	 * @param pathToVault Path to the vault's root directory
	 * @param config The parsed and verified vault config
	 * @param masterkey The masterkey
	 * @return Diagnostic results
	 */
	Collection<DiagnosticResult> check(Path pathToVault, VaultConfig config, Masterkey masterkey);

}